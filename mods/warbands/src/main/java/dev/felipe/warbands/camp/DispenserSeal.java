package dev.felipe.warbands.camp;

/**
 * Whether a hopper may pull from a block. A dispenser that still belongs to a camp is sealed, burning included
 * ({@link Camps#campAt} is what {@code campPresent} means). Opening and breaking are sealed separately.
 */
public final class DispenserSeal {
	private DispenserSeal() {
	}

	/**
	 * {@code dispenser} is a dispenser block. A dropper counts: it extends that block, and the open seal already
	 * refuses it. Other blocks in a camp, and a dispenser outside one, stay pullable.
	 */
	public static boolean hopperMayPull(boolean dispenser, boolean campPresent) {
		return !dispenser || !campPresent;
	}
}
