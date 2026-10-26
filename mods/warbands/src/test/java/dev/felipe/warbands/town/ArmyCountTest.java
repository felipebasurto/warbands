package dev.felipe.warbands.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The cap rule a loaded Town Center applies. No world is started: chunk load, the army registry, and the
 * training queue are not covered here.
 */
class ArmyCountTest {
	@Test
	void unreadZeroIsTheRegisteredArmy() {
		assertEquals(8, ArmyCount.living(false, 0, 8));
		assertTrue(ArmyCount.atCap(ArmyCount.living(false, 0, 8), 0, 5));
	}

	@Test
	void countedEmptyArmyStaysEmpty() {
		assertEquals(0, ArmyCount.living(true, 0, 8));
		assertFalse(ArmyCount.atCap(0, 0, 5));
	}

	@Test
	void queuedUnitsShareTheCap() {
		assertFalse(ArmyCount.atCap(4, 0, 5));
		assertTrue(ArmyCount.atCap(4, 1, 5));
		assertTrue(ArmyCount.atCap(5, 0, 5));
	}
}
