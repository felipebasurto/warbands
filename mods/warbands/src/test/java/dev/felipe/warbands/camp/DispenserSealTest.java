package dev.felipe.warbands.camp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The pull rule. No world is started: a hopper, a dispenser block entity, and {@code Camps.campAt} are not covered.
 */
class DispenserSealTest {
	@Test
	void hopperCannotPullFromACampDispenser() {
		assertFalse(DispenserSeal.hopperMayPull(true, true));
	}

	@Test
	void hopperCanPullFromADispenserOutsideACamp() {
		assertTrue(DispenserSeal.hopperMayPull(true, false));
	}

	@Test
	void hopperCanPullFromOtherBlocks() {
		assertTrue(DispenserSeal.hopperMayPull(false, true));
		assertTrue(DispenserSeal.hopperMayPull(false, false));
	}
}
