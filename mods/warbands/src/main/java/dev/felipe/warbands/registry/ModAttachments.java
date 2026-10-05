package dev.felipe.warbands.registry;

import dev.felipe.warbands.Warbands;
import dev.felipe.warbands.camp.CampRegistry;
import dev.felipe.warbands.raid.RaidRegistry;
import dev.felipe.warbands.town.AgeRegistry;
import dev.felipe.warbands.town.ArmyOrders;
import dev.felipe.warbands.town.ArmyRegistry;
import dev.felipe.warbands.town.BuildingRegistry;
import dev.felipe.warbands.town.HouseRegistry;
import dev.felipe.warbands.town.Relics;
import dev.felipe.warbands.town.StructureBlocks;
import dev.felipe.warbands.town.TownRegistry;
import dev.felipe.warbands.town.TrainingRegistry;
import dev.felipe.warbands.town.UpgradeRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/** World-saved data, stored with Fabric's data attachment API on ServerLevel. */
public final class ModAttachments {
	/** Living soldiers per owner. Stored on the overworld so it's global across dimensions. */
	public static final AttachmentType<ArmyRegistry.Data> ARMY = AttachmentRegistry.create(Warbands.id("army"),
			builder -> builder.persistent(ArmyRegistry.Data.CODEC).initializer(() -> ArmyRegistry.Data.EMPTY));

	/** The War Horn order each commander's army is under. Stored on the overworld, like the army. */
	public static final AttachmentType<ArmyOrders.Data> ARMY_ORDERS = AttachmentRegistry.create(Warbands.id("army_orders"),
			builder -> builder.persistent(ArmyOrders.Data.CODEC).initializer(() -> ArmyOrders.Data.EMPTY));

	/** Highest age reached per player. Stored on the overworld so soldiers anywhere can read it. */
	public static final AttachmentType<AgeRegistry.Data> AGES = AttachmentRegistry.create(Warbands.id("ages"),
			builder -> builder.persistent(AgeRegistry.Data.CODEC).initializer(() -> AgeRegistry.Data.EMPTY));

	/** Hearth (house) positions, per dimension. */
	public static final AttachmentType<HouseRegistry.Data> HOUSES = AttachmentRegistry.create(Warbands.id("houses"),
			builder -> builder.persistent(HouseRegistry.Data.CODEC).initializer(() -> HouseRegistry.Data.EMPTY));

	/** Town Center positions and owners, per dimension. */
	public static final AttachmentType<TownRegistry.Data> TOWNS = AttachmentRegistry.create(Warbands.id("towns"),
			builder -> builder.persistent(TownRegistry.Data.CODEC).initializer(() -> TownRegistry.Data.EMPTY));

	/** Training building positions and owners, one list per kind and dimension. Their queues count toward population. */
	public static final AttachmentType<TrainingRegistry.Data> BARRACKS = training("barracks");
	public static final AttachmentType<TrainingRegistry.Data> ARCHERY_RANGES = training("archery_ranges");
	public static final AttachmentType<TrainingRegistry.Data> STABLES = training("stables");
	public static final AttachmentType<TrainingRegistry.Data> SIEGE_WORKSHOPS = training("siege_workshops");

	/** Feudal upgrades bought per commander. Stored on the overworld, like the army. */
	public static final AttachmentType<UpgradeRegistry.Data> UPGRADES = AttachmentRegistry.create(Warbands.id("upgrades"),
			builder -> builder.persistent(UpgradeRegistry.Data.CODEC).initializer(() -> UpgradeRegistry.Data.EMPTY));

	/** Blocks placed by blueprints (they drop nothing when broken), per dimension. */
	public static final AttachmentType<StructureBlocks.Data> STRUCTURE_BLOCKS = AttachmentRegistry.create(
			Warbands.id("structure_blocks"), builder -> builder.persistent(StructureBlocks.Data.CODEC).initializer(StructureBlocks.Data::new));

	/** Every building placed from a blueprint (for repairs and the half-wrecked rule), per dimension. */
	public static final AttachmentType<BuildingRegistry.Data> BUILDINGS = AttachmentRegistry.create(Warbands.id("buildings"),
			builder -> builder.persistent(BuildingRegistry.Data.CODEC).initializer(() -> BuildingRegistry.Data.EMPTY));

	/** Enemy camps sent by War Maps, per dimension. */
	public static final AttachmentType<CampRegistry.Data> CAMPS = AttachmentRegistry.create(Warbands.id("camps"),
			builder -> builder.persistent(CampRegistry.Data.CODEC).initializer(() -> CampRegistry.Data.EMPTY));

	/** Relics enshrined per Town Center and kinds each owner already earned, per dimension. */
	public static final AttachmentType<Relics.Data> RELICS = AttachmentRegistry.create(Warbands.id("relics"),
			builder -> builder.persistent(Relics.Data.CODEC).initializer(() -> Relics.Data.EMPTY));

	/** Raid timers and the raid in progress, per Town Center and dimension. */
	public static final AttachmentType<RaidRegistry.Data> RAIDS = AttachmentRegistry.create(Warbands.id("raids"),
			builder -> builder.persistent(RaidRegistry.Data.CODEC).initializer(() -> RaidRegistry.Data.EMPTY));

	private ModAttachments() {
	}

	private static AttachmentType<TrainingRegistry.Data> training(String name) {
		return AttachmentRegistry.create(Warbands.id(name),
				builder -> builder.persistent(TrainingRegistry.Data.CODEC).initializer(() -> TrainingRegistry.Data.EMPTY));
	}

	public static void initialize() {
	}
}
