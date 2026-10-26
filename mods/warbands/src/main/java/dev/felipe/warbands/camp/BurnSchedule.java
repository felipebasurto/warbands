package dev.felipe.warbands.camp;

/**
 * The burn deadline ({@code razeAt}) is an absolute game time. While the footprint is not ticking, the pause is
 * the game time it started, recorded once. When the footprint ticks again the deadline shifts by {@code now -
 * pausedAt} once. A deadline that had already passed when the pause would have started is not moved, so that visit
 * can still clear whatever is left.
 */
final class BurnSchedule {
	/** No pause is recorded. A real pause is a game time, which is never negative. */
	static final long NOT_PAUSED = -1L;

	private BurnSchedule() {
	}

	/**
	 * @param razeAt the deadline, unchanged while paused
	 * @param pausedAt {@link #NOT_PAUSED} when the burn is running on the clock
	 * @param save true only when persisted state has to change
	 */
	record Decision(long razeAt, long pausedAt, boolean save) {
	}

	/** Footprint not ticking. The first such step records {@code now}; later steps change nothing. */
	static Decision whileUnloaded(long razeAt, long pausedAt, long now) {
		if (pausedAt >= 0) {
			return new Decision(razeAt, pausedAt, false);
		}
		if (razeAt <= now) {
			return new Decision(razeAt, NOT_PAUSED, false);
		}
		return new Decision(razeAt, now, true);
	}

	/**
	 * Footprint ticking again. A recorded pause shifts the deadline by the time since it started and then clears.
	 * No pause leaves {@code razeAt} where it is. A deadline that had already passed at {@code pausedAt} is cleared
	 * off the pause map and not moved.
	 */
	static Decision whileReady(long razeAt, long pausedAt, long now) {
		if (pausedAt < 0) {
			return new Decision(razeAt, NOT_PAUSED, false);
		}
		if (now < pausedAt) {
			return new Decision(razeAt, pausedAt, false);
		}
		// Already due when the pause was stamped: clear it, but do not move the deadline.
		if (razeAt <= pausedAt) {
			return new Decision(razeAt, NOT_PAUSED, true);
		}
		return new Decision(razeAt + (now - pausedAt), NOT_PAUSED, true);
	}
}
