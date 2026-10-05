package dev.felipe.warbands;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Ways of telling a player where something is. Playtesters don't know which way is north, so hints use an arrow
 * relative to where the player is looking (↑ ahead, → to the right); compass words are kept only for chat, where
 * the player may have turned since.
 */
public final class Directions {
	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
	private static final String[] NAMES = {"north", "north_east", "east", "south_east", "south", "south_west", "west", "north_west"};

	private Directions() {
	}

	/** An arrow from the player's point of view pointing at {@code target}. */
	public static String arrow(Player player, BlockPos target) {
		double dx = target.getX() + 0.5 - player.getX();
		double dz = target.getZ() + 0.5 - player.getZ();
		// Minecraft yaw: 0 faces south (+z), 90 faces west (-x), growing clockwise seen from above.
		float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float relative = Mth.wrapDegrees(targetYaw - player.getYRot());
		return ARROWS[Math.floorMod(Math.round(relative / 45.0F), 8)];
	}

	/** "north-east" and so on, from {@code from} to {@code to}. */
	public static Component compass(BlockPos from, BlockPos to) {
		double angle = Math.toDegrees(Math.atan2(to.getX() - from.getX(), from.getZ() - to.getZ()));
		return Component.translatable("direction.warbands." + NAMES[Math.floorMod((int) Math.round(angle / 45.0), 8)]);
	}

	/** Horizontal distance in whole blocks. */
	public static int distance(BlockPos from, BlockPos to) {
		return (int) Math.sqrt(Math.pow(to.getX() - from.getX(), 2) + Math.pow(to.getZ() - from.getZ(), 2));
	}

	/** A big title on the player's screen, for moments that matter (camp cleared, raid won). */
	public static void title(ServerPlayer player, Component title, Component subtitle) {
		player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
		player.connection.send(new ClientboundSetTitleTextPacket(title.copy().withStyle(ChatFormatting.GOLD)));
	}
}
