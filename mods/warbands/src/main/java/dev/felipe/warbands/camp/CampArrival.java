package dev.felipe.warbands.camp;

/**
 * What to do with an unbuilt camp once a player is near and the footprint is ticking.
 * A block entity in that footprint (a chest placed on the site) holds the camp: it is not built over,
 * the map's cost is not returned, and the slot stays taken.
 */
final class CampArrival {
	enum Action {
		BUILD, HOLD
	}

	private CampArrival() {
	}

	static Action decide(boolean footprintBlocked) {
		return footprintBlocked ? Action.HOLD : Action.BUILD;
	}
}
