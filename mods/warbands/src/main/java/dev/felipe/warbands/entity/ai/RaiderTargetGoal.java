package dev.felipe.warbands.entity.ai;

import dev.felipe.warbands.entity.RamEntity;
import dev.felipe.warbands.entity.SoldierEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

/** A raider attacks players and their soldiers on sight. Its own side is never a target. A raider ram picks no targets: it goes for the Town Center. */
public class RaiderTargetGoal extends NearestAttackableTargetGoal<LivingEntity> {
	private final SoldierEntity raider;

	public RaiderTargetGoal(SoldierEntity raider) {
		super(raider, LivingEntity.class, true, (target, level) -> (target instanceof Player
				|| target instanceof SoldierEntity soldier && !soldier.isRaider()) && FightRaidersGoal.inArea(raider, target));
		this.raider = raider;
	}

	@Override
	public boolean canUse() {
		return this.raider.isRaider() && !(this.raider instanceof RamEntity) && super.canUse();
	}

	@Override
	public boolean canContinueToUse() {
		return this.raider.isRaider() && super.canContinueToUse() && FightRaidersGoal.inArea(this.raider, this.raider.getTarget());
	}
}
