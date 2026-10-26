package dev.felipe.warbands.raid;

import dev.felipe.warbands.Directions;
import dev.felipe.warbands.Ground;
import dev.felipe.warbands.WarbandsAdvancements;
import dev.felipe.warbands.camp.Spoils;
import dev.felipe.warbands.entity.SoldierEntity;
import dev.felipe.warbands.entity.Stance;
import dev.felipe.warbands.raid.RaidRegistry.ActiveRaid;
import dev.felipe.warbands.raid.RaidRegistry.Phase;
import dev.felipe.warbands.raid.RaidRegistry.TownRaids;
import dev.felipe.warbands.registry.ModBlocks;
import dev.felipe.warbands.registry.ModGameRules;
import dev.felipe.warbands.camp.CampTier;
import dev.felipe.warbands.camp.Threat;
import dev.felipe.warbands.town.Age;
import dev.felipe.warbands.town.Relics;
import dev.felipe.warbands.town.Resource;
import dev.felipe.warbands.town.TownCenterBlockEntity;
import dev.felipe.warbands.town.TownRegistry;
import dev.felipe.warbands.town.UnitType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.Difficulty;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Raids on Town Centers, from the Feudal Age on. Checked once a second per dimension.
 * <p>
 * A town is due every 4-6 in-game days (the first at least 2 days after reaching Feudal), or the night after its
 * owner takes an enemy camp. A due raid starts after nightfall (or a day late if night never comes), only while
 * the owner is online and near the town, not on Peaceful, and with the {@code warbands:raids} gamerule on.
 * The horn sounds, a boss bar appears, and after a minute the raiders arrive 48-64 blocks away and march on the
 * Town Center. Kill them all: gold for the stockpile. Leave the Town Center undefended (nobody of yours within 12
 * blocks) with a raider next to the core for 5 seconds: they carry off a fifth of every resource and leave. Ten minutes of attack (counted only while the owner is near) without a result:
 * they leave with nothing.
 */
public final class Raids {
	public static final int DAY = 24000;
	public static final int FIRST_DELAY = 2 * DAY;
	public static final int MIN_INTERVAL = 4 * DAY;
	public static final int MAX_INTERVAL = 6 * DAY;
	public static final int WARNING_TICKS = 60 * 20;
	public static final int TIMEOUT_TICKS = 10 * 60 * 20;
	public static final int RETREAT_TICKS = 60 * 20;
	/** The owner must be this close to the Town Center for a raid to start or be judged. */
	public static final int OWNER_RANGE = 96;
	public static final int SPAWN_MIN = 48;
	public static final int SPAWN_MAX = 64;
	public static final double PLUNDER_RANGE = 4.0;
	/** Raiders don't loot while you or your soldiers are this close to the Town Center. */
	public static final double DEFEND_RANGE = 12.0;
	public static final int PLUNDER_TICKS = 5 * 20;
	public static final double PLUNDER_SHARE = 0.2;
	private static final int CHECK_INTERVAL = 20;

	/** Boss bars live in memory and are rebuilt after a restart from the saved raid. */
	private static final Map<String, ServerBossEvent> BARS = new HashMap<>();

	/** Forgets the boss bars of a world that has closed. */
	public static void clearState() {
		BARS.clear();
	}

	private Raids() {
	}

	/**
	 * How many raiders march: the age's roster grown by raids won and revenge ({@link RaidComposition#size}), then
	 * the {@link Threat} bonus (difficulty, players at home). Easy only trims the extras, never the kind's base
	 * roster (a Siege keeps its rams); its raiders are weaker instead ({@link Threat#raid}).
	 */
	public static int sizeFor(ServerLevel level, BlockPos town, Age age, int won, boolean retaliation) {
		int size = RaidComposition.size(age, won, retaliation);
		return Math.max(RaidComposition.size(age, 0, false), size + Threat.raidBonus(level, town));
	}

	/** Called every server tick per level. */
	public static void tick(ServerLevel level) {
		if (level.getGameTime() % CHECK_INTERVAL != 0) {
			return;
		}
		for (TownRegistry.Town town : List.copyOf(TownRegistry.towns(level))) {
			if (!level.isLoaded(town.pos()) || !(level.getBlockEntity(town.pos()) instanceof TownCenterBlockEntity center)) {
				continue;
			}
			tickTown(level, town, center);
		}
		// Raids whose Town Center is gone: send the raiders home.
		for (TownRaids raids : List.copyOf(RaidRegistry.all(level))) {
			if (level.isLoaded(raids.town()) && !level.getBlockState(raids.town()).is(ModBlocks.TOWN_CENTER)) {
				raids.active().ifPresent(active -> discardRaiders(level, active));
				removeBar(level, raids.town());
				RaidRegistry.remove(level, raids.town());
			}
		}
	}

	private static void tickTown(ServerLevel level, TownRegistry.Town town, TownCenterBlockEntity center) {
		long now = level.getGameTime();
		Optional<TownRaids> saved = RaidRegistry.get(level, town.pos());
		if (saved.isEmpty()) {
			if (center.age().atLeast(Age.FEUDAL)) {
				RaidRegistry.put(level, new TownRaids(town.pos(), town.owner(), now + FIRST_DELAY, 0, false, Optional.empty()));
			}
			return;
		}
		TownRaids raids = saved.get();
		if (!raids.owner().equals(town.owner())) {
			// The Town Center changed hands (rebuilt by someone else): the record's owner would never be near, so its
			// raids could never fire. Start a clean calendar; an old active raid ends with its raiders going home.
			raids.active().ifPresent(active -> discardRaiders(level, active));
			removeBar(level, raids.town());
			raids = new TownRaids(town.pos(), town.owner(), now + FIRST_DELAY, 0, false, Optional.empty());
			RaidRegistry.put(level, raids);
			return;
		}
		if (raids.active().isPresent()) {
			tickActive(level, raids, raids.active().get(), center, now);
			return;
		}
		if (!canRaid(level, raids)) {
			return;
		}
		// The next raid's kind is drawn from the town's bag; Night Stalkers wait for full dark, so a town can sit
		// on a drawn kind until deep night without losing it.
		// Same age as start() raids with: a Village town that took a camp is raided as Feudal.
		raids = ensureBag(level, raids, center.age().atLeast(Age.FEUDAL) ? center.age() : Age.FEUDAL);
		if (raids.bag().get(0).canStart(level, now, raids.nextRaid())) {
			start(level, raids, center, now, null);
		}
	}

	/** Raids happen only when their owner could see them: online, near the town, not Peaceful, gamerule on. */
	private static boolean canRaid(ServerLevel level, TownRaids raids) {
		return raidsOn(level) && ownerNearby(level, raids) != null;
	}

	private static @Nullable ServerPlayer ownerNearby(ServerLevel level, TownRaids raids) {
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(raids.owner());
		if (owner == null || owner.level() != level || owner.isSpectator()
				|| owner.blockPosition().distSqr(raids.town()) > (double) OWNER_RANGE * OWNER_RANGE) {
			return null;
		}
		return owner;
	}

	/** Starts a raid on the Town Center at {@code town} now (the horn sounds). Used by the timer and {@code /warbands raid}. */
	public static boolean startNow(ServerLevel level, BlockPos town) {
		return startNow(level, town, null);
	}

	/**
	 * {@code forced} (the {@code /warbands raid <kind>} cheat) brings that kind regardless of what the bag would
	 * serve, and takes it out of the bag if it's in there. The draw and the bag still update like a natural raid.
	 */
	public static boolean startNow(ServerLevel level, BlockPos town, @Nullable RaidKind forced) {
		if (!(level.getBlockEntity(town) instanceof TownCenterBlockEntity center) || center.owner() == null) {
			return false;
		}
		TownRaids raids = RaidRegistry.get(level, town)
				.orElse(new TownRaids(town, center.owner(), level.getGameTime(), 0, false, Optional.empty()));
		if (raids.active().isPresent() || (forced != null && !RaidKind.forAge(center.age()).contains(forced))) {
			return false;
		}
		start(level, raids, center, level.getGameTime(), forced);
		return true;
	}

	/**
	 * The town's draw pile of raid kinds (one of each the age allows, shuffled), refilled when empty so the first
	 * draw is never the last kind served. The bag is on the saved record, so a refill is written once and the
	 * draw itself only removes.
	 */
	private static TownRaids ensureBag(ServerLevel level, TownRaids raids, Age age) {
		if (!raids.bag().isEmpty()) {
			return raids;
		}
		List<RaidKind> bag = new ArrayList<>(RaidKind.forAge(age));
		Util.shuffle(bag, level.getRandom());
		// Across a refill the same kind can't come twice in a row: if the shuffle put the last kind first, swap it in.
		if (raids.last().isPresent() && bag.size() > 1 && bag.get(0) == raids.last().get()) {
			for (int i = 1; i < bag.size(); i++) {
				if (bag.get(i) != raids.last().get()) {
					bag.set(0, bag.get(i));
					bag.set(i, raids.last().get());
					break;
				}
			}
		}
		TownRaids updated = new TownRaids(raids.town(), raids.owner(), raids.nextRaid(), raids.won(), raids.retaliation(),
				raids.active(), List.copyOf(bag), raids.last());
		RaidRegistry.put(level, updated);
		return updated;
	}

	private static void start(ServerLevel level, TownRaids raids, TownCenterBlockEntity center, long now, @Nullable RaidKind forced) {
		BlockPos spawn = findSpawn(level, raids.town());
		if (spawn == null) {
			// Nowhere dry to come from (an island town): try again tomorrow.
			RaidRegistry.put(level, raids.withNext(now + DAY, raids.won(), raids.retaliation()));
			return;
		}
		Age age = center.age().atLeast(Age.FEUDAL) ? center.age() : Age.FEUDAL;
		raids = ensureBag(level, raids, age);
		List<RaidKind> bag = new ArrayList<>(raids.bag());
		RaidKind kind = forced != null ? forced : bag.remove(0);
		if (forced != null) {
			bag.remove(forced);
		}
		int size = sizeFor(level, raids.town(), age, raids.won(), raids.retaliation());
		ActiveRaid active = new ActiveRaid(Phase.WARNING, now, now, spawn, List.of(), size, age, 0, 0, kind);
		RaidRegistry.put(level, new TownRaids(raids.town(), raids.owner(), raids.nextRaid(), raids.won(), raids.retaliation(),
				Optional.of(active), List.copyOf(bag), Optional.of(kind)));
		level.playSound(null, raids.town(), SoundEvents.RAID_HORN.value(), SoundSource.NEUTRAL, 64.0F, 1.0F);
		level.playSound(null, spawn, SoundEvents.RAID_HORN.value(), SoundSource.NEUTRAL, 64.0F, 1.0F);
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(raids.owner());
		if (owner != null) {
			// No arrows here: a chat line or a title can't follow where the player looks. Compass words, and a column of
			// smoke over the spot they come from (see smokeSignal) that can be seen from the town.
			owner.sendSystemMessage(Component.translatable("message.warbands.raid_warning", kind.displayName(),
					Directions.compass(raids.town(), spawn), size).withStyle(ChatFormatting.RED));
			Directions.title(owner, Component.translatable("message.warbands.raid_warning_title"),
					Component.translatable("message.warbands.raid_warning_subtitle", kind.displayName(), Directions.compass(raids.town(), spawn)));
		}
		ServerBossEvent bar = bar(level, raids.town());
		bar.setName(warningBarName(kind));
		bar.setProgress(1.0F);
	}

	private static void tickActive(ServerLevel level, TownRaids raids, ActiveRaid active, TownCenterBlockEntity center, long now) {
		if (!raidsOn(level)) {
			discardRaiders(level, active);
			finish(level, raids, now, false);
			return;
		}
		ServerPlayer owner = ownerNearby(level, raids);
		ServerBossEvent bar = bar(level, raids.town());
		// The bar is memory-only. Players are attached below, so a reload during the warning has to put the
		// kind back on the title first. The lang line is "%s raid approaching"; with no argument the client
		// shows that "%s" literally.
		if (active.phase() == Phase.WARNING) {
			bar.setName(warningBarName(active.kind()));
		}
		updateBarPlayers(level, bar, raids.town());
		switch (active.phase()) {
			case WARNING -> {
				int warnTicks = warnTicks(level, raids.town());
				bar.setProgress(1.0F - (float) (now - active.phaseStart()) / warnTicks);
				smokeSignal(level, active.spawn());
				if (now - active.phaseStart() >= warnTicks) {
					List<UUID> raiders = spawnRaiders(level, raids.town(), active);
					RaidRegistry.put(level, raids.withActive(Optional.of(active.withPhase(Phase.ATTACK, now)
							.withRaiders(raiders, raiders.size()))));
				}
			}
			case ATTACK -> {
				// active.raiders() loses a raider when it dies or a portal takes it to another dimension, so one in an
				// unloaded chunk still counts as standing.
				// Checked before the owner: chasing the last raider far from town still wins the raid.
				int left = active.raiders().size();
				bar.setName(Component.translatable("bossbar.warbands.raid", active.kind().displayName(), left));
				bar.setProgress(active.total() == 0 ? 0.0F : (float) left / active.total());
				if (left == 0) {
					victory(level, raids, active, center, now);
					return;
				}
				if (owner == null) {
					// Paused while the owner is away (or the town unloaded): no looting, and the timeout clock stands still.
					return;
				}
				List<SoldierEntity> alive = alive(level, active);
				// They loot only an undefended Town Center: while you or your soldiers stand near it, they fight.
				boolean atCore = !defended(level, raids) && alive.stream()
						.anyMatch(r -> r.distanceToSqr(raids.town().getCenter()) <= PLUNDER_RANGE * PLUNDER_RANGE);
				int ticks = atCore ? active.plunderTicks() + CHECK_INTERVAL : 0;
				int elapsed = active.elapsed() + CHECK_INTERVAL;
				if (ticks >= PLUNDER_TICKS) {
					int[] taken = center.plunder(PLUNDER_SHARE);
					owner.sendSystemMessage(Component.translatable("message.warbands.raid_plundered", taken[Resource.FOOD.ordinal()],
							taken[Resource.WOOD.ordinal()], taken[Resource.STONE.ordinal()], taken[Resource.GOLD.ordinal()])
							.withStyle(ChatFormatting.RED));
					retreat(level, raids, active, now);
				} else if (elapsed > TIMEOUT_TICKS) {
					owner.sendSystemMessage(Component.translatable("message.warbands.raid_timeout").withStyle(ChatFormatting.GRAY));
					retreat(level, raids, active, now);
				} else {
					RaidRegistry.put(level, raids.withActive(Optional.of(active.withProgress(ticks, elapsed))));
				}
			}
			case RETREAT -> {
				List<SoldierEntity> alive = alive(level, active);
				bar.setName(Component.translatable("bossbar.warbands.raid_retreat"));
				bar.setProgress(0.0F);
				for (SoldierEntity raider : alive) {
					if (raider.blockPosition().distSqr(raids.town()) > 40.0 * 40.0) {
						discard(raider);
					}
				}
				if (alive.isEmpty() || now - active.phaseStart() >= RETREAT_TICKS) {
					discardRaiders(level, active);
					finish(level, raids, now, false);
				}
			}
		}
	}

	/** A tall column of smoke, with flames at its foot, where the warband will appear: visible from the Town Center. */
	private static void smokeSignal(ServerLevel level, BlockPos spawn) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
		for (int i = 0; i < 4; i++) {
			level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, true, spawn.getX() + 0.5 + (i % 2) * 0.6 - 0.3, y + 0.5,
					spawn.getZ() + 0.5 + (i / 2) * 0.6 - 0.3, 0, 0.0, 0.1, 0.0, 1.0);
		}
		level.sendParticles(ParticleTypes.FLAME, true, true, spawn.getX() + 0.5, y + 0.3, spawn.getZ() + 0.5, 12, 0.5, 0.2, 0.5, 0.02);
	}

	/** The town's warning length: the Frost Sigil calls raids half again as early. */
	private static int warnTicks(ServerLevel level, BlockPos town) {
		return Relics.has(level, town, CampTier.FROZEN_OUTPOST) ? WARNING_TICKS + WARNING_TICKS / 2 : WARNING_TICKS;
	}

	private static void retreat(ServerLevel level, TownRaids raids, ActiveRaid active, long now) {
		for (SoldierEntity raider : alive(level, active)) {
			raider.setTarget(null);
			raider.setMarchTarget(active.spawn());
		}
		RaidRegistry.put(level, raids.withActive(Optional.of(active.withPhase(Phase.RETREAT, now))));
	}

	/** You, or one of your soldiers, stand within {@link #DEFEND_RANGE} of the Town Center. */
	private static boolean defended(ServerLevel level, TownRaids raids) {
		AABB area = new AABB(raids.town()).inflate(DEFEND_RANGE);
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(raids.owner());
		if (owner != null && owner.isAlive() && !owner.isSpectator() && area.contains(owner.position())) {
			return true;
		}
		return !level.getEntitiesOfClass(SoldierEntity.class, area,
				s -> s.isAlive() && !s.isRaider() && raids.owner().equals(s.ownerId())).isEmpty();
	}

	/** Victory: a big title and a fanfare wherever the owner is, fireworks over the Town Center, gold in the stockpile. */
	private static void victory(ServerLevel level, TownRaids raids, ActiveRaid active, TownCenterBlockEntity center, long now) {
		int gold = active.total() * RaidComposition.goldPerRaider(active.age());
		center.addStock(Resource.GOLD, gold);
		Spoils.Site spoils = leaveSpoils(level, raids, active);
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(raids.owner());
		if (owner != null) {
			owner.sendSystemMessage(Component.translatable("message.warbands.raid_won", gold).withStyle(ChatFormatting.GREEN));
			if (spoils != null) {
				owner.sendSystemMessage(Component.translatable("message.warbands.raid_spoils",
						Directions.compass(raids.town(), spoils.chest()), Directions.distance(raids.town(), spoils.chest()))
						.withStyle(ChatFormatting.GOLD));
			}
			Directions.title(owner, Component.translatable("message.warbands.raid_won_title"),
					Component.translatable("message.warbands.raid_won_subtitle", gold));
			owner.level().playSound(null, owner.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
			owner.level().playSound(null, owner.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 1.0F, 1.6F);
			WarbandsAdvancements.award(owner, WarbandsAdvancements.RAID_DEFENDED);
		}
		BlockPos town = raids.town();
		level.sendParticles(ParticleTypes.FIREWORK, town.getX() + 0.5, town.getY() + 8, town.getZ() + 0.5, 150, 3.0, 2.0, 3.0, 0.15);
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, town.getX() + 0.5, town.getY() + 4, town.getZ() + 0.5, 80, 2.0, 2.0, 2.0, 0.3);
		level.playSound(null, town, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.NEUTRAL, 4.0F, 1.0F);
		finish(level, raids.withNext(raids.nextRaid(), raids.won() + 1, raids.retaliation()), now, true);
	}

	/**
	 * The raiders' war chest, left where they came from (or in a ring around the Town Center if the spawn is
	 * unreachable), with a signal campfire burning beside it: the message tells the owner to follow the smoke.
	 * Null when nowhere fits; the gold payout still happens.
	 */
	private static Spoils.@Nullable Site leaveSpoils(ServerLevel level, TownRaids raids, ActiveRaid active) {
		Spoils.Site site = Spoils.site(level, active.spawn().getX(), active.spawn().getZ(), 0, 4);
		if (site == null) {
			site = Spoils.site(level, raids.town().getX(), raids.town().getZ(), 3, 6);
		}
		if (site == null) {
			return null;
		}
		Direction facing = Direction.getApproximateNearest(raids.town().getX() - site.chest().getX(), 0,
				raids.town().getZ() - site.chest().getZ());
		Spoils.chest(level, site.chest(), facing, active.kind().lootTable(), "container.warbands.raiders_war_chest");
		Spoils.campfire(level, site.fire());
		return site;
	}

	private static void finish(ServerLevel level, TownRaids raids, long now, boolean won) {
		long next = now + MIN_INTERVAL + level.getRandom().nextInt(MAX_INTERVAL - MIN_INTERVAL + 1);
		// Keep the bag and the last kind: they are what stops the same kind coming twice in a row.
		RaidRegistry.put(level, new TownRaids(raids.town(), raids.owner(), next, raids.won(), false, Optional.empty(),
				raids.bag(), raids.last()));
		removeBar(level, raids.town());
	}

	/** Ends a raid's one-minute warning now. For the game test, which can't wait a minute of real time. */
	public static void skipWarning(ServerLevel level, BlockPos town) {
		RaidRegistry.get(level, town).ifPresent(raids -> raids.active().ifPresent(active -> {
			if (active.phase() == Phase.WARNING) {
				RaidRegistry.put(level, raids.withActive(
						Optional.of(active.withPhase(Phase.WARNING, level.getGameTime() - warnTicks(level, raids.town())))));
			}
		}));
	}

	/**
	 * A raider of the raid on {@code town} died: it no longer counts toward the ones left standing. The commander hears
	 * it wherever they are (a chime that rises as the warband thins), sees "Raider down · N left", and a puff of soul
	 * smoke marks where it fell; the boss bar updates at once.
	 */
	public static void raiderDied(ServerLevel level, BlockPos town, SoldierEntity raider) {
		level.sendParticles(ParticleTypes.SOUL, raider.getX(), raider.getY() + 1.0, raider.getZ(), 12, 0.3, 0.5, 0.3, 0.03);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, raider.getX(), raider.getY() + 0.8, raider.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
		RaidRegistry.get(level, town).ifPresent(raids -> raids.active().ifPresent(active -> {
			if (!active.raiders().contains(raider.getUUID())) {
				return;
			}
			List<UUID> left = new ArrayList<>(active.raiders());
			left.remove(raider.getUUID());
			RaidRegistry.put(level, raids.withActive(Optional.of(active.withRaiders(left, active.total()))));
			if (active.phase() == Phase.ATTACK) {
				ServerBossEvent bar = bar(level, town);
				bar.setName(Component.translatable("bossbar.warbands.raid", active.kind().displayName(), left.size()));
				bar.setProgress(active.total() == 0 ? 0.0F : (float) left.size() / active.total());
			}
			ServerPlayer owner = level.getServer().getPlayerList().getPlayer(raids.owner());
			if (owner != null && owner.level() == level) {
				float pitch = 0.8F + 1.0F * (1.0F - (active.total() == 0 ? 0.0F : (float) left.size() / active.total()));
				level.playSound(null, owner.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0F, pitch);
				owner.sendOverlayMessage(Component.translatable("message.warbands.raider_down", left.size()).withStyle(ChatFormatting.GOLD));
			}
		}));
	}

	/** Days until the next raid on {@code town}, plus one (1 = tonight); 0 when none is planned or one is on now. */
	public static int daysUntilNext(ServerLevel level, BlockPos town) {
		if (!raidsOn(level)) {
			return 0;
		}
		return RaidRegistry.get(level, town).filter(raids -> raids.active().isEmpty())
				.map(raids -> 1 + (int) Math.max(0, Math.ceil((raids.nextRaid() - level.getGameTime()) / (double) DAY)))
				.orElse(0);
	}

	/** Whether raids happen in this world at all (difficulty and gamerule; not whether the owner is near). */
	public static boolean raidsOn(ServerLevel level) {
		return level.getDifficulty() != Difficulty.PEACEFUL && level.getGameRules().get(ModGameRules.RAIDS);
	}

	/**
	 * Schedules a revenge raid for the coming night, one raider bigger. From the Feudal Age on. A town that reached
	 * Feudal but hasn't been put on the raid calendar yet (its chunk never ticked since) is added now.
	 */
	public static void retaliate(ServerLevel level, BlockPos town, UUID owner) {
		TownRaids raids = RaidRegistry.get(level, town)
				.orElse(new TownRaids(town, owner, level.getGameTime(), 0, false, Optional.empty()));
		if (raids.active().isEmpty()) {
			RaidRegistry.put(level, raids.withNext(Math.min(raids.nextRaid(), level.getGameTime()), raids.won(), true));
		}
	}

	private static List<UUID> spawnRaiders(ServerLevel level, BlockPos town, ActiveRaid active) {
		List<UUID> ids = new ArrayList<>();
		List<UnitType> units = RaidComposition.units(active.kind(), active.age(), active.total());
		for (int i = 0; i < units.size(); i++) {
			UnitType type = units.get(i);
			SoldierEntity raider = type.entityType().create(level, EntitySpawnReason.EVENT);
			if (raider == null) {
				continue;
			}
			double angle = i * 2.4;
			int x = active.spawn().getX() + Mth.floor(Math.cos(angle) * (1 + i % 3));
			int z = active.spawn().getZ() + Mth.floor(Math.sin(angle) * (1 + i % 3));
			int ground = Ground.surfaceIfLoaded(level, x, z);
			if (ground == Integer.MIN_VALUE || Math.abs(ground - active.spawn().getY()) > 4) {
				// An unloaded column reads as the bottom of the world; a cliff edge as a drop. Use the checked spot.
				x = active.spawn().getX();
				z = active.spawn().getZ();
				ground = active.spawn().getY() - 1;
			}
			int y = ground + 1;
			raider.snapTo(x + 0.5, y, z + 0.5, (float) Math.toDegrees(Math.atan2(town.getX() - x, z - town.getZ())), 0.0F);
			raider.finalizeSpawn(level, level.getCurrentDifficultyAt(active.spawn()), EntitySpawnReason.EVENT, null);
			raider.makeRaider(active.age(), town);
			Threat.raid(level).apply(raider, false);
			active.kind().dress(raider);
			if (type == UnitType.RAM) {
				raider.command(Stance.CHARGE); // rams break doors and gates on their way in
			}
			raider.setMarchTarget(town);
			level.addFreshEntity(raider);
			ids.add(raider.getUUID());
		}
		return ids;
	}

	/** A dry surface spot 48-64 blocks from the town, from a random direction. Null if every direction is water. */
	private static @Nullable BlockPos findSpawn(ServerLevel level, BlockPos town) {
		double start = level.getRandom().nextDouble() * Math.PI * 2;
		for (int attempt = 0; attempt < 12; attempt++) {
			double angle = start + attempt * Math.PI / 6;
			int distance = SPAWN_MIN + level.getRandom().nextInt(SPAWN_MAX - SPAWN_MIN + 1);
			int x = town.getX() + Mth.floor(Math.cos(angle) * distance);
			int z = town.getZ() + Mth.floor(Math.sin(angle) * distance);
			int surface = Ground.surfaceIfLoaded(level, x, z);
			if (surface == Integer.MIN_VALUE) {
				continue; // not loaded: its heightmap would say "bottom of the world"
			}
			int y = surface + 1;
			BlockPos ground = new BlockPos(x, surface, z);
			if (level.getFluidState(ground).isEmpty() && level.getFluidState(ground.above()).isEmpty()
					&& Math.abs(y - town.getY()) < 24) {
				return ground.above();
			}
		}
		return null;
	}

	private static List<SoldierEntity> alive(ServerLevel level, ActiveRaid active) {
		List<SoldierEntity> alive = new ArrayList<>();
		for (UUID id : active.raiders()) {
			Entity entity = level.getEntity(id);
			if (entity instanceof SoldierEntity raider && raider.isAlive()) {
				alive.add(raider);
			}
		}
		return alive;
	}

	private static void discardRaiders(ServerLevel level, ActiveRaid active) {
		alive(level, active).forEach(Raids::discard);
	}

	/** Removes a raider and the warhorse under it. */
	private static void discard(SoldierEntity raider) {
		Entity vehicle = raider.getVehicle();
		raider.discard();
		if (vehicle != null) {
			vehicle.discard();
		}
	}

	private static String key(ServerLevel level, BlockPos town) {
		return level.dimension().identifier() + "@" + town.asLong();
	}

	private static ServerBossEvent bar(ServerLevel level, BlockPos town) {
		return BARS.computeIfAbsent(key(level, town), k -> new ServerBossEvent(UUID.randomUUID(),
				Component.translatable("bossbar.warbands.raid_warning"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10));
	}

	/** Warning title. The kind is the {@code %s} in {@code bossbar.warbands.raid_warning}. */
	static Component warningBarName(RaidKind kind) {
		return Component.translatable("bossbar.warbands.raid_warning", kind.displayName());
	}

	/** Everyone near the town sees the bar. */
	private static void updateBarPlayers(ServerLevel level, ServerBossEvent bar, BlockPos town) {
		for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
			if (player.level() != level || player.blockPosition().distSqr(town) > (double) OWNER_RANGE * OWNER_RANGE) {
				bar.removePlayer(player);
			}
		}
		for (ServerPlayer player : level.players()) {
			if (player.blockPosition().distSqr(town) <= (double) OWNER_RANGE * OWNER_RANGE) {
				bar.addPlayer(player);
			}
		}
	}

	private static void removeBar(ServerLevel level, BlockPos town) {
		ServerBossEvent bar = BARS.remove(key(level, town));
		if (bar != null) {
			bar.removeAllPlayers();
		}
	}
}
