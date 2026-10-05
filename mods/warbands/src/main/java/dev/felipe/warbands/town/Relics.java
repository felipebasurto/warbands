package dev.felipe.warbands.town;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.felipe.warbands.WarbandsAdvancements;
import dev.felipe.warbands.camp.CampRegistry.Camp;
import dev.felipe.warbands.camp.CampTier;
import dev.felipe.warbands.entity.SoldierEntity;
import dev.felipe.warbands.registry.ModAttachments;
import dev.felipe.warbands.registry.ModItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The rare prizes of the fantasy camps. The first conquest of each fantasy kind hands the owner its relic; enshrined on
 * a Relic Pedestal inside a town it gives that town one small effect. A town can't enshrine the same kind twice, so the
 * collection is "4 of 4" and relics don't stack.
 */
public final class Relics {
	/** How often the town-wide relic effects refresh their short-lived effects. */
	public static final int EFFECT_TICKS = 80;
	/** A relic reaches this far from its Town Center. */
	private static final int RANGE = 48;
	/** The pedestal works inside a town's reach. */
	public static final int TOWN_RANGE = 64;

	/** The relic each fantasy kind gives on its first conquest. */
	private static final Map<CampTier, Item> RELICS = Map.of(
			CampTier.WITCH_COVEN, ModItems.HAG_CHARM,
			CampTier.FROZEN_OUTPOST, ModItems.FROST_SIGIL,
			CampTier.SUNKEN_CRYPT, ModItems.GRAVE_LANTERN,
			CampTier.ILLAGER_HALL, ModItems.OMEN_BANNER);

	/** The kinds enshrined in one town, keyed by its Town Center position. */
	public record TownRelics(BlockPos town, List<CampTier> kinds) {
		public static final Codec<TownRelics> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("town").forGetter(TownRelics::town),
				KINDS.fieldOf("kinds").forGetter(TownRelics::kinds)).apply(i, TownRelics::new));
	}

	/**
	 * {@code enshrined}: the relics on each town's pedestals. {@code earned}: kinds each owner has already
	 * been handed, so a second Witch Coven doesn't drop a second charm.
	 */
	public record Data(List<TownRelics> enshrined, Map<UUID, List<CampTier>> earned) {
		public static final Data EMPTY = new Data(List.of(), Map.of());
		public static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
				TownRelics.CODEC.listOf().optionalFieldOf("enshrined", List.of()).forGetter(Data::enshrined),
				Codec.unboundedMap(UUIDUtil.STRING_CODEC, KINDS).optionalFieldOf("earned", Map.of()).forGetter(Data::earned))
				.apply(i, Data::new));
	}

	private static final Codec<CampTier> KIND = Codec.STRING.xmap(
			name -> {
				CampTier tier = CampTier.byName(name);
				return tier == null ? CampTier.BANDIT_CAMP : tier;
			}, CampTier::serializedName);
	private static final Codec<List<CampTier>> KINDS = KIND.listOf();

	public static List<CampTier> enshrined(ServerLevel level, BlockPos town) {
		for (TownRelics t : data(level).enshrined()) {
			if (t.town().equals(town)) {
				return t.kinds();
			}
		}
		return List.of();
	}

	public static boolean has(ServerLevel level, BlockPos town, CampTier kind) {
		return enshrined(level, town).contains(kind);
	}

	/** How many relics a full collection has. */
	public static int total() {
		return RELICS.size();
	}

	/** The relic for this kind, or null when the camp isn't a fantasy kind. */
	public static @Nullable Item relicOf(CampTier kind) {
		return RELICS.get(kind);
	}

	/** The fantasy kind a relic item belongs to, or null for anything else. */
	public static @Nullable CampTier kindOf(Item item) {
		for (Map.Entry<CampTier, Item> entry : RELICS.entrySet()) {
			if (entry.getValue() == item) {
				return entry.getKey();
			}
		}
		return null;
	}

	/** Whether {@code owner} has already been handed this kind's relic. */
	public static boolean earned(ServerLevel level, UUID owner, CampTier kind) {
		return data(level).earned().getOrDefault(owner, List.of()).contains(kind);
	}

	/**
	 * The relic {@code owner} earns for taking {@code camp}, and marks it earned so a second camp of the kind
	 * gives nothing. Null when the kind has no relic or this owner already has it. The caller places the item.
	 */
	public static @Nullable ItemStack award(ServerLevel level, Camp camp, UUID owner) {
		Item relic = RELICS.get(camp.tier());
		if (relic == null || earned(level, owner, camp.tier())) {
			return null;
		}
		markEarned(level, data(level), owner, camp.tier());
		return new ItemStack(relic);
	}

	/** Hands the camp's relic on the owner's first conquest of its kind. Called from {@code Camps.claim}. */
	public static void grant(ServerLevel level, Camp camp, ServerPlayer player) {
		ItemStack stack = award(level, camp, player.getUUID());
		if (stack != null && !player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}

	/** Enshrines a relic on the town's collection. {@code false} when that kind already rests there. */
	public static boolean enshrine(ServerLevel level, BlockPos town, CampTier kind) {
		Data data = data(level);
		List<CampTier> kinds = enshrined(level, town);
		if (kinds.contains(kind)) {
			return false;
		}
		List<TownRelics> enshrined = new ArrayList<>(data.enshrined());
		enshrined.removeIf(t -> t.town().equals(town));
		List<CampTier> copy = new ArrayList<>(kinds);
		copy.add(kind);
		enshrined.add(new TownRelics(town.immutable(), List.copyOf(copy)));
		put(level, new Data(List.copyOf(enshrined), data.earned()));
		if (copy.size() == RELICS.size()) {
			ServerPlayer owner = ownerOf(level, town);
			if (owner != null) {
				WarbandsAdvancements.award(owner, WarbandsAdvancements.RELIC_COLLECTION);
			}
		}
		return true;
	}

	private static @Nullable ServerPlayer ownerOf(ServerLevel level, BlockPos town) {
		for (TownRegistry.Town t : TownRegistry.towns(level)) {
			if (t.pos().equals(town)) {
				return level.getServer().getPlayerList().getPlayer(t.owner());
			}
		}
		return null;
	}

	private static Data data(ServerLevel level) {
		return level.getAttachedOrCreate(ModAttachments.RELICS);
	}

	private static void put(ServerLevel level, Data data) {
		level.setAttached(ModAttachments.RELICS, data);
	}

	private static void markEarned(ServerLevel level, Data data, UUID owner, CampTier kind) {
		Map<UUID, List<CampTier>> earned = new HashMap<>(data.earned());
		List<CampTier> copy = new ArrayList<>(earned.getOrDefault(owner, List.of()));
		copy.add(kind);
		earned.put(owner, List.copyOf(copy));
		put(level, new Data(data.enshrined(), Map.copyOf(earned)));
	}

	/** Town-wide effects, refreshed every {@link #EFFECT_TICKS}. Called once per level tick. */
	public static void tick(ServerLevel level) {
		if (level.getGameTime() % EFFECT_TICKS != 0) {
			return;
		}
		Data data = data(level);
		if (data.enshrined().isEmpty()) {
			return;
		}
		for (TownRelics t : data.enshrined()) {
			tickTown(level, t.town(), t.kinds());
		}
	}

	private static void tickTown(ServerLevel level, BlockPos town, List<CampTier> kinds) {
		if (!level.isLoaded(town)) {
			return;
		}
		TownRegistry.Town t = townOf(level, town);
		if (t == null) {
			return;
		}
		AABB box = new AABB(town).inflate(RANGE);
		if (kinds.contains(CampTier.WITCH_COVEN)) {
			// The Hag's Charm: the town's soldiers knit their wounds.
			for (UUID id : ArmyRegistry.ids(level.getServer(), t.owner())) {
				Entity entity = level.getEntity(id);
				if (entity instanceof net.minecraft.world.entity.LivingEntity living && box.contains(living.position())) {
					living.addEffect(
							new MobEffectInstance(MobEffects.REGENERATION, EFFECT_TICKS + 40, 0, false, false, true));
				}
			}
		}
		if (kinds.contains(CampTier.SUNKEN_CRYPT)) {
			// The Grave Lantern: the crypt's dead sap every hostile thing near the town.
			for (Monster monster : level.getEntitiesOfClass(Monster.class, box)) {
				monster.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EFFECT_TICKS + 40, 0, false, false, true));
				monster.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, EFFECT_TICKS + 40, 0, false, false, true));
			}
			for (SoldierEntity soldier : level.getEntitiesOfClass(SoldierEntity.class, box)) {
				if (SoldierEntity.isRaiderSide(soldier)) {
					soldier.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EFFECT_TICKS + 40, 0, false, false, true));
				}
			}
		}
		if (kinds.contains(CampTier.ILLAGER_HALL)) {
			// The Omen Banner: the town's horses run faster.
			for (AbstractHorse horse : level.getEntitiesOfClass(AbstractHorse.class, box)) {
				EntityReference<?> owner = horse.getOwnerReference();
				if (owner != null && t.owner().equals(owner.getUUID())) {
					horse.addEffect(new MobEffectInstance(MobEffects.SPEED, EFFECT_TICKS + 40, 0, false, false, true));
				}
			}
		}
		// The Frost Sigil works in Raids.warnTicks: it is read when a raid is called, not here.
	}

	private static TownRegistry.@Nullable Town townOf(ServerLevel level, BlockPos pos) {
		for (TownRegistry.Town t : TownRegistry.towns(level)) {
			if (t.pos().equals(pos)) {
				return t;
			}
		}
		return null;
	}

	private Relics() {
	}
}
