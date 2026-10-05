package dev.felipe.warbands.registry;

import dev.felipe.warbands.camp.TrophyItem;
import dev.felipe.warbands.camp.WarMapItem;
import dev.felipe.warbands.HandbookItem;
import dev.felipe.warbands.PlaytestGuideItem;
import dev.felipe.warbands.Warbands;
import dev.felipe.warbands.item.ArmoredWarhorseItem;
import dev.felipe.warbands.item.RelicItem;
import dev.felipe.warbands.blueprint.Blueprint;
import dev.felipe.warbands.blueprint.BlueprintItem;
import dev.felipe.warbands.item.WarHornItem;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;

public final class ModItems {
	public static final Item HANDBOOK = register("handbook", HandbookItem::new, new Item.Properties().stacksTo(1));
	public static final Item WAR_HORN = register("war_horn", WarHornItem::new, new Item.Properties().stacksTo(1));
	public static final Item TOWN_CENTER_BLUEPRINT = register("town_center_blueprint",
			props -> new BlueprintItem(Blueprint.TOWN_CENTER, props), new Item.Properties().stacksTo(16).rarity(Rarity.RARE));
	public static final Item HOUSE_BLUEPRINT = register("house_blueprint",
			props -> new BlueprintItem(Blueprint.HOUSE, props), new Item.Properties().stacksTo(16));
	public static final Item BARRACKS_BLUEPRINT = register("barracks_blueprint",
			props -> new BlueprintItem(Blueprint.BARRACKS, props), new Item.Properties().stacksTo(16));
	public static final Item ARCHERY_RANGE_BLUEPRINT = register("archery_range_blueprint",
			props -> new BlueprintItem(Blueprint.ARCHERY_RANGE, props), new Item.Properties().stacksTo(16));
	public static final Item BLACKSMITH_BLUEPRINT = register("blacksmith_blueprint",
			props -> new BlueprintItem(Blueprint.BLACKSMITH, props), new Item.Properties().stacksTo(16));
	public static final Item STABLE_BLUEPRINT = register("stable_blueprint",
			props -> new BlueprintItem(Blueprint.STABLE, props), new Item.Properties().stacksTo(16));
	public static final Item WATCH_TOWER_BLUEPRINT = register("watch_tower_blueprint",
			props -> new BlueprintItem(Blueprint.WATCH_TOWER, props), new Item.Properties().stacksTo(16));
	public static final Item SIEGE_WORKSHOP_BLUEPRINT = register("siege_workshop_blueprint",
			props -> new BlueprintItem(Blueprint.SIEGE_WORKSHOP, props), new Item.Properties().stacksTo(16));
	public static final Item MILITIA_SPAWN_EGG = spawnEgg("militia_spawn_egg", ModEntities.MILITIA);
	public static final Item ARCHER_SPAWN_EGG = spawnEgg("archer_spawn_egg", ModEntities.ARCHER);
	public static final Item SPEARMAN_SPAWN_EGG = spawnEgg("spearman_spawn_egg", ModEntities.SPEARMAN);
	public static final Item CROSSBOWMAN_SPAWN_EGG = spawnEgg("crossbowman_spawn_egg", ModEntities.CROSSBOWMAN);
	public static final Item KNIGHT_SPAWN_EGG = spawnEgg("knight_spawn_egg", ModEntities.KNIGHT);
	public static final Item RAM_SPAWN_EGG = spawnEgg("ram_spawn_egg", ModEntities.RAM);
	/** Bought at the Town Center (no recipe): shows the way to your enemy camp while held. */
	public static final Item WAR_MAP = register("war_map", WarMapItem::new, new Item.Properties().stacksTo(1));
	/** Won by claiming a camp's standard (no recipe). Bandit and Fort trophies unlock the next age. */
	public static final Item BANDIT_TROPHY = register("bandit_trophy", props -> new TrophyItem("feudal", props),
			new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
	public static final Item FORT_TROPHY = register("fort_trophy", props -> new TrophyItem("castle", props),
			new Item.Properties().stacksTo(16).rarity(Rarity.RARE));
	public static final Item KEEP_TROPHY = register("keep_trophy", props -> new TrophyItem("prize", props),
			new Item.Properties().stacksTo(16).rarity(Rarity.EPIC));
	/** Icon only (training menu, advancement, guide): no recipe, not in a creative tab. */
	public static final Item BATTERING_RAM = register("battering_ram", Item::new, new Item.Properties().stacksTo(1));

	/** The Witch Coven's relic: the town's soldiers knit their wounds. */
	public static final Item HAG_CHARM = register("hag_charm", RelicItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
	/** The Frozen Outpost's relic: raids on the town call out earlier. */
	public static final Item FROST_SIGIL = register("frost_sigil", RelicItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
	/** The Sunken Crypt's relic: the crypt's dead sap the town's foes. */
	public static final Item GRAVE_LANTERN = register("grave_lantern", RelicItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
	/** The Illager Hall's relic: the town's horses run faster. */
	public static final Item OMEN_BANNER = register("omen_banner", RelicItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

	/** The Relic Pedestal, crafted and placed inside the town's reach. */
	public static final Item RELIC_PEDESTAL = register("relic_pedestal",
			properties -> new BlockItem(ModBlocks.RELIC_PEDESTAL, properties), new Item.Properties().useBlockDescriptionPrefix());

	/** Decoration props seen in the camps, craftable for a town's buildings. */
	public static final Item WEAPON_RACK = register("weapon_rack",
			properties -> new BlockItem(ModBlocks.WEAPON_RACK, properties), new Item.Properties().useBlockDescriptionPrefix());
	public static final Item HIDE_RACK = register("hide_rack",
			properties -> new BlockItem(ModBlocks.HIDE_RACK, properties), new Item.Properties().useBlockDescriptionPrefix());
	public static final Item SUPPLY_CRATE = register("supply_crate",
			properties -> new BlockItem(ModBlocks.SUPPLY_CRATE, properties), new Item.Properties().useBlockDescriptionPrefix());

	/** The Cavalry Encampment's rare prize: calls a barded warhorse tamed to the caller. */
	public static final Item WARHORSE_WHISTLE = register("warhorse_whistle", ArmoredWarhorseItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

	/** The tester's button panel ({@code /warbands playtest}). Not in a creative tab: it needs cheats anyway. */
	public static final Item PLAYTEST_GUIDE = register("playtest_guide", PlaytestGuideItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

	private ModItems() {
	}

	private static Item spawnEgg(String name, EntityType<?> type) {
		return register(name, SpawnEggItem::new, new Item.Properties().spawnEgg(type));
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Warbands.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	public static void initialize() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Warbands.id("warbands"),
				CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
						.title(Component.translatable("itemGroup.warbands"))
						.icon(() -> new ItemStack(WAR_HORN))
						.displayItems((parameters, output) -> {
							output.accept(HANDBOOK);
							output.accept(WAR_HORN);
							output.accept(TOWN_CENTER_BLUEPRINT);
							output.accept(HOUSE_BLUEPRINT);
							output.accept(BARRACKS_BLUEPRINT);
							output.accept(ARCHERY_RANGE_BLUEPRINT);
							output.accept(BLACKSMITH_BLUEPRINT);
							output.accept(STABLE_BLUEPRINT);
							output.accept(WATCH_TOWER_BLUEPRINT);
							output.accept(SIEGE_WORKSHOP_BLUEPRINT);
							output.accept(WAR_MAP);
							output.accept(BANDIT_TROPHY);
							output.accept(FORT_TROPHY);
							output.accept(KEEP_TROPHY);
							output.accept(HAG_CHARM);
							output.accept(FROST_SIGIL);
							output.accept(GRAVE_LANTERN);
							output.accept(OMEN_BANNER);
							output.accept(RELIC_PEDESTAL);
							output.accept(WEAPON_RACK);
							output.accept(HIDE_RACK);
							output.accept(SUPPLY_CRATE);
							output.accept(WARHORSE_WHISTLE);
							output.accept(MILITIA_SPAWN_EGG);
							output.accept(ARCHER_SPAWN_EGG);
							output.accept(SPEARMAN_SPAWN_EGG);
							output.accept(CROSSBOWMAN_SPAWN_EGG);
							output.accept(KNIGHT_SPAWN_EGG);
							output.accept(RAM_SPAWN_EGG);
						}).build());
	}
}
