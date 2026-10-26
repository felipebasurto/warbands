package dev.felipe.warbands.camp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Nearby sites for a blocked camp. Terrain acceptance is the War Map site search, not this list. */
class CampRelocationTest {
	@Test
	void nearestOffsetsClearTheOldFootprint() {
		int half = 7;
		List<CampRelocation.Offset> offsets = CampRelocation.offsets(half, 96, 16);
		assertTrue(offsets.size() > 1);
		CampRelocation.Offset first = offsets.get(0);
		assertEquals(16 * 16, first.dx() * first.dx() + first.dz() * first.dz());
		int previous = 0;
		for (CampRelocation.Offset offset : offsets) {
			int chebyshev = Math.max(Math.abs(offset.dx()), Math.abs(offset.dz()));
			assertTrue(chebyshev > half * 2, offset.toString());
			assertTrue(chebyshev <= 96, offset.toString());
			int distance = offset.dx() * offset.dx() + offset.dz() * offset.dz();
			assertTrue(distance >= previous, offset.toString());
			previous = distance;
		}
	}

	@Test
	void largeCampStartsOutsideItsOwnWalls() {
		int half = 22;
		List<CampRelocation.Offset> offsets = CampRelocation.offsets(half);
		assertEquals(48, Math.max(Math.abs(offsets.get(0).dx()), Math.abs(offsets.get(0).dz())));
		for (CampRelocation.Offset offset : offsets) {
			assertTrue(Math.max(Math.abs(offset.dx()), Math.abs(offset.dz())) > half * 2);
			assertTrue(Math.max(Math.abs(offset.dx()), Math.abs(offset.dz())) <= CampRelocation.RADIUS);
		}
	}

	@Test
	void noRoomInsideTheSearchReturnsNothing() {
		assertTrue(CampRelocation.offsets(7, 8, 16).isEmpty());
	}
}
