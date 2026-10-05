package dev.felipe.warbands.entity.ai;

import dev.felipe.warbands.entity.SoldierEntity;
import dev.felipe.warbands.entity.Stance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import org.jspecify.annotations.Nullable;

/**
 * Your soldiers fight raiders they see without being told. How far they go depends on the order:
 * <ul>
 * <li><b>Follow</b> is an escort: only raiders within {@link #ESCORT_RANGE} blocks of the commander, and they let go
 * of one that leads them further away. (Fighting back when hit, or at what you hit, is vanilla's owner goals.)</li>
 * <li><b>Attack</b> hunts: any raider in follow range (24), before monsters ({@code ChargeHostilesGoal} runs after).</li>
 * <li><b>Hold</b>, and recruits waiting at their building: only inside their area (see {@link #inArea}).</li>
 * </ul>
 */
public class FightRaidersGoal extends NearestAttackableTargetGoal<SoldierEntity> {
	/** On Follow, raiders this close to the commander are fair game. */
	public static final double ESCORT_RANGE = 12.0;
	/** On Follow, a chase is dropped once the raider is this far from the commander. */
	private static final double ESCORT_LEASH = 20.0;
	private final SoldierEntity soldier;

	public FightRaidersGoal(SoldierEntity soldier) {
		super(soldier, SoldierEntity.class, true, (target, level) -> target instanceof SoldierEntity other && other.isRaider()
				&& inArea(soldier, target) && escorting(soldier, target, ESCORT_RANGE));
		this.soldier = soldier;
	}

	@Override
	public boolean canUse() {
		return !this.soldier.isRaider() && this.soldier.isTame() && super.canUse();
	}

	@Override
	public boolean canContinueToUse() {
		return !this.soldier.isRaider() && super.canContinueToUse() && inArea(this.soldier, this.soldier.getTarget())
				&& escorting(this.soldier, this.soldier.getTarget(), ESCORT_LEASH);
	}

	/** Off Follow (or with no commander around, or while mustering) anything goes; on Follow, near the commander. */
	static boolean escorting(SoldierEntity soldier, @Nullable LivingEntity target, double range) {
		if (target == null || soldier.stance() != Stance.FOLLOW || soldier.isMustering()) {
			return true;
		}
		LivingEntity owner = soldier.getOwner();
		return owner == null || owner.level() != soldier.level() || target.distanceToSqr(owner) <= range * range;
	}

	/**
	 * Vanilla's nearest-target goals ignore a mob's home when picking a target (only the other target goals check
	 * it), so a soldier on Hold would lock onto an enemy it may not reach and stand at the edge of its area,
	 * ignoring one right next to it. Held soldiers and camp guards only pick, and keep, targets inside their area.
	 */
	static boolean inArea(SoldierEntity soldier, @Nullable LivingEntity target) {
		return target == null || !soldier.hasHome() || soldier.isWithinHome(target.blockPosition());
	}
}
