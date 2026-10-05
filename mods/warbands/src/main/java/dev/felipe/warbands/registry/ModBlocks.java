package dev.felipe.warbands.registry;

import dev.felipe.warbands.Warbands;
import dev.felipe.warbands.camp.AlarmHornBlock;
import dev.felipe.warbands.camp.AlarmPlateBlock;
import dev.felipe.warbands.camp.CaltropsBlock;
import dev.felipe.warbands.camp.CampStandardBlock;
import dev.felipe.warbands.camp.PowderKegBlock;
import dev.felipe.warbands.camp.WarRelayBlock;
import dev.felipe.warbands.town.BlacksmithBlock;
import dev.felipe.warbands.town.WatchTowerBlock;
import dev.felipe.warbands.town.HearthBlock;
import dev.felipe.warbands.town.TownCenterBlock;
import dev.felipe.warbands.town.RelicPedestalBlock;
import dev.felipe.warbands.town.TrainingBlock;
import dev.felipe.warbands.town.TrainingSite;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** Structure core blocks. They have no items: they only come from Blueprints. */
public final class ModBlocks {
	public static final Block TOWN_CENTER = register("town_center", TownCenterBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.WOOD)
					.strength(5.0F, 1200.0F)
					.sound(SoundType.WOOD)
					.pushReaction(PushReaction.BLOCK));

	public static final Block HEARTH = register("hearth", HearthBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.STONE)
					.strength(3.0F, 6.0F)
					.sound(SoundType.STONE)
					.lightLevel(state -> 13)
					.pushReaction(PushReaction.BLOCK));

	public static final Block BARRACKS = register("barracks", properties -> new TrainingBlock(TrainingSite.BARRACKS, properties),
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.WOOD)
					.strength(5.0F, 1200.0F)
					.sound(SoundType.WOOD)
					.pushReaction(PushReaction.BLOCK));

	public static final Block ARCHERY_RANGE = register("archery_range", properties -> new TrainingBlock(TrainingSite.ARCHERY_RANGE, properties),
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.WOOD)
					.strength(5.0F, 1200.0F)
					.sound(SoundType.WOOD)
					.pushReaction(PushReaction.BLOCK));

	public static final Block STABLE = register("stable", properties -> new TrainingBlock(TrainingSite.STABLE, properties),
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.WOOD)
					.strength(5.0F, 1200.0F)
					.sound(SoundType.WOOD)
					.pushReaction(PushReaction.BLOCK));

	public static final Block SIEGE_WORKSHOP = register("siege_workshop", properties -> new TrainingBlock(TrainingSite.SIEGE_WORKSHOP, properties),
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.WOOD)
					.strength(5.0F, 1200.0F)
					.sound(SoundType.WOOD)
					.pushReaction(PushReaction.BLOCK));

	public static final Block WATCH_TOWER = register("watch_tower", WatchTowerBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.STONE)
					.strength(5.0F, 1200.0F)
					.sound(SoundType.STONE)
					.pushReaction(PushReaction.BLOCK));

	public static final Block BLACKSMITH = register("blacksmith", BlacksmithBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.STONE)
					.strength(5.0F, 1200.0F)
					.sound(SoundType.STONE)
					.pushReaction(PushReaction.BLOCK));

	/** An enemy camp's core. Unbreakable: conquer the camp, then right-click it to claim the plunder. */
	public static final Block CAMP_STANDARD = register("camp_standard", CampStandardBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_BLACK)
					.strength(-1.0F, 3_600_000.0F)
					.sound(SoundType.WOOD)
					.noLootTable()
					.pushReaction(PushReaction.BLOCK));

	/** The Keep's gate: as hard as obsidian to mine, but a Battering Ram on Charge breaks it. */
	public static final Block REINFORCED_GATE = register("reinforced_gate", Block::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.WOOD)
					.strength(50.0F, 1200.0F)
					.sound(SoundType.WOOD)
					.noLootTable()
					.pushReaction(PushReaction.BLOCK));

	/** Camp trap: step on it and the camp's bell rings and the guards sally out. */
	public static final Block ALARM_PLATE = register("alarm_plate", AlarmPlateBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.GOLD)
					.strength(0.5F)
					.sound(SoundType.METAL)
					.noLootTable()
					.pushReaction(PushReaction.DESTROY));

	/** Camp trap: scattered spikes that slow and hurt whoever walks on them while the camp stands. */
	public static final Block CALTROPS = register("caltrops", CaltropsBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_BROWN)
					.strength(0.2F)
					.sound(SoundType.METAL)
					.noLootTable()
					.pushReaction(PushReaction.DESTROY));

	/** The town's reliquary: enshrine fantasy-camp relics on it for small town-wide effects. */
	public static final Block RELIC_PEDESTAL = register("relic_pedestal", RelicPedestalBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_BLACK)
					.strength(2.0F, 6.0F)
					.sound(SoundType.STONE)
					.noOcclusion()
					.lightLevel(RelicPedestalBlock::light)
					.pushReaction(PushReaction.BLOCK));

	/** Camp trap: Castle powder keg — trip it and it hurts everything close but breaks no blocks. */
	public static final Block POWDER_KEG = register("powder_keg", PowderKegBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_BROWN)
					.strength(0.5F)
					.sound(SoundType.WOOD)
					.noLootTable()
					.pushReaction(PushReaction.DESTROY));

	/**
	 * A camp's redstone brain: powered while the camp is alarmed, so its piston portcullises, rigged floors and
	 * dispenser galleries run on vanilla redstone. Quick to smash once you reach it: every trap wired to it dies.
	 */
	public static final Block WAR_RELAY = register("war_relay", WarRelayBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_RED)
					.strength(1.5F, 6.0F)
					.sound(SoundType.COPPER)
					.noLootTable()
					.lightLevel(state -> state.getValue(WarRelayBlock.POWERED) ? 9 : 2)
					.pushReaction(PushReaction.BLOCK));

	/** Camp trap: a redstone signal (a sculk sensor that heard you) sounds the camp alarm. */
	public static final Block ALARM_HORN = register("alarm_horn", AlarmHornBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.GOLD)
					.strength(1.5F, 6.0F)
					.sound(SoundType.METAL)
					.noLootTable()
					.pushReaction(PushReaction.BLOCK));

	/**
	 * Camp gate: iron grating that sticky pistons drop across a camp's inner gate when the alarm sounds. The one camp
	 * block pistons may move; it drops nothing, and a Battering Ram smashes it.
	 */
	public static final Block PORTCULLIS = register("portcullis", Block::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.METAL)
					.strength(25.0F, 1200.0F)
					.sound(SoundType.CHAIN)
					.noOcclusion()
					.noLootTable());

	/** Camp trap: planks on a sticky piston that pulls them away when a pressure plate is stepped on. */
	public static final Block RIGGED_FLOOR = register("rigged_floor", Block::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.WOOD)
					.strength(2.0F, 3.0F)
					.sound(SoundType.WOOD)
					.noLootTable());

	/** Camp and town decoration: a rack of blades on a dark frame. */
	public static final Block WEAPON_RACK = register("weapon_rack", dev.felipe.warbands.prop.RackBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.WOOD)
					.strength(1.5F)
					.sound(SoundType.WOOD)
					.noOcclusion()
					.pushReaction(PushReaction.DESTROY));

	/** Camp and town decoration: a pelt stretched in a frame. */
	public static final Block HIDE_RACK = register("hide_rack", dev.felipe.warbands.prop.RackBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.WOOD)
					.strength(1.5F)
					.sound(SoundType.WOOD)
					.noOcclusion()
					.pushReaction(PushReaction.DESTROY));

	/** Camp and town decoration: a banded crate (holds nothing, so it can't be used to store or duplicate loot). */
	public static final Block SUPPLY_CRATE = register("supply_crate", Block::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.WOOD)
					.strength(2.0F)
					.sound(SoundType.WOOD));

	private ModBlocks() {
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Warbands.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	public static void initialize() {
	}
}
