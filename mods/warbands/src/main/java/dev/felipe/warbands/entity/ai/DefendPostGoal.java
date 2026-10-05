package dev.felipe.warbands.entity.ai;

import dev.felipe.warbands.entity.SoldierEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

/**
 * While posted on a tower (your Watch Tower, or a camp tower for raiders), shoot enemies in range and do not leave
 * the platform. Your soldiers shoot monsters (creepers too: the platform is out of blast reach) and raiders; raider
 * guards shoot players and their soldiers. Targets are only picked, and kept, within the soldier's shooting range:
 * a target it can see but can't reach would freeze it on the spot.
 */
public class DefendPostGoal extends NearestAttackableTargetGoal<LivingEntity> {
	private final SoldierEntity soldier;

	public DefendPostGoal(SoldierEntity soldier) {
		super(soldier, LivingEntity.class, true, (target, level) -> accepts(soldier, target));
		this.soldier = soldier;
	}

	private static boolean accepts(SoldierEntity soldier, LivingEntity target) {
		if (soldier.isRaider()) {
			return target instanceof Player || target instanceof SoldierEntity other && !other.isRaider();
		}
		boolean enemy = target.getType().getCategory() == MobCategory.MONSTER
				|| target instanceof SoldierEntity other && other.isRaider();
		if (!enemy) {
			return false;
		}
		LivingEntity owner = soldier.getOwner();
		return owner == null || soldier.wantsToAttack(target, owner);
	}

	/** Called from the parent constructor too, before {@link #soldier} is set: read the mob field instead. */
	@Override
	protected double getFollowDistance() {
		return ((SoldierEntity) this.mob).attackRadius();
	}

	@Override
	public boolean canUse() {
		return this.soldier.isPosted() && super.canUse();
	}

	@Override
	public boolean canContinueToUse() {
		return this.soldier.isPosted() && super.canContinueToUse();
	}
}
