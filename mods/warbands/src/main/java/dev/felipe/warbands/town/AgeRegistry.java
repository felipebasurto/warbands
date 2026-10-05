package dev.felipe.warbands.town;

import com.mojang.serialization.Codec;
import dev.felipe.warbands.registry.ModAttachments;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;

/**
 * The highest age each player has reached with any of their Town Centers. Soldiers read it to
 * get their age bonuses, so it's global (stored on the overworld) and never goes down.
 */
public final class AgeRegistry {
	public record Data(Map<UUID, String> ages) {
		public static final Data EMPTY = new Data(Map.of());
		public static final Codec<Data> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING)
				.xmap(Data::new, Data::ages);
	}

	private AgeRegistry() {
	}

	public static Age get(MinecraftServer server, UUID owner) {
		String name = data(server).ages().get(owner);
		return name == null ? Age.VILLAGE : Age.byName(name);
	}

	/** Records {@code age} for {@code owner} if it's higher than what they had. */
	public static void raise(MinecraftServer server, UUID owner, Age age) {
		if (age.ordinal() <= get(server, owner).ordinal()) {
			return;
		}
		Map<UUID, String> copy = new HashMap<>(data(server).ages());
		copy.put(owner, age.serializedName());
		server.overworld().setAttached(ModAttachments.AGES, new Data(Map.copyOf(copy)));
	}

	private static Data data(MinecraftServer server) {
		return server.overworld().getAttachedOrCreate(ModAttachments.AGES);
	}
}
