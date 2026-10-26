package dev.felipe.warbands.camp;

/**
 * The burn deadline ({@code razeAt}) is an absolute game time. Left alone, time spent with the camp's chunks
 * unloaded spends the burn too: the next visit either finishes it in one tick or clears only what is loaded.
 * {@link #defer} gives that time back. A deadline that has already passed is not moved, so the visit that finds
 * the footprint ticking can still clear whatever is left.
 */
final class BurnSchedule {
	private BurnSchedule() {
	}

	/**
	 * Deadline after one missed burn step. {@code stepTicks} is the game time since the previous step (the burn
	 * advances every {@code BURN_STEP} ticks), so the remaining time stays what it was then.
	 */
	static long defer(long razeAt, long now, int stepTicks) {
		return razeAt > now ? razeAt + stepTicks : razeAt;
	}
}
