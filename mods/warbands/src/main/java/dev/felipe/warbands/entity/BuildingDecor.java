package dev.felipe.warbands.entity;

import dev.felipe.warbands.blueprint.Blueprint;
import dev.felipe.warbands.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Decorative mobs that bring a building to life: horses in the Stable's stalls, a smith at the forge.
 * They are scenery, not loot: they can't be hurt (except in creative), ridden, leashed, bred or traded
 * with, drop nothing, and disappear once their building's core block is gone. They are MISC mobs, so they
 * never count toward spawn caps, and the smith is not a Villager, so villages, iron golems, raids and
 * zombies ignore it. Positions are structure coordinates from {@code tools/gen_structures.py}.
 */
public final class BuildingDecor {
	private BuildingDecor() {
	}

	/** Called once after a blueprint is placed. {@code core} is the building's core block in the world. */
	public static void spawn(ServerLevel level, Blueprint blueprint, BlockPos origin, StructurePlaceSettings settings, BlockPos core) {
		switch (blueprint) {
			case STABLE -> {
				// Two of the four 2x2 stalls get a horse; each looks out at the aisle.
				place(level, ModEntities.STABLE_HORSE.create(level, EntitySpawnReason.STRUCTURE), core,
						stall(origin, settings, new BlockPos(2, 1, 2), new BlockPos(3, 1, 3)), Vec3.atCenterOf(core));
				place(level, ModEntities.STABLE_HORSE.create(level, EntitySpawnReason.STRUCTURE), core,
						stall(origin, settings, new BlockPos(5, 1, 5), new BlockPos(6, 1, 6)), Vec3.atCenterOf(core));
			}
			case BLACKSMITH -> place(level, ModEntities.SMITH.create(level, EntitySpawnReason.STRUCTURE), core,
					Vec3.atBottomCenterOf(world(origin, settings, new BlockPos(2, 1, 3))),
					Vec3.atCenterOf(world(origin, settings, new BlockPos(1, 1, 3))));
			default -> {
			}
		}
	}

	private static BlockPos world(BlockPos origin, StructurePlaceSettings settings, BlockPos local) {
		return origin.offset(StructureTemplate.calculateRelativePosition(settings, local));
	}

	/** Bottom center of the 2x2 stall between two opposite corner blocks, whatever the rotation. */
	private static Vec3 stall(BlockPos origin, StructurePlaceSettings settings, BlockPos cornerA, BlockPos cornerB) {
		BlockPos a = world(origin, settings, cornerA);
		BlockPos b = world(origin, settings, cornerB);
		return new Vec3((a.getX() + b.getX()) / 2.0 + 0.5, a.getY(), (a.getZ() + b.getZ()) / 2.0 + 0.5);
	}

	private static void place(ServerLevel level, @Nullable Mob mob, BlockPos anchor, Vec3 pos, Vec3 lookAt) {
		if (mob == null) {
			return;
		}
		float yaw = (float) (Mth.atan2(lookAt.z - pos.z, lookAt.x - pos.x) * Mth.RAD_TO_DEG) - 90.0F;
		mob.snapTo(pos.x, pos.y, pos.z, yaw, 0.0F);
		mob.setYHeadRot(yaw);
		mob.setYBodyRot(yaw);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(anchor), EntitySpawnReason.STRUCTURE, null);
		if (mob instanceof StableHorseEntity horse) {
			horse.setAge(0);
			horse.setAnchor(anchor);
		} else if (mob instanceof SmithEntity smith) {
			smith.setAnchor(anchor);
		}
		level.addFreshEntity(mob);
	}

	/** True when the building this mob belongs to has been broken (or it never had one). */
	static boolean anchorGone(Level level, @Nullable BlockPos anchor, Block core) {
		return anchor == null || level.isLoaded(anchor) && !level.getBlockState(anchor).is(core);
	}
}
