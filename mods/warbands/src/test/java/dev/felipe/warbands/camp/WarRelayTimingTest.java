package dev.felipe.warbands.camp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WarRelayTimingTest {
	@Test
	void standingCampChecksEveryStep() {
		assertEquals(10, WarRelayTiming.nextDelay(true, false, 10));
		assertEquals(10, WarRelayTiming.nextDelay(true, true, 10));
	}

	@Test
	void campNotRegisteredYetKeepsTheSlowCheck() {
		assertEquals(100, WarRelayTiming.nextDelay(false, true, 10));
	}

	@Test
	void goneCampStops() {
		assertEquals(-1, WarRelayTiming.nextDelay(false, false, 10));
	}

	@Test
	void footprintMatchesTheCampMargin() {
		assertTrue(WarRelayTiming.inFootprint(0, 0, 0, 7, 4));
		assertTrue(WarRelayTiming.inFootprint(11, -6, -11, 7, 4));
		assertTrue(WarRelayTiming.inFootprint(0, 10, 0, 7, 4));
		assertFalse(WarRelayTiming.inFootprint(12, 0, 0, 7, 4));
		assertFalse(WarRelayTiming.inFootprint(0, -7, 0, 7, 4));
		assertFalse(WarRelayTiming.inFootprint(0, 11, 0, 7, 4));
	}
}
