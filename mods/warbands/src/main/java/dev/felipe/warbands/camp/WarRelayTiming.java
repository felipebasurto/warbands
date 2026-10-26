package dev.felipe.warbands.camp;

/**
 * How long a war relay waits before it checks again. A standing camp checks every step, one that is
 * not registered yet keeps a slow check, and one that is gone stops.
 */
final class WarRelayTiming {
	private WarRelayTiming() {
	}

	/**
	 * Ticks until the next check, or {@code -1} when the relay should stop. A standing camp checks every
	 * {@code step} ticks. A camp that is not registered yet ({@code pendingCamp}) keeps the slow check, a
	 * tenth of that rate. A camp that is gone stops.
	 */
	static int nextDelay(boolean inCamp, boolean pendingCamp, int step) {
		if (inCamp) {
			return step;
		}
		return pendingCamp ? step * 10 : -1;
	}

	/**
	 * Whether a block {@code (dx, dy, dz)} from a camp's center sits in that camp's footprint, with the same slack
	 * {@link Camps#campAt} gives traps just inside the walls.
	 */
	static boolean inFootprint(int dx, int dy, int dz, int half, int height) {
		return Math.abs(dx) <= half + 4 && Math.abs(dz) <= half + 4 && dy >= -6 && dy <= height + 6;
	}
}
