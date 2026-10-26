package dev.felipe.warbands.camp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Arrival at an unbuilt camp. Refunds and freeing the slot are outside this choice. */
class CampArrivalTest {
	@Test
	void chestOnAnUnbuiltSiteHoldsTheCamp() {
		assertEquals(CampArrival.Action.HOLD, CampArrival.decide(true));
	}

	@Test
	void openSiteBuilds() {
		assertEquals(CampArrival.Action.BUILD, CampArrival.decide(false));
	}
}
