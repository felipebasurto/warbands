package dev.felipe.warbands.camp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The claim gate. Guards-alive, loot and persistence are outside this check. */
class CampClaimTest {
	@Test
	void ownerClaimsWithoutBeingRecordedAsAFighter() {
		assertTrue(CampClaim.mayClaim(true, true, false, true, false));
		assertTrue(CampClaim.mayClaim(true, false, false, false, false));
		assertTrue(CampClaim.mayClaim(true, true, true, false, false));
	}

	@Test
	void bystanderBesideAnAbsentCommanderCannotClaim() {
		assertFalse(CampClaim.mayClaim(false, false, false, true, false));
	}

	@Test
	void fighterBesideAnAbsentCommanderCanClaim() {
		assertTrue(CampClaim.mayClaim(false, false, false, true, true));
	}

	@Test
	void onlineCommanderKeepsTheClaimEvenFromSomeoneWhoFought() {
		assertFalse(CampClaim.mayClaim(false, true, false, true, true));
	}

	@Test
	void fighterOutOfRangeOrSpectatingCannotClaim() {
		assertFalse(CampClaim.mayClaim(false, false, false, false, true));
		assertFalse(CampClaim.mayClaim(false, false, true, true, true));
	}
}
