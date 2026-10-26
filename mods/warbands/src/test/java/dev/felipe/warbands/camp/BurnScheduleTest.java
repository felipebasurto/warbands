package dev.felipe.warbands.camp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The deadline rule {@code Camps.tickBurning} applies when a camp's footprint is not ticking.
 * No world is started: chunk load and the block sweep are not covered here.
 */
class BurnScheduleTest {
	private static final int STEP = 5;

	@Test
	void unloadedTimeDoesNotSpendTheBurn() {
		long now = 10_000L;
		long razeAt = now + 1_000L;
		long remaining = razeAt - now;
		// Longer than one burn, sampled on the same cadence as the burn tick.
		for (int i = 0; i < 400; i++) {
			now += STEP;
			razeAt = BurnSchedule.defer(razeAt, now, STEP);
			assertEquals(remaining, razeAt - now, "step " + i);
		}
		assertTrue(razeAt > now);
	}

	@Test
	void pauseResumePauseKeepsOnlyLoadedTime() {
		long now = 0L;
		long razeAt = 1_000L;
		now += 200L; // footprint ticking: the deadline stays, so this time is spent
		assertEquals(800L, razeAt - now);

		for (int i = 0; i < 100; i++) {
			now += STEP;
			razeAt = BurnSchedule.defer(razeAt, now, STEP);
		}
		assertEquals(800L, razeAt - now);

		now += 300L;
		assertEquals(500L, razeAt - now);

		for (int i = 0; i < 80; i++) {
			now += STEP;
			razeAt = BurnSchedule.defer(razeAt, now, STEP);
		}
		assertEquals(500L, razeAt - now);
	}

	@Test
	void aDeadlineAlreadyReachedIsNotPushedOut() {
		long razeAt = 1_000L;
		long now = 1_000L;
		for (int i = 0; i < 50; i++) {
			now += STEP;
			assertEquals(1_000L, BurnSchedule.defer(razeAt, now, STEP));
		}
		assertEquals(4_000L, BurnSchedule.defer(4_000L, 5_000L, STEP));
	}
}
