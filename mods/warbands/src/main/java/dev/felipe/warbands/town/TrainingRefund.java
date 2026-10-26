package dev.felipe.warbands.town;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Which Town Center is paid when a training building is broken. The nearest loaded center in range is the one
 * training spent from. If none is loaded, the nearest center still in range is loaded so the refund is not dropped
 * with the building. Nothing owed, or no center in range, is nowhere to pay.
 */
final class TrainingRefund {
	/**
	 * A center the caller already checked. A loaded one is still a Town Center. An unloaded one is only a registry
	 * position: its chunk is not loaded, so the block cannot be read yet.
	 */
	record Center(long pos, boolean loaded, double distSqr) {
	}

	/** {@code loadChunk} when the center's chunk has to be loaded before the stockpile can be paid. */
	record Choice(long pos, boolean loadChunk) {
	}

	private TrainingRefund() {
	}

	static @Nullable Choice choose(boolean owed, List<Center> centers, double radiusSqr) {
		if (!owed) {
			return null;
		}
		Choice loaded = nearest(centers, radiusSqr, true);
		if (loaded != null) {
			return loaded;
		}
		return nearest(centers, radiusSqr, false);
	}

	/**
	 * The closest center with this loaded-ness within {@code radiusSqr}. A tie keeps the later one, as
	 * {@code TownRegistry.nearestOwned} does.
	 */
	private static @Nullable Choice nearest(List<Center> centers, double radiusSqr, boolean loaded) {
		long best = 0L;
		double bestDist = radiusSqr;
		boolean found = false;
		for (Center center : centers) {
			if (center.loaded() != loaded || center.distSqr() > bestDist) {
				continue;
			}
			best = center.pos();
			bestDist = center.distSqr();
			found = true;
		}
		return found ? new Choice(best, !loaded) : null;
	}
}
