package dev.felipe.warbands.camp;

/**
 * What to do with an unbuilt camp once a player is near and the footprint is ticking.
 * A block entity there (a chest on the site) moves the camp to another clear site when one is nearby.
 * The cost stays paid and the slot stays taken. With nowhere to move, the camp waits.
 */
final class CampArrival {
	enum Action {
		BUILD, RELOCATE, HOLD
	}

	private CampArrival() {
	}

	static Action decide(boolean footprintBlocked, boolean siteFound) {
		if (!footprintBlocked) {
			return Action.BUILD;
		}
		return siteFound ? Action.RELOCATE : Action.HOLD;
	}
}
