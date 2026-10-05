package dev.felipe.warbands.entity.ai;

import dev.felipe.warbands.entity.SoldierEntity;
import dev.felipe.warbands.entity.Stance;
import dev.felipe.warbands.town.BuildingRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Idle wandering only while following. A held soldier stays on the post. Idle soldiers don't wander into your
 * buildings: a crowd in the Town Center blocks the door.
 */
public class OrderedStrollGoal extends WaterAvoidingRandomStrollGoal {
	private final SoldierEntity soldier;

	public OrderedStrollGoal(SoldierEntity soldier) {
		super(soldier, 0.8);
		this.soldier = soldier;
	}

	@Override
	public boolean canUse() {
		return this.soldier.stance() == Stance.FOLLOW && super.canUse();
	}

	@Override
	protected @Nullable Vec3 getPosition() {
		for (int attempt = 0; attempt < 3; attempt++) {
			Vec3 spot = super.getPosition();
			if (spot == null || !insideBuilding(BlockPos.containing(spot))) {
				return spot;
			}
		}
		return null;
	}

	private boolean insideBuilding(BlockPos pos) {
		if (!(this.soldier.level() instanceof ServerLevel level) || level.canSeeSky(pos)) {
			return false;
		}
		for (BuildingRegistry.Building building : BuildingRegistry.all(level)) {
			BlockPos min = building.min();
			BlockPos max = building.max();
			if (pos.getX() >= min.getX() && pos.getX() <= max.getX() && pos.getY() >= min.getY() && pos.getY() <= max.getY()
					&& pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ()) {
				return true;
			}
		}
		return false;
	}
}
