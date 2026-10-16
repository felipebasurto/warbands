package dev.felipe.warbands.entity;

import dev.felipe.warbands.WarbandsAdvancements;
import dev.felipe.warbands.town.ArmyRegistry;
import dev.felipe.warbands.town.UnitType;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;

/**
 * Spawns a finished trainee just outside a building's door and counts them in the army. It waits there (see
 * {@link SoldierEntity#muster}) until its commander comes by or blows the War Horn.
 */
public final class SoldierSpawner {
	private SoldierSpawner() {
	}

	/**
	 * @param doorDistance how many blocks out the door the first candidate is. The Town Center
	 * uses 5; the Barracks and the Archery Range use 4.
	 */
	public static boolean spawn(ServerLevel level, BlockPos pos, Direction facing, UnitType type, UUID owner, String ownerName, int doorDistance) {
		SoldierEntity soldier = type.entityType().create(level, EntitySpawnReason.MOB_SUMMONED);
		if (soldier == null) {
			return false;
		}
		BlockPos spot = findSpawnSpot(level, pos, facing, soldier, doorDistance);
		soldier.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, facing.toYRot(), 0.0F);
		soldier.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.MOB_SUMMONED, null);
		soldier.assignOwner(owner, ownerName);
		soldier.muster(spot);
		if (!level.addFreshEntity(soldier)) {
			// Refused by the world: roll back its army count and let the queue try again, so the cost isn't lost.
			soldier.discard();
			return false;
		}
		WarbandsAdvancements.award(level.getServer(), owner, advancement(type));
		if (ArmyRegistry.count(level.getServer(), owner) >= 10) {
			WarbandsAdvancements.award(level.getServer(), owner, WarbandsAdvancements.WARBAND);
		}
		level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.8F, 1.2F);
		return true;
	}

	private static String advancement(UnitType type) {
		return switch (type) {
			case MILITIA -> WarbandsAdvancements.FIRST_RECRUIT;
			case ARCHER -> WarbandsAdvancements.ARCHER;
			case SPEARMAN -> WarbandsAdvancements.SPEARMAN;
			case CROSSBOWMAN -> WarbandsAdvancements.CROSSBOWMAN;
			case KNIGHT -> WarbandsAdvancements.KNIGHT;
			case RAM -> WarbandsAdvancements.RAM;
		};
	}

	/** Prefers a spot just outside the door, then anywhere nearby, then on top of the block. */
	private static BlockPos findSpawnSpot(ServerLevel level, BlockPos pos, Direction facing, SoldierEntity soldier, int doorDistance) {
		for (int distance = doorDistance; distance <= doorDistance + 3; distance++) {
			BlockPos base = pos.relative(facing, distance);
			for (int dy : new int[] {0, 1, -1, 2, -2}) {
				BlockPos candidate = base.above(dy);
				if (canStand(level, candidate, soldier)) {
					return candidate;
				}
			}
		}
		for (BlockPos candidate : BlockPos.withinManhattan(pos, 4, 2, 4)) {
			if (canStand(level, candidate, soldier)) {
				return candidate.immutable();
			}
		}
		return pos.above();
	}

	private static boolean canStand(ServerLevel level, BlockPos pos, SoldierEntity soldier) {
		BlockPos below = pos.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
				&& level.noCollision(soldier, soldier.getType().getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
	}
}
