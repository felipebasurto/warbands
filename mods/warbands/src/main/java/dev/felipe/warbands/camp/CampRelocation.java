package dev.felipe.warbands.camp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Where to look when an unbuilt camp's footprint is blocked. Offsets are nearest first, outside the old footprint,
 * and no farther than {@link #RADIUS}. The same terrain rules as a new War Map decide which of these spots are real.
 */
final class CampRelocation {
	/** Far enough to leave the blocked footprint, near enough that the walk stays short. */
	static final int RADIUS = 96;
	/** Spacing of the square rings. Tighter than this spends the height probes a site search already calls expensive. */
	static final int STEP = 16;

	record Offset(int dx, int dz) {
	}

	private CampRelocation() {
	}

	static List<Offset> offsets(int half) {
		return offsets(half, RADIUS, STEP);
	}

	/** Chebyshev rings of {@code step}, from the first ring past the old footprint out to {@code radius}. */
	static List<Offset> offsets(int half, int radius, int step) {
		int min = half * 2 + 1;
		if (step < 1 || radius < min) {
			return List.of();
		}
		List<Offset> found = new ArrayList<>();
		for (int r = step; r <= radius; r += step) {
			if (r < min) {
				continue;
			}
			for (int dx = -r; dx <= r; dx += step) {
				found.add(new Offset(dx, -r));
				found.add(new Offset(dx, r));
			}
			for (int dz = -r + step; dz <= r - step; dz += step) {
				found.add(new Offset(-r, dz));
				found.add(new Offset(r, dz));
			}
		}
		found.sort(Comparator.comparingInt(offset -> offset.dx() * offset.dx() + offset.dz() * offset.dz()));
		return List.copyOf(found);
	}
}
