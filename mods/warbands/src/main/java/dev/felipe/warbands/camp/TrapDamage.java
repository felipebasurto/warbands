package dev.felipe.warbands.camp;

/**
 * Camp guns and powder kegs spare the camp's own guards. A player or an enemy is not spared.
 * <p>
 * A camp gun is an arrow with no shooter. Vanilla still names that arrow as the attacker, so both entity
 * flags are set and the usual friendly-fire check never sees a guard. A powder keg is an explosion with
 * neither entity set. A shot that has a shooter, and a blast that names one, still land.
 */
public final class TrapDamage {
	private TrapDamage() {
	}

	public static boolean sparesOwnGuard(boolean ownGuard, boolean arrow, boolean arrowHasShooter,
			boolean hasCausingEntity, boolean hasDirectEntity, boolean explosion) {
		if (!ownGuard) {
			return false;
		}
		if (arrow && !arrowHasShooter) {
			return true;
		}
		return explosion && !hasCausingEntity && !hasDirectEntity;
	}
}
