package dev.felipe.warbands.camp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Which hits spare a camp's own guards. No world is started: dispensers are not fired, kegs are not lit,
 * and nobody is actually damaged.
 */
class TrapDamageTest {
	@Test
	void thisCampsGunSparesItsGuard() {
		assertTrue(TrapDamage.sparesOwnGuard(true, true, true, false));
	}

	@Test
	void thisCampsPowderKegSparesItsGuard() {
		assertTrue(TrapDamage.sparesOwnGuard(true, false, false, true));
	}

	@Test
	void playerDispenserStillHurtsGuards() {
		assertFalse(TrapDamage.sparesOwnGuard(true, true, false, false));
	}

	@Test
	void tntStillHurtsGuards() {
		assertFalse(TrapDamage.sparesOwnGuard(true, false, false, false));
	}

	@Test
	void playersAndEnemiesStillTakeCampGunsAndKegs() {
		assertFalse(TrapDamage.sparesOwnGuard(false, true, true, false));
		assertFalse(TrapDamage.sparesOwnGuard(false, false, false, true));
	}
}
