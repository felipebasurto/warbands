package dev.felipe.warbands.entity.ai;

import dev.felipe.warbands.entity.SoldierEntity;
import dev.felipe.warbands.entity.Stance;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;

/**
 * Follows the owner unless the soldier is holding a post or is a recruit still waiting at its building. Charging soldiers follow too once the
 * fight is over, so they never get left behind out of horn range.
 */
public class FollowWhenOrderedGoal extends FollowOwnerGoal {
	private final SoldierEntity soldier;

	public FollowWhenOrderedGoal(SoldierEntity soldier) {
		super(soldier, 1.1, 8.0F, 4.0F);
		this.soldier = soldier;
	}

	@Override
	public boolean canUse() {
		return this.soldier.stance() != Stance.HOLD && !this.soldier.isMustering() && super.canUse();
	}

	@Override
	public boolean canContinueToUse() {
		return this.soldier.stance() != Stance.HOLD && !this.soldier.isMustering() && super.canContinueToUse();
	}
}
