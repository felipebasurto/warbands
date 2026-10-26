package dev.felipe.warbands.town;

/**
 * How many soldiers a Town Center counts toward its population cap. The cached count starts unread. That unread
 * zero is not an empty army: the army registry already has the soldiers, loaded or not.
 */
final class ArmyCount {
	private ArmyCount() {
	}

	/**
	 * @param counted false until the town has copied the registry into {@code cached}
	 */
	static int living(boolean counted, int cached, int registered) {
		return counted ? cached : registered;
	}

	/** {@code queued} units already reserve a slot. The town is full when one more would pass {@code cap}. */
	static boolean atCap(int living, int queued, int cap) {
		return living + queued >= cap;
	}
}
