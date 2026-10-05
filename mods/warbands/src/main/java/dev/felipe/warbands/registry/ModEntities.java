package dev.felipe.warbands.registry;

import dev.felipe.warbands.Warbands;
import dev.felipe.warbands.entity.ArcherEntity;
import dev.felipe.warbands.entity.CrossbowmanEntity;
import dev.felipe.warbands.entity.KnightEntity;
import dev.felipe.warbands.entity.MilitiaEntity;
import dev.felipe.warbands.entity.RamEntity;
import dev.felipe.warbands.entity.SmithEntity;
import dev.felipe.warbands.entity.SpearmanEntity;
import dev.felipe.warbands.entity.StableHorseEntity;
import dev.felipe.warbands.entity.WarhorseEntity;
import dev.felipe.warbands.entity.WarlordEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	public static final EntityType<MilitiaEntity> MILITIA = register("militia", humanoid(MilitiaEntity::new));
	public static final EntityType<ArcherEntity> ARCHER = register("archer", humanoid(ArcherEntity::new));
	public static final EntityType<SpearmanEntity> SPEARMAN = register("spearman", humanoid(SpearmanEntity::new));
	public static final EntityType<CrossbowmanEntity> CROSSBOWMAN = register("crossbowman", humanoid(CrossbowmanEntity::new));
	public static final EntityType<KnightEntity> KNIGHT = register("knight", humanoid(KnightEntity::new));
	public static final EntityType<WarlordEntity> WARLORD = register("warlord", humanoid(WarlordEntity::new).sized(0.7F, 2.0F));
	public static final EntityType<RamEntity> RAM = register("ram",
			EntityType.Builder.of(RamEntity::new, MobCategory.MISC)
					.sized(1.4F, 1.3F)
					.eyeHeight(1.0F)
					.clientTrackingRange(10));
	public static final EntityType<WarhorseEntity> WARHORSE = register("warhorse",
			EntityType.Builder.of(WarhorseEntity::new, MobCategory.CREATURE)
					.sized(1.3964844F, 1.6F)
					.eyeHeight(1.52F)
					.passengerAttachments(1.44375F)
					.clientTrackingRange(10));
	/** Scenery: MISC so they never touch mob spawn caps. */
	public static final EntityType<StableHorseEntity> STABLE_HORSE = register("stable_horse",
			EntityType.Builder.of(StableHorseEntity::new, MobCategory.MISC)
					.sized(1.3964844F, 1.6F)
					.eyeHeight(1.52F)
					.clientTrackingRange(10));
	public static final EntityType<SmithEntity> SMITH = register("smith", humanoid(SmithEntity::new).sized(0.6F, 1.95F));

	private ModEntities() {
	}

	/** Player-sized mob. The riding offset is the zombie's: without it a knight floats above the saddle. */
	private static <T extends Entity> EntityType.Builder<T> humanoid(EntityType.EntityFactory<T> factory) {
		return EntityType.Builder.of(factory, MobCategory.MISC)
				.sized(0.6F, 1.8F)
				.eyeHeight(1.62F)
				.ridingOffset(-0.7F)
				.clientTrackingRange(10);
	}

	private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Warbands.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(MILITIA, MilitiaEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ARCHER, ArcherEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(SPEARMAN, SpearmanEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(CROSSBOWMAN, CrossbowmanEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(KNIGHT, KnightEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(WARLORD, WarlordEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(RAM, RamEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(WARHORSE, WarhorseEntity.createBaseHorseAttributes());
		FabricDefaultAttributeRegistry.register(STABLE_HORSE, StableHorseEntity.createBaseHorseAttributes());
		FabricDefaultAttributeRegistry.register(SMITH, SmithEntity.createAttributes());
	}
}
