package dev.felipe.warbands.entity.ai;

import dev.felipe.warbands.entity.ArcherEntity;
import dev.felipe.warbands.town.Age;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Items;

/**
 * Copy of vanilla {@code RangedBowAttackGoal}, whose generics require a Monster.
 * Draws the bow, strafes around the target, and fires when fully drawn. The wait between shots
 * comes from the archer, so it gets shorter as its owner advances in age.
 */
public class SoldierBowAttackGoal extends Goal {
	private final ArcherEntity mob;
	private final double speedModifier;
	private int attackTime = -1;
	private int seeTime;
	private boolean strafingClockwise;
	private boolean strafingBackwards;
	private int strafingTime = -1;

	public SoldierBowAttackGoal(ArcherEntity mob, double speedModifier) {
		this.mob = mob;
		this.speedModifier = speedModifier;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return this.mob.getTarget() != null && this.mob.isHolding(Items.BOW);
	}

	@Override
	public boolean canContinueToUse() {
		return (this.canUse() || !this.mob.getNavigation().isDone()) && this.mob.isHolding(Items.BOW);
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
		this.attackTime = -1;
		this.mob.stopUsingItem();
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
		} else if (distSqr <= attackRadiusSqr && this.seeTime >= 20) {
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

		if (this.mob.isUsingItem()) {
			if (!canSee && this.seeTime < -60) {
				this.mob.stopUsingItem();
			} else if (canSee) {
				int pullTime = this.mob.getTicksUsingItem();
				if (pullTime >= Age.BOW_DRAW_TICKS) {
					this.mob.stopUsingItem();
					this.mob.performRangedAttack(target, BowItem.getPowerForTime(pullTime));
					this.attackTime = this.mob.shotIntervalTicks();
				}
			}
		} else if (--this.attackTime <= 0 && this.seeTime >= -60
				&& (!this.mob.isPosted() || (canSee && distSqr <= attackRadiusSqr))) {
			this.mob.startUsingItem(ProjectileUtil.getWeaponHoldingHand(this.mob, Items.BOW));
		}
	}
}
