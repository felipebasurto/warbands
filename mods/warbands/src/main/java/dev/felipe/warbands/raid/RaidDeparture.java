package dev.felipe.warbands.raid;

import net.minecraft.world.entity.Entity.RemovalReason;

/**
 * Which removals take a raider off the raid's count. Death is counted from the raider's {@code die} method.
 * A portal is not a death: the entity is removed with {@link RemovalReason#CHANGED_DIMENSION} and the copy
 * lives in another dimension, so it has to leave the count or the raid cannot be won. Unloading does not
 * (a raider in an unloaded chunk is still alive), and neither does discard (that is how a finished raid
 * sends the survivors home).
 */
public final class RaidDeparture {
	private RaidDeparture() {
	}

	public static boolean dropsFromCount(RemovalReason reason) {
		return reason == RemovalReason.CHANGED_DIMENSION;
	}
}
