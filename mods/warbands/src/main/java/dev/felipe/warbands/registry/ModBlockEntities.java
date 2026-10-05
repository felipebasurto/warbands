package dev.felipe.warbands.registry;

import dev.felipe.warbands.Warbands;
import dev.felipe.warbands.town.BlacksmithBlockEntity;
import dev.felipe.warbands.town.WatchTowerBlockEntity;
import dev.felipe.warbands.town.TownCenterBlockEntity;
import dev.felipe.warbands.town.TrainingBlockEntity;
import dev.felipe.warbands.town.TrainingSite;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
	public static final BlockEntityType<TownCenterBlockEntity> TOWN_CENTER = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, Warbands.id("town_center"),
			FabricBlockEntityTypeBuilder.create(TownCenterBlockEntity::new, ModBlocks.TOWN_CENTER).build());

	public static final BlockEntityType<TrainingBlockEntity> BARRACKS = training("barracks", TrainingSite.BARRACKS);
	public static final BlockEntityType<TrainingBlockEntity> ARCHERY_RANGE = training("archery_range", TrainingSite.ARCHERY_RANGE);
	public static final BlockEntityType<TrainingBlockEntity> STABLE = training("stable", TrainingSite.STABLE);
	public static final BlockEntityType<TrainingBlockEntity> SIEGE_WORKSHOP = training("siege_workshop", TrainingSite.SIEGE_WORKSHOP);

	public static final BlockEntityType<WatchTowerBlockEntity> WATCH_TOWER = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, Warbands.id("watch_tower"),
			FabricBlockEntityTypeBuilder.create(WatchTowerBlockEntity::new, ModBlocks.WATCH_TOWER).build());

	public static final BlockEntityType<BlacksmithBlockEntity> BLACKSMITH = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, Warbands.id("blacksmith"),
			FabricBlockEntityTypeBuilder.create(BlacksmithBlockEntity::new, ModBlocks.BLACKSMITH).build());

	private ModBlockEntities() {
	}

	/** One type per training building, all backed by {@link TrainingBlockEntity}, so saved block ids stay the same. */
	private static BlockEntityType<TrainingBlockEntity> training(String name, TrainingSite site) {
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Warbands.id(name),
				FabricBlockEntityTypeBuilder.<TrainingBlockEntity>create((pos, state) -> new TrainingBlockEntity(site, pos, state), site.block()).build());
	}

	public static void initialize() {
	}
}
