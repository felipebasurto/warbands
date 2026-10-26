package dev.felipe.warbands.camp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Who a camp gun or a powder keg is allowed to hurt. No world is started: dispensers, explosions,
 * and the damage event are not covered here.
 */
class TrapDamageTest {
	@Test
	void campGunSparesOwnGuardEvenThoughTheArrowIsNamedAsTheAttacker() {
		assertTrue(TrapDamage.sparesOwnGuard(true, true, false, true, true, false));
	}

	@Test
	void powderKegSparesOwnGuard() {
		assertTrue(TrapDamage.sparesOwnGuard(true, false, false, false, false, true));
	}

	@Test
	void playersAndEnemiesStillTakeGunAndKegDamage() {
		assertFalse(TrapDamage.sparesOwnGuard(false, true, false, true, true, false));
		assertFalse(TrapDamage.sparesOwnGuard(false, false, false, false, false, true));
	}

	@Test
	void guardsStillTakeAimedShotsAndBlastsThatNameSomeone() {
		assertFalse(TrapDamage.sparesOwnGuard(true, true, true, true, true, false));
		assertFalse(TrapDamage.sparesOwnGuard(true, false, false, true, true, false));
		assertFalse(TrapDamage.sparesOwnGuard(true, false, false, true, true, true));
		assertFalse(TrapDamage.sparesOwnGuard(true, false, false, false, true, true));
	}
}
