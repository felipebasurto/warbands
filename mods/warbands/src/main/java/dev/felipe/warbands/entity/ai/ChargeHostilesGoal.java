package dev.felipe.warbands.entity.ai;

import dev.felipe.warbands.entity.SoldierEntity;
import dev.felipe.warbands.entity.Stance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;

/**
 * Charge is the one order that picks its own targets: nearby hostile mobs (slimes and phantoms
 * included), never friends. Creepers are left alone so soldiers don't blow up next to the player.
 * Follow and Hold keep the vanilla "only what you fight" rules.
 */
public class ChargeHostilesGoal extends NearestAttackableTargetGoal<LivingEntity> {
	private final SoldierEntity soldier;

	public ChargeHostilesGoal(SoldierEntity soldier) {
		super(soldier, LivingEntity.class, true, (target, level) -> accepts(soldier, target));
		this.soldier = soldier;
	}

	private static boolean accepts(SoldierEntity soldier, LivingEntity target) {
		if (target.getType().getCategory() != MobCategory.MONSTER || target.getType() == EntityType.CREEPER) {
			return false;
		}
		LivingEntity owner = soldier.getOwner();
		return owner == null || soldier.wantsToAttack(target, owner);
	}

	@Override
	public boolean canUse() {
		return this.soldier.stance() == Stance.CHARGE && !this.soldier.isRaider() && super.canUse();
	}

	@Override
	public boolean canContinueToUse() {
		return this.soldier.stance() == Stance.CHARGE && super.canContinueToUse();
	}
}
