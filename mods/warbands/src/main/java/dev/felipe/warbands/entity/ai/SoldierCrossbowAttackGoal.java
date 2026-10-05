package dev.felipe.warbands.entity.ai;

import dev.felipe.warbands.entity.CrossbowmanEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Items;

/**
 * Holds a crossbow and fires on a timer. It does not draw the way a bow does, and it never
 * calls {@code CrossbowItem.use}, so a shot cannot fail for lack of bolts or consume one.
 */
public class SoldierCrossbowAttackGoal extends Goal {
	private final CrossbowmanEntity mob;
	private final double speedModifier;
	private final int attackInterval;
	private int attackTime;
	private int seeTime;
	private boolean strafingClockwise;
	private boolean strafingBackwards;
	private int strafingTime = -1;

	public SoldierCrossbowAttackGoal(CrossbowmanEntity mob, double speedModifier, int attackInterval) {
		this.mob = mob;
		this.speedModifier = speedModifier;
		this.attackInterval = attackInterval;
		this.attackTime = attackInterval;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return this.mob.getTarget() != null && this.mob.isHolding(Items.CROSSBOW);
	}

	@Override
	public boolean canContinueToUse() {
		return (this.canUse() || !this.mob.getNavigation().isDone()) && this.mob.isHolding(Items.CROSSBOW);
	}

	@Override
	public void start() {
		super.start();
		this.mob.setAggressive(true);
	}

	@Override
	public void stop() {
		super.stop();
		this.mob.setAggressive(false);
		this.seeTime = 0;
		this.attackTime = this.attackInterval;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity target = this.mob.getTarget();
		if (target == null) {
			return;
		}
		double distSqr = this.mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
		float attackRadiusSqr = this.mob.attackRadius();
		attackRadiusSqr *= attackRadiusSqr;
		boolean canSee = this.mob.getSensing().hasLineOfSight(target);
		if (canSee != this.seeTime > 0) {
			this.seeTime = 0;
		}
		this.seeTime += canSee ? 1 : -1;

		if (this.mob.isPosted()) {
			this.mob.getNavigation().stop();
			this.strafingTime = -1;
		} else if (distSqr <= attackRadiusSqr && this.seeTime >= 5) {
			this.mob.getNavigation().stop();
			this.strafingTime++;
		} else {
			this.mob.getNavigation().moveTo(target, this.speedModifier);
			this.strafingTime = -1;
		}

		if (this.strafingTime >= 20) {
			if (this.mob.getRandom().nextFloat() < 0.3F) {
				this.strafingClockwise = !this.strafingClockwise;
			}
			if (this.mob.getRandom().nextFloat() < 0.3F) {
				this.strafingBackwards = !this.strafingBackwards;
			}
			this.strafingTime = 0;
		}

		if (this.strafingTime > -1) {
			if (distSqr > attackRadiusSqr * 0.75F) {
				this.strafingBackwards = false;
			} else if (distSqr < attackRadiusSqr * 0.25F) {
				this.strafingBackwards = true;
			}
			this.mob.getMoveControl().strafe(this.strafingBackwards ? -0.5F : 0.5F, this.strafingClockwise ? 0.5F : -0.5F);
			this.mob.lookAt(target, 30.0F, 30.0F);
		} else {
			this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
		}

		if (--this.attackTime <= 0 && canSee && distSqr <= attackRadiusSqr && this.seeTime >= 5) {
			this.mob.performRangedAttack(target, 1.0F);
			this.attackTime = this.attackInterval;
		}
	}
}
