package dev.felipe.warbands.camp;

/**
 * A camp's own guns and powder kegs spare that camp's guards. A player or an enemy is not spared,
 * and neither is a guard hit by a dispenser the player built or by TNT.
 * <p>
 * A camp gun is a shooterless arrow from a dispenser inside that camp's footprint. A powder keg is
 * one the camp placed. Any other shooterless arrow, and any other blast, still lands.
 */
public final class TrapDamage {
	private TrapDamage() {
	}

	public static boolean sparesOwnGuard(boolean guardOfThisCamp, boolean shooterlessArrow, boolean dispenserInsideThisCamp,
			boolean campPlacedPowderKeg) {
		if (!guardOfThisCamp) {
			return false;
		}
		if (shooterlessArrow && dispenserInsideThisCamp) {
			return true;
		}
		return campPlacedPowderKeg;
	}
}
