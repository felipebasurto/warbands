package dev.felipe.warbands.camp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The pause rule {@code Camps.tickBurning} applies around a footprint that is not ticking.
 * No world is started: chunk load, saving, and the block sweep are not covered here.
 */
class BurnScheduleTest {
	private static final int STEP = 5;

	@Test
	void unloadedStepsRecordThePauseOnce() {
		long now = 10_000L;
		long razeAt = now + 1_000L;
		long pausedAt = BurnSchedule.NOT_PAUSED;
		int saves = 0;
		BurnSchedule.Decision first = null;
		for (int i = 0; i < 400; i++) {
			now += STEP;
			BurnSchedule.Decision decision = BurnSchedule.whileUnloaded(razeAt, pausedAt, now);
			if (decision.save()) {
				saves++;
				first = decision;
			}
			assertEquals(razeAt, decision.razeAt(), "step " + i);
			pausedAt = decision.pausedAt();
		}
		assertEquals(1, saves);
		assertEquals(10_000L + STEP, first.pausedAt());
		assertTrue(pausedAt >= 0);
		assertEquals(razeAt, BurnSchedule.whileUnloaded(razeAt, pausedAt, now).razeAt());
	}

	@Test
	void resumeShiftsTheDeadlineOnceByTheWholePause() {
		long razeAt = 5_000L;
		long pausedAt = BurnSchedule.NOT_PAUSED;
		BurnSchedule.Decision paused = BurnSchedule.whileUnloaded(razeAt, pausedAt, 1_000L);
		assertTrue(paused.save());
		assertEquals(1_000L, paused.pausedAt());

		long now = 1_000L + 2_500L;
		BurnSchedule.Decision resumed = BurnSchedule.whileReady(paused.razeAt(), paused.pausedAt(), now);
		assertTrue(resumed.save());
		assertEquals(BurnSchedule.NOT_PAUSED, resumed.pausedAt());
		assertEquals(razeAt + (now - paused.pausedAt()), resumed.razeAt());
		assertEquals(4_000L, resumed.razeAt() - now);

		BurnSchedule.Decision again = BurnSchedule.whileReady(resumed.razeAt(), resumed.pausedAt(), now + 500L);
		assertFalse(again.save());
		assertEquals(resumed.razeAt(), again.razeAt());
	}

	@Test
	void pauseResumePauseKeepsOnlyLoadedTime() {
		long now = 0L;
		long razeAt = 1_000L;
		long pausedAt = BurnSchedule.NOT_PAUSED;

		now = 200L;
		assertEquals(800L, razeAt - now);

		BurnSchedule.Decision paused = BurnSchedule.whileUnloaded(razeAt, pausedAt, now);
		assertTrue(paused.save());
		now = 700L;
		BurnSchedule.Decision resumed = BurnSchedule.whileReady(paused.razeAt(), paused.pausedAt(), now);
		assertEquals(800L, resumed.razeAt() - now);
		razeAt = resumed.razeAt();
		pausedAt = resumed.pausedAt();

		now = 1_000L;
		assertEquals(500L, razeAt - now);

		paused = BurnSchedule.whileUnloaded(razeAt, pausedAt, now);
		assertTrue(paused.save());
		assertEquals(1_000L, paused.pausedAt());
		now = 1_400L;
		resumed = BurnSchedule.whileReady(paused.razeAt(), paused.pausedAt(), now);
		assertEquals(500L, resumed.razeAt() - now);
		assertEquals(BurnSchedule.NOT_PAUSED, resumed.pausedAt());
	}

	@Test
	void aDeadlineAlreadyReachedIsNotPushedOut() {
		BurnSchedule.Decision unloaded = BurnSchedule.whileUnloaded(1_000L, BurnSchedule.NOT_PAUSED, 1_000L);
		assertFalse(unloaded.save());
		assertEquals(BurnSchedule.NOT_PAUSED, unloaded.pausedAt());
		assertEquals(1_000L, unloaded.razeAt());

		unloaded = BurnSchedule.whileUnloaded(4_000L, BurnSchedule.NOT_PAUSED, 5_000L);
		assertFalse(unloaded.save());
		assertEquals(4_000L, unloaded.razeAt());

		BurnSchedule.Decision ready = BurnSchedule.whileReady(4_000L, BurnSchedule.NOT_PAUSED, 9_000L);
		assertFalse(ready.save());
		assertEquals(4_000L, ready.razeAt());

		// A pause stamped at the deadline itself does not move it, but it is cleared.
		ready = BurnSchedule.whileReady(1_000L, 1_000L, 5_000L);
		assertTrue(ready.save());
		assertEquals(1_000L, ready.razeAt());
		assertEquals(BurnSchedule.NOT_PAUSED, ready.pausedAt());
	}

	@Test
	void timePastTheOriginalDeadlineDuringAPauseIsGivenBack() {
		BurnSchedule.Decision paused = BurnSchedule.whileUnloaded(1_500L, BurnSchedule.NOT_PAUSED, 1_000L);
		BurnSchedule.Decision resumed = BurnSchedule.whileReady(paused.razeAt(), paused.pausedAt(), 3_000L);
		assertEquals(1_500L + (3_000L - 1_000L), resumed.razeAt());
		assertEquals(500L, resumed.razeAt() - 3_000L);
	}
}
