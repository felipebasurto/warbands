package dev.felipe.warbands.camp;

import dev.felipe.warbands.Warbands;
import dev.felipe.warbands.WarbandsAdvancements;
import dev.felipe.warbands.Directions;
import dev.felipe.warbands.Ground;
import dev.felipe.warbands.town.Relics;
import dev.felipe.warbands.town.StructureBlocks;
import dev.felipe.warbands.town.TownRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import dev.felipe.warbands.blueprint.BlueprintPlacer;
import dev.felipe.warbands.camp.CampRegistry.Camp;
import dev.felipe.warbands.entity.SoldierEntity;
import dev.felipe.warbands.entity.WarlordEntity;
import dev.felipe.warbands.raid.Raids;
import dev.felipe.warbands.registry.ModBlocks;
import dev.felipe.warbands.registry.ModEntities;
import dev.felipe.warbands.registry.ModItems;
import dev.felipe.warbands.town.Age;
import dev.felipe.warbands.town.Resource;
import dev.felipe.warbands.town.TownCenterBlockEntity;
import dev.felipe.warbands.town.UnitType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.resources.Identifier;
import net.minecraft.core.QuartPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jspecify.annotations.Nullable;

/**
 * War Maps and enemy camps. Buying a map picks a dry site 250-400 blocks from the town, away from other camps and towns
 * (from the world generator's height estimate, so nothing is loaded), and hands over a real map with the camp and the
 * town marked. The camp is built once a player comes within 128 blocks and its guards hold their posts. When they're all
 * dead a beam marks the Camp Standard; the owner claims it: the loot goes to the Town Center that sold the map, the owner
 * gets the camp's trophy (the key to the next age), the camp burns down, the map now leads home, and (from the Feudal Age)
 * that town can expect a revenge raid the next night.
 */
public final class Camps {
	public static final int MIN_DISTANCE = 250;
	public static final int MAX_DISTANCE = 400;
	public static final int BUILD_RANGE = 128;
	/** Natural terrain cleared around a camp's walls, and how far the lane out of its gate runs (see clearApproach). */
	private static final int APPROACH_MARGIN = 3;
	private static final int APPROACH_LANE = 10;
	/** Ground guards chase this far from their spot (out through the gate a little), then walk back. */
	private static final int GUARD_RADIUS = 20;
	private static final int CHECK_INTERVAL = 40;
	/** Past the 250-400 ring, if every site there is too close to another camp or town. */
	private static final int FAR_DISTANCE = 500;
	private static final int THEMED_FAR_DISTANCE = 900;
	/** Camps and other Town Centers keep at least this far from a new camp. */
	private static final int CAMP_SPACING = 160;
	/** The owner must be this close for the "camp cleared" title and the beam over the standard. */
	private static final int CLEARED_RANGE = 64;
	/** How long a taken camp burns: the fire takes hold, the camp falls in from the roofs down, then it smoulders out. */
	private static final int BURN_TICKS = 50 * 20;
	/** The fire advances every this many ticks. */
	private static final int BURN_STEP = 5;
	/** The camp's foundation goes this deep (BlueprintPlacer's MAX_FOUNDATION_DEPTH); it turns to dirt with the floor. */
	private static final int FOUNDATION_DEPTH = 8;
	/** Once a camp is under attack, its ground guards chase this far from their spot: no picking them off from outside. */
	private static final int SALLY_RADIUS = 48;
	/** Custom data key on a War Map: the camp's x/z (BlockPos.asLong with y 0). */
	private static final String MAP_TAG = "warbands_camp";
	/** Custom data key on a War Map whose camp was taken: the Town Center it leads back to. */
	private static final String HOME_TAG = "warbands_home";
	/** Entity tag on a camp's vanilla-mob guards: they drop nothing and give no XP (see {@code CampGuardMixin}). */
	public static final String GUARD_TAG = "warbands_camp_guard";
	/** Entity tag on a commander's waiting trophy/relic: hoppers can't take it (see {@code HopperRewardMixin}). */
	public static final String REWARD_TAG = "warbands_camp_reward";
	/** Burn order of each burning camp, by level and center. Not saved: rebuilt from what's still standing. */
	private static final Map<String, Burn> BURNS = new HashMap<>();
	/** Last alarm per camp (level and center), for the war relays. Not saved: a camp calms down over a restart. */
	private static final Map<String, Long> ALARMS = new HashMap<>();

	/** Forgets burns and alarms of a world that has closed (statics outlive a singleplayer world in one session). */
	public static void clearState() {
		BURNS.clear();
		ALARMS.clear();
	}
	/** A camp stays alarmed (its war relays powered) this long after the last alarm or sighting. */
	private static final int ALARM_HOLD = 60 * 20;

	private Camps() {
	}

	// ---- buying a War Map ----

	/** Why this town can't sell a War Map right now, or null if it can. */
	public static @Nullable Component whyNot(ServerLevel level, TownCenterBlockEntity town, UUID owner) {
		if (CampRegistry.active(level, owner).isPresent()) {
			return Component.translatable("message.warbands.war_map_active");
		}
		CampTier price = CampTier.forAge(town.age());
		for (Resource resource : Resource.values()) {
			if (town.stock(resource) < price.cost(resource)) {
				return Component.translatable("message.warbands.war_map_resources", town.age().displayName());
			}
		}
		return null;
	}

	/** The Town Center's War Map button: draws the camp kind from the player's bag. */
	public static boolean buy(ServerLevel level, TownCenterBlockEntity town, Player player) {
		return buy(level, town, player, null);
	}

	/**
	 * As {@link #buy}, but {@code forced} chooses the kind (the operator command and the game test use it; it doesn't
	 * touch the bag). When the drawn kind's home biomes can't be found nearby, the map falls back to the age's plain
	 * kind on any dry site.
	 */
	public static boolean buy(ServerLevel level, TownCenterBlockEntity town, Player player, @Nullable CampTier forced) {
		return buy(level, town, player, forced, true);
	}

	/**
	 * As {@link #buy(ServerLevel, TownCenterBlockEntity, Player, CampTier)}; {@code charge} false gives the map free
	 * (the {@code /warbands camp} command), still refusing while another camp is out.
	 */
	public static boolean buy(ServerLevel level, TownCenterBlockEntity town, Player player, @Nullable CampTier forced,
			boolean charge) {
		Component reason = charge ? whyNot(level, town, player.getUUID())
				: CampRegistry.active(level, player.getUUID()).isPresent()
						? Component.translatable("message.warbands.war_map_active") : null;
		if (reason != null) {
			player.sendOverlayMessage(reason);
			return false;
		}
		CampTier tier = forced != null ? forced : drawKind(level, player.getUUID(), town.age());
		BlockPos site = tier.hasBiomeHome() ? findSite(level, town.getBlockPos(), tier) : null;
		if (site == null && tier.hasBiomeHome() && forced == null) {
			tier = CampTier.forAge(town.age());
		}
		if (site == null) {
			site = findSite(level, town.getBlockPos());
		}
		if (site == null) {
			player.sendOverlayMessage(Component.translatable("message.warbands.war_map_no_site"));
			return false;
		}
		if (charge) {
			for (Resource resource : Resource.values()) {
				town.take(resource, tier.cost(resource));
			}
		}
		Direction facing = Direction.getApproximateNearest(site.getX() - town.getBlockPos().getX(), 0,
				site.getZ() - town.getBlockPos().getZ());
		// Turned like a blueprint placed by someone standing at the town: its entrance faces the town.
		Rotation rotation = BlueprintPlacer.rotationFor(facing.getAxis().isHorizontal() ? facing : Direction.NORTH);
		List<CampLayout> layouts = tier.drawable();
		CampLayout layout = layouts.get(level.getRandom().nextInt(layouts.size()));
		Threat estimate = Threat.estimate(level, player.getUUID(), tier.age(), layout.guards().size(), town.getBlockPos());
		CampRegistry.add(level, Camp.chosen(player.getUUID(), town.getBlockPos(), tier, site, rotation, layout.id(), estimate));
		ItemStack map = warMap(level, town.getBlockPos(), site, tier, layout, estimate);
		if (!player.getInventory().add(map)) {
			player.drop(map, false);
		}
		player.sendSystemMessage(Component.translatable("message.warbands.war_map_bought", tier.displayName(),
				Directions.distance(town.getBlockPos(), site)).withStyle(ChatFormatting.GOLD));
		if (player instanceof ServerPlayer serverPlayer) {
			WarbandsAdvancements.award(serverPlayer, WarbandsAdvancements.WAR_MAP);
		}
		return true;
	}

	/**
	 * Shift-click on the War Map button: gives up the current camp, with no refund, so a new map can be bought (or none,
	 * for a quiet spell). A camp not yet built is forgotten; a built one keeps standing and can still be taken.
	 */
	public static boolean abandon(ServerLevel level, Player player) {
		Camp camp = CampRegistry.active(level, player.getUUID()).orElse(null);
		if (camp == null) {
			player.sendOverlayMessage(Component.translatable("message.warbands.war_map_none"));
			return false;
		}
		if (camp.built()) {
			CampRegistry.replace(level, camp, camp.asAbandoned());
		} else {
			CampRegistry.remove(level, camp);
		}
		player.sendSystemMessage(Component.translatable("message.warbands.war_map_abandoned", camp.tier().displayName())
				.withStyle(ChatFormatting.GRAY));
		return true;
	}

	/**
	 * The kind a War Map leads to: the next draw from the player's per-age bag (a shuffled list of that age's kinds,
	 * refilled when empty, and a refill never serves the kind just drawn). Saved on the camps attachment so draws
	 * keep their order across restarts.
	 */
	private static CampTier drawKind(ServerLevel level, UUID owner, Age age) {
		CampRegistry.Bag bag = CampRegistry.bag(level, owner);
		List<CampTier> rest = bag.age() == age ? new ArrayList<>(bag.bag()) : new ArrayList<>();
		CampTier last = bag.age() == age ? bag.last().orElse(null) : null;
		if (rest.isEmpty()) {
			rest.addAll(CampTier.kinds(age));
			Util.shuffle(rest, level.getRandom());
			if (rest.size() > 1 && rest.get(0) == last) {
				rest.set(0, rest.set(rest.size() - 1, rest.get(0)));
			}
		}
		CampTier kind = rest.remove(0);
		CampRegistry.putBag(level, owner, new CampRegistry.Bag(age, List.copyOf(rest), Optional.of(kind)));
		return kind;
	}

	/**
	 * A dry, open spot 250-400 blocks away (400-500 if that ring is taken), judged from the world generator without
	 * loading chunks, and at least {@link #CAMP_SPACING} blocks from every other camp and every other Town Center.
	 */
	private static @Nullable BlockPos findSite(ServerLevel level, BlockPos town) {
		BlockPos site = findSite(level, town, MIN_DISTANCE, MAX_DISTANCE, null);
		return site != null ? site : findSite(level, town, MAX_DISTANCE, FAR_DISTANCE, null);
	}

	/** The site the game would pick for a war map of this kind around a town (the game test measures how often it finds one). */
	public static @Nullable BlockPos siteFor(ServerLevel level, BlockPos town, CampTier tier) {
		return tier.hasBiomeHome() ? findSite(level, town, tier) : findSite(level, town);
	}

	/** As {@link #findSite(ServerLevel, BlockPos)}, but only spots in one of the kind's home biomes. */
	private static @Nullable BlockPos findSite(ServerLevel level, BlockPos town, CampTier tier) {
		BlockPos site = findSite(level, town, MIN_DISTANCE, MAX_DISTANCE, tier);
		if (site == null) {
			site = findSite(level, town, MAX_DISTANCE, FAR_DISTANCE, tier);
		}
		// swamps, snow and mountains can be far from a town: a themed camp is worth a longer march than a plain one
		return site != null ? site : findSite(level, town, FAR_DISTANCE, THEMED_FAR_DISTANCE, tier);
	}

	private static @Nullable BlockPos findSite(ServerLevel level, BlockPos town, int min, int max, @Nullable CampTier kind) {
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		int sea = generator.getSeaLevel();
		double start = level.getRandom().nextDouble() * Math.PI * 2;
		// A biome-matched search tries more angles and rings before giving up.
		int attempts = kind != null ? 48 : 24;
		for (int attempt = 0; attempt < attempts; attempt++) {
			double angle = start + attempt * (Math.PI * 2 / 12) + (attempt >= 12 ? Math.PI / 12 * (attempt % 12) : 0);
			int distance = min + level.getRandom().nextInt(max - min + 1);
			int x = town.getX() + Mth.floor(Math.cos(angle) * distance);
			int z = town.getZ() + Mth.floor(Math.sin(angle) * distance);
			if (crowded(level, town, x, z)) {
				continue;
			}
			int surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState());
			if (kind != null && !kind.prefers(generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(surface),
					QuartPos.fromBlock(z), level.getChunkSource().randomState().sampler()))) {
				continue; // the biome test is cheap: the terrain probes below are not
			}
			int floor = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, level.getChunkSource().randomState());
			if (unsuitable(level, generator, x, z, surface, floor, kind)) {
				continue;
			}
			return new BlockPos(x, surface, z);
		}
		return null;
	}

	/**
	 * Ground the game never builds a camp of this kind on: open water, cliffs and ravines. A dock may have water round
	 * it (it sits on an inlet) and a witch coven stands in shallow swamp water, so those two skip the water rules.
	 */
	private static boolean unsuitable(ServerLevel level, ChunkGenerator generator, int x, int z, int surface, int floor, @Nullable CampTier kind) {
		int sea = generator.getSeaLevel();
		boolean wet = kind == CampTier.WITCH_COVEN;
		if (wet ? surface < sea - 2 || surface - floor > 3 : surface <= sea || surface != floor) {
			return true;
		}
		return tooSteep(level, generator, x, z, surface, kind == CampTier.SMUGGLERS_DOCK || wet);
	}

	/** {@link #unsuitable} for a site the game test picked: false when the game could build this kind there. */
	public static boolean unsuitableSite(ServerLevel level, int x, int z, CampTier kind) {
		var generator = level.getChunkSource().getGenerator();
		var random = level.getChunkSource().randomState();
		int surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random);
		int floor = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
		return unsuitable(level, generator, x, z, surface, floor, kind);
	}

	/** Camps need reasonably even ground: a cliff or ravine edge leaves the gate hanging or buried. */
	public static boolean tooSteep(ServerLevel level, int x, int z) {
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		int surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState());
		return tooSteep(level, generator, x, z, surface, false);
	}

	private static boolean tooSteep(ServerLevel level, ChunkGenerator generator, int x, int z, int surface, boolean waterOk) {
		int sea = generator.getSeaLevel();
		for (int dx = -12; dx <= 12; dx += 12) {
			for (int dz = -12; dz <= 12; dz += 12) {
				int h = generator.getBaseHeight(x + dx, z + dz, Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState());
				if (h <= sea && waterOk) {
					continue; // a dock sits on an inlet: water round it is the point
				}
				if (Math.abs(h - surface) > 4 || h <= sea) {
					return true;
				}
			}
		}
		return false;
	}

	/** Too close to a camp (standing, taken or burnt) or to someone else's Town Center. */
	private static boolean crowded(ServerLevel level, BlockPos town, int x, int z) {
		BlockPos spot = new BlockPos(x, 0, z);
		for (Camp camp : CampRegistry.all(level)) {
			if (Directions.distance(camp.center(), spot) < CAMP_SPACING) {
				return true;
			}
		}
		for (TownRegistry.Town other : TownRegistry.towns(level)) {
			if (!other.pos().equals(town) && Directions.distance(other.pos(), spot) < CAMP_SPACING) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A real map, like a cartographer's explorer map: the land drawn in, a red X on the camp, a blue banner on the
	 * Town Center that sold it, and your own arrow. Maps snap to a grid, so it takes the smallest scale that shows
	 * both. Tagged with the camp so claiming the camp can take the map back.
	 */
	private static ItemStack warMap(ServerLevel level, BlockPos town, BlockPos site, CampTier tier, CampLayout layout,
			Threat threat) {
		int midX = (town.getX() + site.getX()) / 2;
		int midZ = (town.getZ() + site.getZ()) / 2;
		byte scale = 4;
		for (byte s = 2; s < 4; s++) {
			if (fits(midX, midZ, s, town) && fits(midX, midZ, s, site)) {
				scale = s;
				break;
			}
		}
		ItemStack map = MapItem.create(level, midX, midZ, scale, true, true);
		MapItem.renderBiomePreviewMap(level, map);
		MapItemSavedData.addTargetDecoration(map, site, "warbands_camp", MapDecorationTypes.RED_X);
		MapItemSavedData.addTargetDecoration(map, town, "warbands_town", MapDecorationTypes.BLUE_BANNER);
		map.set(DataComponents.CUSTOM_NAME, Component.translatable("item.warbands.war_map.named", tier.displayName()));
		map.set(DataComponents.LORE, new ItemLore(List.of(
				Component.translatable("tooltip.warbands.war_map.camp").withStyle(ChatFormatting.RED),
				Component.translatable("tooltip.warbands.war_map.town").withStyle(ChatFormatting.BLUE),
				Component.translatable("tooltip.warbands.war_map.threat", threat.starText()).withStyle(ChatFormatting.GOLD),
				Component.translatable("tooltip.warbands.war_map.scouts", layout.scoutReport()).withStyle(ChatFormatting.GRAY))));
		CompoundTag tag = new CompoundTag();
		tag.putLong(MAP_TAG, new BlockPos(site.getX(), 0, site.getZ()).asLong());
		map.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return map;
	}

	/** Whether {@code pos} lands inside the map vanilla would make around (x, z) at {@code scale}, with a margin. */
	private static boolean fits(int x, int z, byte scale, BlockPos pos) {
		int size = 128 * (1 << scale);
		int centerX = Mth.floor((x + 64.0) / size) * size + size / 2 - 64;
		int centerZ = Mth.floor((z + 64.0) / size) * size + size / 2 - 64;
		int limit = size / 2 - 12;
		return Math.abs(pos.getX() - centerX) < limit && Math.abs(pos.getZ() - centerZ) < limit;
	}

	/** The camp a War Map points at (its x/z, y 0), or null for any other item or a map whose camp was taken. */
	public static @Nullable BlockPos mapTarget(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || !stack.is(Items.FILLED_MAP) || data.copyTag().contains(HOME_TAG)) {
			return null;
		}
		long packed = data.copyTag().getLongOr(MAP_TAG, Long.MIN_VALUE);
		return packed == Long.MIN_VALUE ? null : BlockPos.of(packed);
	}

	// ---- building camps and paying out ----

	/** Called every server tick per level: map hints, building camps players approach, cleared and burning camps. */
	public static void tick(ServerLevel level) {
		long now = level.getGameTime();
		if (now % 10 == 0) {
			for (ServerPlayer player : level.players()) {
				if (holdsWarMap(player)) {
					player.sendOverlayMessage(hint(level, player));
				}
			}
		}
		if (now % BURN_STEP == 0) {
			for (Camp camp : List.copyOf(CampRegistry.all(level))) {
				if (camp.burning()) {
					tickBurning(level, camp, now);
				}
			}
		}
		if (now % CHECK_INTERVAL != 0) {
			return;
		}
		for (Camp camp : List.copyOf(CampRegistry.all(level))) {
			if (!camp.built()) {
				// Only once every chunk of the footprint and its approach is loaded and ticking: isLoaded alone is also true
				// at the edge of view, where guards would freeze and the clearing would load chunks on the spot.
				if (!camp.abandoned() && playerNear(level, camp.center())
						&& Ground.ready(level, camp.center(), camp.layout().half() + APPROACH_LANE + 1)) {
					build(level, camp);
				}
			} else if (!camp.conquered()) {
				tickStanding(level, camp);
			}
		}
	}

	/**
	 * Once the last guard falls, tell the owner (big title, a horn) and keep a beam of light over the standard so it's
	 * easy to find, even inside the Keep.
	 */
	private static void tickStanding(ServerLevel level, Camp camp) {
		BlockPos standard = standardPos(camp);
		if (!level.isLoaded(standard) || !playerNear(level, standard, CLEARED_RANGE)) {
			return;
		}
		if (guardsLeft(level, camp) > 0) {
			rouse(level, camp);
			return;
		}
		for (int i = 0; i < 12; i++) {
			level.sendParticles(ParticleTypes.END_ROD, standard.getX() + 0.5, standard.getY() + 2 + i * 1.5, standard.getZ() + 0.5,
					2, 0.1, 0.4, 0.1, 0.0);
		}
		if (!camp.cleared()) {
			CampRegistry.replace(level, camp, camp.asCleared());
			if (level.getServer().getPlayerList().getPlayer(camp.owner()) instanceof ServerPlayer owner && owner.level() == level) {
				owner.sendOverlayMessage(Component.translatable("message.warbands.camp_cleared_subtitle"));
				level.playSound(null, owner.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 0.6F, 1.4F);
			}
		}
	}

	/**
	 * A camp under attack fights back as one: once any guard is hurt or has spotted an enemy, the gates are thrown open
	 * and the ground guards sally out after the attacker, far past their usual post. Without this, a player could open
	 * one gate a crack and shoot the whole camp through it without ever being reached.
	 */
	private static void rouse(ServerLevel level, Camp camp) {
		List<Mob> guards = new ArrayList<>();
		LivingEntity enemy = null;
		for (UUID id : camp.guards()) {
			if (level.getEntity(id) instanceof Mob guard && guard.isAlive()) {
				guards.add(guard);
				LivingEntity attacker = guard.getLastHurtByMob();
				if (attacker != null && attacker.isAlive() && !(attacker instanceof SoldierEntity other && other.isRaider())) {
					enemy = attacker;
				} else if (enemy == null && guard.getTarget() != null && guard.getTarget().isAlive()) {
					enemy = guard.getTarget();
				}
			}
		}
		if (enemy == null) {
			return;
		}
		sally(level, camp, guards, enemy);
	}

	/** An alarm plate rings: the camp wakes and sallies out after the intruder that tripped it. */
	public static void alarm(ServerLevel level, Camp camp, LivingEntity intruder) {
		List<Mob> guards = new ArrayList<>();
		for (UUID id : camp.guards()) {
			if (level.getEntity(id) instanceof Mob guard && guard.isAlive()) {
				guards.add(guard);
			}
		}
		sally(level, camp, guards, intruder);
	}

	/** Gates open and every unposted guard widens its beat and goes for {@code enemy}. */
	private static void sally(ServerLevel level, Camp camp, List<Mob> guards, LivingEntity enemy) {
		ALARMS.put(level.dimension() + "@" + camp.center().asLong(), level.getGameTime());
		openGates(level, camp);
		for (Mob guard : guards) {
			if (guard instanceof SoldierEntity soldier && soldier.isPosted()) {
				continue;
			}
			if (guard.hasHome() && guard.getHomeRadius() < SALLY_RADIUS) {
				guard.setHomeTo(guard.getHomePosition(), SALLY_RADIUS);
			}
			if (guard.getTarget() == null && guard.distanceToSqr(enemy) <= (double) SALLY_RADIUS * SALLY_RADIUS) {
				guard.setTarget(enemy);
			}
		}
	}

	/** Opens every closed fence gate in the camp (and keeps them open if the player shuts them again). */
	private static void openGates(ServerLevel level, Camp camp) {
		int half = camp.layout().half();
		BlockPos c = camp.center();
		boolean opened = false;
		for (BlockPos pos : BlockPos.betweenClosed(c.offset(-half, 1, -half), c.offset(half, 3, half))) {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN)) {
				level.setBlock(pos, state.setValue(FenceGateBlock.OPEN, true), Block.UPDATE_CLIENTS);
				opened = true;
			}
		}
		if (opened) {
			level.playSound(null, c, SoundEvents.FENCE_GATE_OPEN, SoundSource.BLOCKS, 2.0F, 0.8F);
			level.playSound(null, c, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4.0F, 1.2F);
		}
	}

	/**
	 * Every structure block of a burning camp, in the order the fire takes it: roughly top to bottom with some noise,
	 * so roofs and towers fall in first and the walls go block by block. The floor and the foundation come last and
	 * turn into plain dirt, so the site grows back into the landscape. {@code at} is the burn progress (0-1) at which
	 * each block goes.
	 */
	private static final class Burn {
		private final List<BlockPos> order;
		private final float[] at;
		private int next;

		private Burn(ServerLevel level, Camp camp) {
			BlockPos c = camp.center();
			int half = camp.layout().half();
			int height = Math.max(1, camp.layout().height());
			List<BlockPos> blocks = new ArrayList<>();
			Map<BlockPos, Float> keys = new HashMap<>();
			for (BlockPos pos : BlockPos.betweenClosed(c.offset(-half, -FOUNDATION_DEPTH, -half), c.offset(half, height, half))) {
				if (!StructureBlocks.contains(level, pos) && !movable(level.getBlockState(pos))) {
					continue;
				}
				int rel = pos.getY() - c.getY();
				float noise = noise(pos);
				float key = rel < 0 ? 0.99F : rel == 0 ? 0.8F + 0.19F * noise : 0.1F + 0.68F * (1.0F - (float) rel / height) + 0.14F * noise;
				BlockPos immutable = pos.immutable();
				blocks.add(immutable);
				keys.put(immutable, key);
			}
			blocks.sort((x, y) -> Float.compare(keys.get(x), keys.get(y)));
			this.order = blocks;
			this.at = new float[blocks.size()];
			for (int i = 0; i < blocks.size(); i++) {
				this.at[i] = keys.get(blocks.get(i));
			}
		}

		private static float noise(BlockPos pos) {
			// A real bit mixer: a plain multiply leaves straight diagonal stripes across the floor.
			long h = pos.asLong();
			h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
			h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
			h ^= h >>> 31;
			return ((h >>> 40) & 0xFFFF) / 65536.0F;
		}
	}

	/**
	 * A taken camp burns down over {@link #BURN_TICKS}: flames and smoke run along the blocks about to go, tall smoke
	 * columns rise that can be seen from far away, and the camp collapses from the top with debris and crackling, until
	 * only a patch of dirt is left. Blocks vanish without drops and without knocking off their neighbours, so nothing
	 * falls as loot.
	 */
	private static void tickBurning(ServerLevel level, Camp camp, long now) {
		if (!level.isLoaded(camp.center())) {
			return;
		}
		String key = level.dimension() + "@" + camp.center().asLong();
		Burn burn = BURNS.computeIfAbsent(key, k -> new Burn(level, camp));
		BlockPos c = camp.center();
		float progress = Mth.clamp(1.0F - (float) (camp.razeAt() - now) / BURN_TICKS, 0.0F, 1.0F);
		var random = level.getRandom();

		int fallen = 0;
		Map<SoundType, Integer> materials = new HashMap<>();
		while (burn.next < burn.order.size() && burn.at[burn.next] <= progress) {
			BlockPos pos = burn.order.get(burn.next++);
			BlockState state = level.getBlockState(pos);
			if (!StructureBlocks.release(level, pos) && !movable(state)) {
				continue;
			}
			if (pos.getY() <= c.getY()) {
				if (!state.isAir()) {
					level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
					if (pos.getY() == c.getY() && random.nextInt(4) == 0) {
						level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 1, 0.3, 0.1, 0.3, 0.02);
					}
				}
				continue;
			}
			if (state.isAir()) {
				continue;
			}
			if (fallen < 10 && random.nextInt(3) == 0) {
				level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state)); // debris and the block's break sound
			}
			level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.03);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1, 0.2, 0.2, 0.2, 0.03);
			materials.merge(state.getSoundType(), 1, Integer::sum);
			// No block-entity side effects either: a gun's leftover ammo burns with it instead of spilling as loot.
			level.setBlock(pos, Blocks.AIR.defaultBlockState(),
					Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
			fallen++;
		}
		if (fallen >= 12) {
			// The crash sounds like what fell: stone walls rumble, palisades crack.
			SoundType material = materials.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
			level.playSound(null, c, material.getBreakSound(), SoundSource.BLOCKS, 3.0F, 0.5F + random.nextFloat() * 0.2F);
		}

		if (burn.next >= burn.order.size() && progress >= 1.0F) {
			int half = camp.layout().half();
			level.sendParticles(ParticleTypes.LARGE_SMOKE, c.getX() + 0.5, c.getY() + 2, c.getZ() + 0.5, 80, half / 2.0, 2, half / 2.0, 0.05);
			level.sendParticles(ParticleTypes.WHITE_ASH, c.getX() + 0.5, c.getY() + 2, c.getZ() + 0.5, 120, half / 2.0, 1.5, half / 2.0, 0.02);
			level.playSound(null, c, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 3.0F, 0.7F);
			fillPit(level, c, half, camp.layout().sink());
			CampRegistry.replace(level, camp, camp.asRazed());
			BURNS.remove(key);
			return;
		}

		// The fire front: the next blocks to go blaze, strongest in the middle of the burn.
		float strength = Math.min(1.0F, Math.min(progress / 0.12F, (1.0F - progress) / 0.12F + 0.25F));
		int window = Math.min(burn.order.size() - burn.next, 160);
		for (int i = 0, flames = Math.round(14 * strength); i < flames && window > 0; i++) {
			BlockPos pos = burn.order.get(burn.next + random.nextInt(window));
			double x = pos.getX() + 0.5;
			double y = pos.getY() + 1.0;
			double z = pos.getZ() + 0.5;
			level.sendParticles(ParticleTypes.FLAME, x, y, z, 5, 0.35, 0.4, 0.35, 0.02);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.5, z, 1, 0.2, 0.3, 0.2, 0.02);
			if (random.nextInt(5) == 0) {
				level.sendParticles(ParticleTypes.LAVA, x, y, z, 1, 0.2, 0.1, 0.2, 0.0);
			}
		}
		int half = camp.layout().half();
		if (now % 20 == 0) {
			// Smoke columns everyone around can see: the camp is burning.
			for (int i = 0; i < 2 + Math.round(3 * strength); i++) {
				double x = c.getX() + random.nextInt(half * 2 + 1) - half + 0.5;
				double z = c.getZ() + random.nextInt(half * 2 + 1) - half + 0.5;
				level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, true, x, c.getY() + 2, z, 0, 0.0, 0.08, 0.0, 1.0);
			}
			level.sendParticles(ParticleTypes.ASH, c.getX() + 0.5, c.getY() + 4, c.getZ() + 0.5, 20, half / 2.0, 3, half / 2.0, 0.0);
			level.playSound(null, c.offset(random.nextInt(half * 2 + 1) - half, 2, random.nextInt(half * 2 + 1) - half),
					SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 3.0F, 0.6F + random.nextFloat() * 0.4F);
		}
		if (now % 60 == 0 && strength > 0.5F) {
			level.playSound(null, c, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 1.5F, 0.5F);
		}
	}

	private static boolean playerNear(ServerLevel level, BlockPos pos) {
		return playerNear(level, pos, BUILD_RANGE);
	}

	/**
	 * The built camp whose footprint contains {@code pos} (with a few blocks of margin for traps sitting just inside
	 * the walls), if any.
	 */
	public static Optional<Camp> campAt(ServerLevel level, BlockPos pos) {
		for (Camp camp : CampRegistry.all(level)) {
			if (!camp.built() || camp.razed()) {
				continue;
			}
			int half = camp.layout().half() + 4;
			BlockPos c = camp.center();
			if (Math.abs(pos.getX() - c.getX()) <= half && Math.abs(pos.getZ() - c.getZ()) <= half
					&& pos.getY() >= c.getY() - 6 && pos.getY() <= c.getY() + camp.layout().height() + 6) {
				return Optional.of(camp);
			}
		}
		return Optional.empty();
	}

	/**
	 * Whether the camp around {@code pos} is still fighting and was alarmed in the last {@link #ALARM_HOLD} ticks (an
	 * alarm plate, a horn, or its guards spotting or being hurt by an enemy). War relays are powered while it is.
	 */
	public static boolean alarmedAt(ServerLevel level, BlockPos pos) {
		Camp camp = campAt(level, pos).filter(c -> !c.conquered() && !c.abandoned()).orElse(null);
		if (camp == null) {
			return false;
		}
		Long last = ALARMS.get(level.dimension() + "@" + camp.center().asLong());
		return last != null && level.getGameTime() - last < ALARM_HOLD && guardsLeft(level, camp) > 0;
	}

	/**
	 * Camp contraption parts pistons may move (they drop nothing, so a moved one can't be mined for loot) and the
	 * burn-down sweeps away wherever they ended up.
	 */
	public static boolean movable(BlockState state) {
		return state.is(dev.felipe.warbands.registry.ModBlocks.PORTCULLIS) || state.is(dev.felipe.warbands.registry.ModBlocks.RIGGED_FLOOR);
	}

	/** Whether {@code pos} is inside a camp that is still fighting (built, not conquered, not abandoned). */
	public static boolean armedAt(ServerLevel level, BlockPos pos) {
		return campAt(level, pos).filter(camp -> !camp.conquered() && !camp.abandoned()).isPresent();
	}

	private static boolean playerNear(ServerLevel level, BlockPos pos, int range) {
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.blockPosition().distSqr(pos) <= (double) range * range) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Builds the camp on today's real ground and stations its guards. The tick calls it once the area is ready
	 * ({@link Ground#ready}); the game test calls it directly after force-loading the chunks.
	 */
	public static void build(ServerLevel level, Camp camp) {
		CampTier tier = camp.tier();
		CampLayout layout = camp.layout();
		int half = layout.half();
		int ground = Ground.footprint(level, camp.center(), half);
		if (ground == Integer.MIN_VALUE) {
			return;
		}
		int y = ground - layout.sink();
		BlockPos center = new BlockPos(camp.center().getX(), y, camp.center().getZ());
		StructurePlaceSettings settings = BlueprintPlacer.build(level, layout.structureId(), center, half, layout.height(),
				camp.rotation());
		if (settings == null) {
			return;
		}
		// a sunk camp's floor layer is at ground level, so its approach is cleared from there
		BlockPos ground0 = center.above(layout.sink());
		int above = layout.height() - layout.sink();
		clearApproach(level, ground0, half, above, camp.rotation().rotate(Direction.SOUTH));
		dev.felipe.warbands.blueprint.SiteCleanup.settle(level, ground0, half, above);
		BlockPos standard = center.offset(-half, 0, -half)
				.offset(StructureTemplate.calculateRelativePosition(settings, layout.core()));
		Spoils.stock(level, center, half, layout.height(), standard, tier);
		Threat threat = Threat.of(level, camp.owner(), tier.age(), layout.guards().size(), Threat.party(level, center));
		BlockPos origin = center.offset(-half, 0, -half);
		List<UUID> guards = new ArrayList<>();
		java.util.Set<BlockPos> usedSpots = new java.util.HashSet<>();
		for (CampTier.Guard guard : garrison(layout, tier.age(), threat.extraGuards())) {
			Mob mob = guard.type().create(level, EntitySpawnReason.STRUCTURE);
			if (mob == null) {
				continue;
			}
			BlockPos spot = origin.offset(StructureTemplate.calculateRelativePosition(settings, guard.spot()));
			if (!guard.onTower() && mob.getType() != ModEntities.WARLORD) {
				spot = spotNear(level, spot, usedSpots);
			}
			mob.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.STRUCTURE, null);
			boolean boss = false;
			if (mob instanceof SoldierEntity soldier) {
				soldier.makeRaider(tier.age(), null);
				soldier.setCampKind(tier.name().toLowerCase(java.util.Locale.ROOT));
				soldier.guard(spot, guard.onTower() ? SoldierEntity.POST_RANGE : GUARD_RADIUS, guard.onTower());
				if (soldier instanceof WarlordEntity warlord) {
					warlord.setBoss(tier == CampTier.KEEP);
					warlord.setCustomName(warlordName(tier));
					boss = true;
				}
			} else {
				// A vanilla camp guard: stays put-ish, never despawns, drops nothing and gives no XP (CampGuardMixin).
				mob.setPersistenceRequired();
				mob.setHomeTo(spot, GUARD_RADIUS);
				mob.addTag(GUARD_TAG);
				if (mob.is(EntityTypeTags.BURN_IN_DAYLIGHT)) {
					// Strays, skeletons and zombies would burn at dawn and leave the camp to be claimed for free.
					mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0,
							false, false));
				}
			}
			threat.apply(mob, boss);
			if (tier == CampTier.SUNKEN_CRYPT && !boss && !(mob instanceof SoldierEntity)) {
				hardenUndead(mob);
			}
			if (!level.addFreshEntity(mob)) {
				Warbands.LOGGER.warn("camp {} guard {} at {} was not added to the world", layout.id(),
						guard.type().toShortString(), spot.toShortString());
			}
			guards.add(mob.getUUID());
		}
		CampRegistry.replace(level, camp, camp.asBuilt(center, guards, threat));
		level.playSound(null, center, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 8.0F, 0.8F);
		for (ServerPlayer player : level.players()) {
			if (player.blockPosition().distSqr(center) <= (double) BUILD_RANGE * BUILD_RANGE) {
				player.sendSystemMessage(Component.translatable("message.warbands.camp_threat", tier.displayName(),
						threat.starText(), guards.size()).withStyle(ChatFormatting.GOLD));
			}
		}
	}

	/**
	 * The guards a camp is built with: its layout's garrison, plus {@code extra} from the spare spots (a harder threat,
	 * cycling through the age's troops), or minus that many (Easy), never the warlord or a tower post.
	 */
	public static List<CampTier.Guard> garrison(CampLayout layout, Age age, int extra) {
		List<CampTier.Guard> guards = new ArrayList<>(layout.guards());
		addArmy(layout, age, guards);
		if (extra < 0) {
			for (int i = guards.size() - 1; i >= 0 && extra < 0; i--) {
				CampTier.Guard guard = guards.get(i);
				if (!guard.onTower() && guard.type() != ModEntities.WARLORD) {
					guards.remove(i);
					extra++;
				}
			}
			return guards;
		}
		List<UnitType> troops = age.atLeast(Age.CASTLE)
				? List.of(UnitType.SPEARMAN, UnitType.CROSSBOWMAN,
						UnitType.KNIGHT)
				: age.atLeast(Age.FEUDAL)
						? List.of(UnitType.SPEARMAN, UnitType.CROSSBOWMAN,
								UnitType.MILITIA)
						: List.of(UnitType.MILITIA, UnitType.ARCHER);
		// Layouts without spare spots (the originals) double up on their ground guards' spots.
		List<BlockPos> spots = layout.spares().isEmpty()
				? layout.guards().stream().filter(g -> !g.onTower() && g.type() != ModEntities.WARLORD).map(CampTier.Guard::spot).toList()
				: layout.spares();
		for (int i = 0; i < extra && !spots.isEmpty(); i++) {
			BlockPos spot = spots.get(i % spots.size());
			guards.add(CampTier.guard(troops.get(i % troops.size()), spot.getX(), spot.getY(), spot.getZ()));
		}
		return guards;
	}

	/** Feudal and Castle camps hold an army, not a patrol: the garrison grows to this many (at most {@link #ARMY_CAP}). */
	private static final int ARMY_CAP = 40;

	private static void addArmy(CampLayout layout, Age age, List<CampTier.Guard> guards) {
		if (!age.atLeast(Age.FEUDAL)) {
			return;
		}
		boolean castle = age.atLeast(Age.CASTLE);
		int base = guards.size();
		// One soldier per ~36 (Castle) or ~50 (Feudal) square blocks of camp, so a 23-wide encampment isn't a crowd.
		int target = Math.max(base, Math.min(ARMY_CAP, layout.size() * layout.size() / (castle ? 36 : 50)));
		List<UnitType> troops = castle ? List.of(UnitType.SPEARMAN, UnitType.CROSSBOWMAN, UnitType.KNIGHT)
				: List.of(UnitType.SPEARMAN, UnitType.CROSSBOWMAN, UnitType.MILITIA);
		// A crypt's dead rise from its own tombs: no living soldiers among them.
		List<EntityType<? extends Mob>> undead = List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.HUSK, EntityType.SKELETON,
				EntityType.WITHER_SKELETON);
		boolean crypt = layout.id().contains("crypt");
		List<BlockPos> spots = layout.spares().isEmpty()
				? layout.guards().stream().filter(g -> !g.onTower() && g.type() != ModEntities.WARLORD).map(CampTier.Guard::spot).toList()
				: layout.spares();
		for (int i = 0; base + i < target && !spots.isEmpty(); i++) {
			BlockPos spot = spots.get(i % spots.size());
			guards.add(crypt ? CampTier.mob(undead.get(i % undead.size()), spot.getX(), spot.getY(), spot.getZ())
					: CampTier.guard(troops.get(i % troops.size()), spot.getX(), spot.getY(), spot.getZ()));
		}
	}

	private static final Identifier CRYPT_HEALTH = Warbands.id("crypt_health");
	private static final Identifier CRYPT_ARMOR = Warbands.id("crypt_armor");
	private static final Identifier CRYPT_SPEED = Warbands.id("crypt_speed");

	/**
	 * The crypt's dead are no easy targets for a bow: twice the health, bone-hard armor against arrows, and quick
	 * enough that a zombie can't be kited forever.
	 */
	private static void hardenUndead(Mob mob) {
		var health = mob.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.addOrReplacePermanentModifier(new AttributeModifier(CRYPT_HEALTH, 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			mob.setHealth(mob.getMaxHealth());
		}
		var armor = mob.getAttribute(Attributes.ARMOR);
		if (armor != null) {
			armor.addOrReplacePermanentModifier(new AttributeModifier(CRYPT_ARMOR, 8.0, AttributeModifier.Operation.ADD_VALUE));
		}
		var speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.addOrReplacePermanentModifier(new AttributeModifier(CRYPT_SPEED, 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	/** A free standing spot near {@code spot} (several guards share a post's area), or {@code spot} itself if there is none. */
	private static BlockPos spotNear(ServerLevel level, BlockPos spot, java.util.Set<BlockPos> used) {
		if (used.add(spot)) {
			return spot;
		}
		List<BlockPos> ring = new ArrayList<>();
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				ring.add(spot.offset(dx, 0, dz));
			}
		}
		java.util.Collections.shuffle(ring, new java.util.Random(spot.asLong() + used.size()));
		for (BlockPos pos : ring) {
			if (!used.contains(pos) && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
					&& level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
					&& level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
					&& level.getFluidState(pos).isEmpty()) {
				used.add(pos);
				return pos;
			}
		}
		return spot;
	}

	/**
	 * Sites are judged at one point, so a camp can be cut into a hillside, its gate opening onto a cliff. Clears
	 * natural terrain (only what {@link BlueprintPlacer#isClearable} allows, never a player's block) above the camp's
	 * floor in a ring around the walls and in a 5-wide lane out of the entrance, and gives the lane a floor.
	 */
	private static void clearApproach(ServerLevel level, BlockPos center, int half, int height, Direction entrance) {
		BlueprintPlacer.clearApproach(level, center, half, height, entrance, APPROACH_MARGIN, APPROACH_LANE);
	}

	/** A sunk camp (the crypt) would leave a pit once burnt: fill it back to the ground it was dug from. */
	private static void fillPit(ServerLevel level, BlockPos floor, int half, int sink) {
		for (int dy = 1; dy <= sink; dy++) {
			for (int dx = -half; dx <= half; dx++) {
				for (int dz = -half; dz <= half; dz++) {
					BlockPos pos = floor.offset(dx, dy, dz);
					if (level.getBlockState(pos).isAir()) {
						level.setBlock(pos, (dy == sink ? Blocks.COARSE_DIRT : Blocks.DIRT).defaultBlockState(), Block.UPDATE_ALL);
					}
				}
			}
		}
	}

	/** Each Castle kind's warlord is a named man, so the kill feed and the boss bar read like a bounty. */
	private static Component warlordName(CampTier tier) {
		String name = switch (tier) {
			case KEEP -> "Warlord Vorak";
			case CAVALRY_ENCAMPMENT -> "Khaness Arha";
			case SUNKEN_CRYPT -> "Grave-King Mos";
			case ILLAGER_HALL -> "Chief Brakk";
			default -> "Warlord";
		};
		return Component.literal(name).withStyle(ChatFormatting.RED);
	}

	/**
	 * Guards of this camp still alive. A guard leaves the list only when it dies ({@link #guardDied}), so one that is
	 * merely unloaded or in another dimension still counts.
	 */
	public static int guardsLeft(ServerLevel level, Camp camp) {
		int alive = 0;
		for (UUID id : camp.guards()) {
			Entity entity = level.getEntity(id);
			// Older camps also list guards that died before deaths were recorded, so a missing one can't be told from an unloaded one.
			if (entity == null ? camp.tracked() : entity.isAlive()) {
				alive++;
			}
		}
		return alive;
	}

	/** Death bookkeeping: takes the fallen guard off its camp's list, whatever dimension it died in. */
	public static void guardDied(LivingEntity entity) {
		if (!(entity.level() instanceof ServerLevel here)) {
			return;
		}
		UUID id = entity.getUUID();
		for (ServerLevel level : here.getServer().getAllLevels()) {
			for (Camp camp : List.copyOf(CampRegistry.all(level))) {
				if (camp.guards().contains(id)) {
					CampRegistry.replace(level, camp, camp.withoutGuard(id));
				}
			}
		}
	}

	/** Where a built camp's standard (its core) is. */
	public static BlockPos standardPos(Camp camp) {
		CampLayout layout = camp.layout();
		StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(camp.rotation())
				.setRotationPivot(new BlockPos(layout.half(), 0, layout.half()));
		return camp.center().offset(-layout.half(), 0, -layout.half())
				.offset(StructureTemplate.calculateRelativePosition(settings, layout.core()));
	}

	/** The camp whose standard is at {@code pos}. */
	public static @Nullable Camp atStandard(ServerLevel level, BlockPos pos) {
		for (Camp camp : CampRegistry.all(level)) {
			if (camp.built() && standardPos(camp).equals(pos)) {
				return camp;
			}
		}
		return null;
	}

	/** Right-click on a Camp Standard. */
	public static void claim(ServerLevel level, BlockPos pos, Player player) {
		Camp camp = atStandard(level, pos);
		if (camp == null || camp.conquered()) {
			player.sendOverlayMessage(Component.translatable("message.warbands.camp_taken"));
			return;
		}
		// The owner claims while they're on the server. If they're offline, someone who fought beside the banner
		// (within the party range, not a spectator) can claim for them. Anyone else is turned away.
		boolean forCommander = false;
		if (!camp.owner().equals(player.getUUID())) {
			boolean ownerHere = level.getServer().getPlayerList().getPlayer(camp.owner()) != null;
			boolean beside = !player.isSpectator() && player.blockPosition().closerThan(pos, Threat.PARTY_RANGE);
			if (ownerHere || !beside) {
				player.sendOverlayMessage(Component.translatable("message.warbands.camp_not_yours"));
				return;
			}
			forCommander = true;
		}
		int left = guardsLeft(level, camp);
		if (left > 0) {
			player.sendOverlayMessage(Component.translatable("message.warbands.camp_guards_left", left));
			return;
		}
		CampTier tier = camp.tier();
		CampRegistry.replace(level, camp, camp.asConquered(level.getGameTime() + BURN_TICKS));
		// The standard falls and leaves the tier's War Chest: released from the protected blocks, it survives the
		// burn-down on the dirt and is an ordinary chest afterwards (breakable, with drops). The entrance faces
		// rotation().rotate(SOUTH), so the chest faces the way in.
		StructureBlocks.release(level, pos);
		Spoils.chest(level, pos, camp.rotation().rotate(Direction.SOUTH), tier.lootTable(), "container.warbands.war_chest");
		ItemStack trophy = new ItemStack(tier.trophy());
		ItemStack relic = null;
		if (forCommander) {
			// The age trophy and the relic stay the commander's. Leave them on the banner; don't hand them to the ally.
			dropAt(level, pos, trophy, camp.owner());
			relic = Relics.award(level, camp, camp.owner());
			if (relic != null) {
				dropAt(level, pos, relic, camp.owner());
			}
		} else if (!player.getInventory().add(trophy)) {
			player.drop(trophy, false);
		}
		mapsLeadHome(player, camp);
		String rewardKey = forCommander ? "message.warbands.camp_summary.away"
				: tier == CampTier.KEEP ? "message.warbands.camp_summary.prize" : "message.warbands.camp_summary.age";
		player.sendSystemMessage(Component.translatable("message.warbands.camp_summary", tier.displayName(),
				Component.translatable(rewardKey)).withStyle(ChatFormatting.GREEN));
		level.playSound(null, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
		if (player instanceof ServerPlayer serverPlayer) {
			WarbandsAdvancements.award(serverPlayer, tier.advancement());
			for (ServerPlayer mate : level.players()) { // coop: whoever fought beside the claimer gets the credit too
				if (mate != serverPlayer && !mate.isSpectator() && mate.blockPosition().closerThan(pos, Threat.PARTY_RANGE)) {
					WarbandsAdvancements.award(mate, tier.advancement());
				}
			}
			if (!forCommander) {
				Relics.grant(level, camp, serverPlayer);
			}
		}
		payOut(level, camp.town());
		if (tier.age().atLeast(Age.FEUDAL) && Raids.raidsOn(level)) {
			Raids.retaliate(level, camp.town(), camp.owner());
			player.sendSystemMessage(Component.translatable(forCommander ? "message.warbands.camp_revenge_away" : "message.warbands.camp_revenge")
					.withStyle(ChatFormatting.RED));
		}
	}

	/**
	 * Drops {@code stack} on the banner for a commander who isn't here. Only they can pick it up, and it neither
	 * despawns nor burns with the camp, so the relic (already marked earned) can't be lost or taken by the ally.
	 */
	private static void dropAt(ServerLevel level, BlockPos pos, ItemStack stack, UUID owner) {
		ItemEntity drop = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
		drop.setTarget(owner);
		drop.setUnlimitedLifetime();
		drop.setInvulnerable(true);
		drop.addTag(REWARD_TAG);
		level.addFreshEntity(drop);
	}

	/** Pays conquered camps into the town that sold their maps, if it's loaded. Called on conquest and by the town. */
	public static void payOut(ServerLevel level, BlockPos town) {
		if (!level.isLoaded(town) || !(level.getBlockEntity(town) instanceof TownCenterBlockEntity center)) {
			return;
		}
		boolean paid = false;
		for (Camp camp : List.copyOf(CampRegistry.all(level))) {
			if (camp.conquered() && !camp.paid() && camp.town().equals(town)) {
				for (Resource resource : Resource.values()) {
					center.addStock(resource, camp.tier().plunder(resource));
				}
				CampRegistry.replace(level, camp, camp.asPaid());
				paid = true;
			}
		}
		if (paid) {
			CampRegistry.trim(level);
		}
	}

	/**
	 * The War Maps for {@code camp} in the player's inventory become maps home: the red X stays on the burnt camp, and
	 * holding one points back to the Town Center. Old-style War Map items (no picture) just crumble.
	 */
	private static boolean mapsLeadHome(Player player, Camp camp) {
		boolean kept = false;
		BlockPos target = new BlockPos(camp.center().getX(), 0, camp.center().getZ());
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			BlockPos mapCamp = mapTarget(stack);
			if (mapCamp != null && mapCamp.equals(target)) {
				CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
				tag.putLong(HOME_TAG, camp.town().asLong());
				stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
				stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.warbands.war_map.taken", camp.tier().displayName()));
				stack.set(DataComponents.LORE, new ItemLore(List.of(
						Component.translatable("tooltip.warbands.war_map.home").withStyle(ChatFormatting.GREEN),
						Component.translatable("tooltip.warbands.war_map.burnt").withStyle(ChatFormatting.DARK_GRAY),
						Component.translatable("tooltip.warbands.war_map.town").withStyle(ChatFormatting.BLUE))));
				kept = true;
			} else if (stack.is(ModItems.WAR_MAP)) {
				player.getInventory().setItem(i, ItemStack.EMPTY);
			}
		}
		return kept;
	}

	/** The Town Center a taken camp's War Map leads back to, or null. */
	public static @Nullable BlockPos mapHome(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || !stack.is(Items.FILLED_MAP)) {
			return null;
		}
		long packed = data.copyTag().getLongOr(HOME_TAG, Long.MIN_VALUE);
		return packed == Long.MIN_VALUE ? null : BlockPos.of(packed);
	}

	/** Whether the player holds a War Map (to a camp, or home from a taken one). */
	private static boolean holdsWarMap(Player player) {
		for (ItemStack stack : List.of(player.getMainHandItem(), player.getOffhandItem())) {
			if (mapTarget(stack) != null || mapHome(stack) != null) {
				return true;
			}
		}
		return false;
	}

	/** "Raider Fort · 58 blocks ↗" while a War Map is held; the arrow is relative to where the player looks. */
	public static Component hint(ServerLevel level, Player player) {
		for (ItemStack stack : List.of(player.getMainHandItem(), player.getOffhandItem())) {
			BlockPos home = mapHome(stack);
			if (home != null) {
				return Component.translatable("message.warbands.war_map_home_hint",
						Directions.distance(player.blockPosition(), home), Directions.arrow(player, home));
			}
			if (mapTarget(stack) != null) {
				break;
			}
		}
		return CampRegistry.active(level, player.getUUID())
				.map(camp -> (Component) Component.translatable("message.warbands.war_map_hint", camp.tier().displayName(),
						Directions.distance(player.blockPosition(), camp.center()), Directions.arrow(player, camp.center())))
				.orElse(Component.translatable("message.warbands.war_map_none"));
	}
}
