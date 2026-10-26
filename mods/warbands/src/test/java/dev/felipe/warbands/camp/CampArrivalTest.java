package dev.felipe.warbands.camp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Arrival at an unbuilt camp. Paying the cost back and freeing the slot are outside this choice. */
class CampArrivalTest {
	@Test
	void openSiteBuilds() {
		assertEquals(CampArrival.Action.BUILD, CampArrival.decide(false, false));
		assertEquals(CampArrival.Action.BUILD, CampArrival.decide(false, true));
	}

	@Test
	void blockedSiteMovesWhenAnotherSpotExists() {
		assertEquals(CampArrival.Action.RELOCATE, CampArrival.decide(true, true));
	}

	@Test
	void blockedSiteHoldsWhenNowhereIsClear() {
		assertEquals(CampArrival.Action.HOLD, CampArrival.decide(true, false));
	}
}
