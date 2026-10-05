package dev.felipe.warbands.camp;

import dev.felipe.warbands.Warbands;
import dev.felipe.warbands.WarbandsAdvancements;
import dev.felipe.warbands.registry.ModEntities;
import dev.felipe.warbands.registry.ModItems;
import dev.felipe.warbands.town.Age;
import dev.felipe.warbands.town.Resource;
import dev.felipe.warbands.town.UnitType;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

/**
 * The enemy camps, several kinds per age, each with a few hand-made layouts ({@link CampLayout}: size, shape, core,
 * guard spots, generated with the structures by {@code tools/gen_camps.py}). Costs and plunder are the
 * same within an age, so a War Map's price doesn't depend on the draw. Each kind prefers some biomes (forest,
 * beach, swamp...): the site search tries those first and falls back to the age's plain kind on any dry site.
 */
public enum CampTier {
	BANDIT_CAMP("bandit_camp", Age.VILLAGE, WarbandsAdvancements.CAMP_BANDIT, Home.ANYWHERE),
	POACHERS_LODGE("poachers_lodge", Age.VILLAGE, WarbandsAdvancements.CAMP_POACHERS_LODGE,
			Home.ofTags(BiomeTags.IS_FOREST, BiomeTags.IS_TAIGA)),
	SMUGGLERS_DOCK("smugglers_dock", Age.VILLAGE, WarbandsAdvancements.CAMP_SMUGGLERS_DOCK, Home.ofTags(BiomeTags.IS_BEACH)),

	FORT("fort", Age.FEUDAL, WarbandsAdvancements.CAMP_FORT, Home.ANYWHERE),
	QUARRY_STRONGHOLD("quarry_stronghold", Age.FEUDAL, WarbandsAdvancements.CAMP_QUARRY_STRONGHOLD,
			Home.ofTags(BiomeTags.IS_HILL, BiomeTags.IS_MOUNTAIN)),
	WITCH_COVEN("witch_coven", Age.FEUDAL, WarbandsAdvancements.CAMP_WITCH_COVEN, Home.ofTags(BiomeTags.HAS_SWAMP_HUT)),
	FROZEN_OUTPOST("frozen_outpost", Age.FEUDAL, WarbandsAdvancements.CAMP_FROZEN_OUTPOST, Home.ofTags(BiomeTags.HAS_IGLOO)),

	KEEP("keep", Age.CASTLE, WarbandsAdvancements.CAMP_KEEP, Home.ANYWHERE),
	CAVALRY_ENCAMPMENT("cavalry_encampment", Age.CASTLE, WarbandsAdvancements.CAMP_CAVALRY_ENCAMPMENT,
			Home.ofTags(BiomeTags.HAS_VILLAGE_PLAINS, BiomeTags.IS_SAVANNA)),
	SUNKEN_CRYPT("sunken_crypt", Age.CASTLE, WarbandsAdvancements.CAMP_SUNKEN_CRYPT, Home.ANYWHERE),
	ILLAGER_HALL("illager_hall", Age.CASTLE, WarbandsAdvancements.CAMP_ILLAGER_HALL,
			Home.ofBiomes(Biomes.DARK_FOREST, Biomes.PALE_GARDEN));

	/** Map price and plunder per age (food, wood, stone, gold): same for every kind, so the button price is honest. */
	private static final class Prices {
		private static final int[] VILLAGE_COST = {100, 0, 0, 50};
		private static final int[] VILLAGE_PLUNDER = {150, 100, 0, 80};
		private static final int[] FEUDAL_COST = {200, 100, 0, 100};
		private static final int[] FEUDAL_PLUNDER = {400, 200, 150, 250};
		private static final int[] CASTLE_COST = {400, 200, 0, 300};
		private static final int[] CASTLE_PLUNDER = {800, 400, 400, 600};
	}

	/** A guard's spot: a soldier unit or a vanilla mob, standing or walled onto a tower top. */
	public record Guard(EntityType<? extends Mob> type, BlockPos spot, boolean onTower) {
	}

	/** The biomes a kind looks for first: a tag set or a list of biomes; {@code ANYWHERE} builds on any dry site. */
	private record Home(List<TagKey<Biome>> tags, List<ResourceKey<Biome>> keys) {
		static final Home ANYWHERE = new Home(List.of(), List.of());

		@SafeVarargs
		static Home ofTags(TagKey<Biome>... tags) {
			return new Home(List.of(tags), List.of());
		}

		@SafeVarargs
		static Home ofBiomes(ResourceKey<Biome>... keys) {
			return new Home(List.of(), List.of(keys));
		}

		boolean matches(Holder<Biome> biome) {
			for (TagKey<Biome> tag : tags) {
				if (biome.is(tag)) {
					return true;
				}
			}
			for (ResourceKey<Biome> key : keys) {
				if (biome.is(key)) {
					return true;
				}
			}
			return false;
		}
	}

	private final String name;
	private final Age age;
	private final int[] cost;
	private final int[] plunder;
	private final String advancement;
	private final Home home;

	CampTier(String name, Age age, String advancement, Home home) {
		this.name = name;
		this.age = age;
		this.cost = switch (age) {
			case VILLAGE -> Prices.VILLAGE_COST;
			case FEUDAL -> Prices.FEUDAL_COST;
			case CASTLE -> Prices.CASTLE_COST;
		};
		this.plunder = switch (age) {
			case VILLAGE -> Prices.VILLAGE_PLUNDER;
			case FEUDAL -> Prices.FEUDAL_PLUNDER;
			case CASTLE -> Prices.CASTLE_PLUNDER;
		};
		this.advancement = advancement;
		this.home = home;
	}

	static Guard guard(UnitType unit, int x, int y, int z) {
		return new Guard(unit.entityType(), new BlockPos(x, y, z), false);
	}

	static Guard tower(UnitType unit, int x, int y, int z) {
		return new Guard(unit.entityType(), new BlockPos(x, y, z), true);
	}

	static Guard mob(EntityType<? extends Mob> type, int x, int y, int z) {
		return new Guard(type, new BlockPos(x, y, z), false);
	}

	/** The Castle kinds' boss: he counts as a guard, so the standard can't be claimed while he stands. */
	static Guard warlord(int x, int z) {
		return mob(ModEntities.WARLORD, x, 1, z);
	}

	/** The age's plain kind: the fallback when no biome-matched site comes up, and the price shown on the button. */
	public static CampTier forAge(Age age) {
		return age.atLeast(Age.CASTLE) ? KEEP : age.atLeast(Age.FEUDAL) ? FORT : BANDIT_CAMP;
	}

	/** The kinds a town of this age can send you to (the War Map draws from these). */
	public static List<CampTier> kinds(Age age) {
		if (age.atLeast(Age.CASTLE)) {
			return List.of(KEEP, CAVALRY_ENCAMPMENT, SUNKEN_CRYPT, ILLAGER_HALL);
		}
		if (age.atLeast(Age.FEUDAL)) {
			return List.of(FORT, QUARRY_STRONGHOLD, WITCH_COVEN, FROZEN_OUTPOST);
		}
		return List.of(BANDIT_CAMP, POACHERS_LODGE, SMUGGLERS_DOCK);
	}

	public static @Nullable CampTier byName(String name) {
		for (CampTier tier : values()) {
			if (tier.name.equals(name)) {
				return tier;
			}
		}
		return null;
	}

	public String serializedName() {
		return name;
	}

	public Component displayName() {
		return Component.translatable("camp.warbands." + name);
	}

	/** The War Chest's loot table ({@code data/warbands/loot_table/chests/camp/<kind>.json}). */
	public ResourceKey<LootTable> lootTable() {
		return ResourceKey.create(Registries.LOOT_TABLE, Warbands.id("chests/camp/" + name));
	}

	/** The stash chests' loot table ({@code data/warbands/loot_table/chests/cache/<kind>.json}, made by {@code tools/gen_loot.py}). */
	public ResourceKey<LootTable> cacheTable() {
		return ResourceKey.create(Registries.LOOT_TABLE, Warbands.id("chests/cache/" + name));
	}

	/** Whether this kind prefers the biome at a site (false only when it has preferences and none match). */
	public boolean prefers(Holder<Biome> biome) {
		return home.matches(biome);
	}

	public boolean hasBiomeHome() {
		return home != Home.ANYWHERE;
	}

	/** This kind's hand-made layouts (at least one; the first is the original design, what old saves fall back to). */
	public List<CampLayout> layouts() {
		return CampLayouts.of(this);
	}

	/**
	 * The layouts a War Map can pick. From the Feudal Age on the small original design is left out (the bigger variants
	 * are the ones worth an army), but it stays loadable for old saves and tests.
	 */
	public List<CampLayout> drawable() {
		List<CampLayout> all = layouts();
		return age.atLeast(Age.FEUDAL) && all.size() > 1 ? all.subList(1, all.size()) : all;
	}

	/** The layout with this id, or the first one (old saves, a layout since removed). */
	public CampLayout layout(String id) {
		for (CampLayout layout : layouts()) {
			if (layout.id().equals(id)) {
				return layout;
			}
		}
		return layouts().get(0);
	}

	/** What claiming this camp's standard gives (the age's trophy, whatever the kind). */
	public Item trophy() {
		return switch (age) {
			case VILLAGE -> ModItems.BANDIT_TROPHY;
			case FEUDAL -> ModItems.FORT_TROPHY;
			case CASTLE -> ModItems.KEEP_TROPHY;
		};
	}

	/** Guards fight with this age's bonuses. */
	public Age age() {
		return age;
	}

	public int cost(Resource resource) {
		return cost[resource.ordinal()];
	}

	public int plunder(Resource resource) {
		return plunder[resource.ordinal()];
	}

	public String advancement() {
		return advancement;
	}

}
