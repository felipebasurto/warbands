package dev.felipe.warbands.camp;

/**
 * Who may claim a camp standard once its guards are dead. The owner always may: that is the single-player path, and
 * it does not depend on the fighter list. Another player may claim only for a commander who is offline, and only if
 * that player fought this camp and is standing beside the banner (not spectating).
 */
public final class CampClaim {
	private CampClaim() {
	}

	public static boolean mayClaim(boolean isOwner, boolean ownerOnline, boolean spectator, boolean withinPartyRange,
			boolean fought) {
		if (isOwner) {
			return true;
		}
		return !ownerOnline && !spectator && withinPartyRange && fought;
	}
}
