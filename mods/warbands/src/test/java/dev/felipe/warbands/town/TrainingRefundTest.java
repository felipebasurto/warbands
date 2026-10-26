package dev.felipe.warbands.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Which Town Center is paid when a training building breaks.
 * No world is started: chunk load and the stockpile credit are not covered here.
 */
class TrainingRefundTest {
	/** {@code TownCenterBlockEntity.HOUSE_RADIUS} squared. */
	private static final double RADIUS_SQR = 64.0 * 64.0;

	@Test
	void nothingOwedIsNotPaid() {
		assertNull(TrainingRefund.choose(false, List.of(center(1L, false, 1.0)), RADIUS_SQR));
	}

	@Test
	void unloadedCenterInRangeIsLoadedForTheRefund() {
		TrainingRefund.Choice choice = TrainingRefund.choose(true, List.of(center(1L, false, 100.0)), RADIUS_SQR);
		assertEquals(1L, choice.pos());
		assertTrue(choice.loadChunk());
	}

	@Test
	void loadedCenterIsPaidWithoutLoading() {
		TrainingRefund.Choice choice = TrainingRefund.choose(true, List.of(center(4L, true, 100.0)), RADIUS_SQR);
		assertEquals(4L, choice.pos());
		assertFalse(choice.loadChunk());
	}

	@Test
	void loadedCenterBeatsACloserUnloadedOne() {
		List<TrainingRefund.Center> centers = List.of(center(1L, false, 10.0), center(2L, true, 400.0));
		TrainingRefund.Choice choice = TrainingRefund.choose(true, centers, RADIUS_SQR);
		assertEquals(2L, choice.pos());
		assertFalse(choice.loadChunk());
	}

	@Test
	void nearerUnloadedCenterWinsWhenNoneIsLoaded() {
		List<TrainingRefund.Center> centers = List.of(center(1L, false, 400.0), center(2L, false, 10.0));
		assertEquals(2L, TrainingRefund.choose(true, centers, RADIUS_SQR).pos());
	}

	@Test
	void centerOnTheRadiusCountsAndJustOutsideDoesNot() {
		assertEquals(7L, TrainingRefund.choose(true, List.of(center(7L, false, RADIUS_SQR)), RADIUS_SQR).pos());
		assertNull(TrainingRefund.choose(true, List.of(center(8L, false, RADIUS_SQR + 1.0)), RADIUS_SQR));
		assertNull(TrainingRefund.choose(true, List.of(), RADIUS_SQR));
	}

	@Test
	void equalDistanceKeepsTheLaterCenter() {
		List<TrainingRefund.Center> centers = List.of(center(1L, false, 10.0), center(2L, false, 10.0));
		assertEquals(2L, TrainingRefund.choose(true, centers, RADIUS_SQR).pos());
	}

	private static TrainingRefund.Center center(long pos, boolean loaded, double distSqr) {
		return new TrainingRefund.Center(pos, loaded, distSqr);
	}
}
