package dev.felipe.warbands.raid;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.entity.Entity.RemovalReason;
import org.junit.jupiter.api.Test;

class RaidDepartureTest {
	@Test
	void onlyADimensionChangeDropsTheRaiderFromTheCount() {
		assertTrue(RaidDeparture.dropsFromCount(RemovalReason.CHANGED_DIMENSION));
		for (RemovalReason reason : RemovalReason.values()) {
			if (reason != RemovalReason.CHANGED_DIMENSION) {
				assertFalse(RaidDeparture.dropsFromCount(reason), reason.name());
			}
		}
	}
}
