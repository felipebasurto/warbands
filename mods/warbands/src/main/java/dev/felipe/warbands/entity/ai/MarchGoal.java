package dev.felipe.warbands.entity.ai;

import dev.felipe.warbands.entity.RamEntity;
import dev.felipe.warbands.entity.SoldierEntity;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A raider with nothing to fight walks to its march target. Long marches go in hops: if there's no full path, it
 * heads for a reachable spot about 16 blocks closer, and tries again.
 */
public class MarchGoal extends Goal {
	private static final double ARRIVED = 3.0;
	/** Raider rams march a little faster: they're slow, and they're the ones that must reach the town. */
	private static final double RAM_SPEED = 1.3;
	private final SoldierEntity raider;
	private int repath;

	public MarchGoal(SoldierEntity raider) {
		this.raider = raider;
		this.setFlags(EnumSet.of(Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		BlockPos target = this.raider.marchTarget();
		return this.raider.isRaider() && target != null && this.raider.getTarget() == null && !this.raider.isPassenger()
				&& this.raider.blockPosition().distSqr(target) > ARRIVED * ARRIVED;
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		this.repath = 0;
	}

	@Override
	public void tick() {
		if (--this.repath > 0 && !this.raider.getNavigation().isDone()) {
			return;
		}
		this.repath = 40;
		BlockPos target = this.raider.marchTarget();
		if (target == null) {
			return;
		}
		double speed = this.raider instanceof RamEntity ? RAM_SPEED : 1.0;
		if (!this.raider.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, speed)) {
			Vec3 hop = hopTowards(Vec3.atBottomCenterOf(target));
			if (hop != null) {
				this.raider.getNavigation().moveTo(hop.x, hop.y, hop.z, speed);
			}
		}
	}

	private @Nullable Vec3 hopTowards(Vec3 target) {
		return DefaultRandomPos.getPosTowards(this.raider, 16, 7, target, Math.PI / 2);
	}

	@Override
	public void stop() {
		this.raider.getNavigation().stop();
	}
}
