package dev.felipe.warbands.town;

import com.mojang.serialization.Codec;
import dev.felipe.warbands.registry.ModAttachments;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Tracks every living soldier per owner, so population works even when soldiers are far away. */
public final class ArmyRegistry {
	public record Data(Map<UUID, List<UUID>> soldiers) {
		public static final Data EMPTY = new Data(Map.of());
		public static final Codec<Data> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, UUIDUtil.CODEC.listOf())
				.xmap(Data::new, Data::soldiers);
	}

	private ArmyRegistry() {
	}

	public static int count(MinecraftServer server, UUID owner) {
		return ids(server, owner).size();
	}

	public static List<UUID> ids(MinecraftServer server, UUID owner) {
		return data(server).soldiers().getOrDefault(owner, List.of());
	}

	public static void add(MinecraftServer server, UUID owner, UUID soldier) {
		update(server, owner, set -> set.add(soldier));
	}

	public static void remove(MinecraftServer server, UUID owner, UUID soldier) {
		update(server, owner, set -> set.remove(soldier));
	}

	private static Data data(MinecraftServer server) {
		return server.overworld().getAttachedOrCreate(ModAttachments.ARMY);
	}

	private static void update(MinecraftServer server, UUID owner, java.util.function.Predicate<Set<UUID>> change) {
		ServerLevel overworld = server.overworld();
		Data data = data(server);
		Set<UUID> soldiers = new LinkedHashSet<>(data.soldiers().getOrDefault(owner, List.of()));
		if (!change.test(soldiers)) {
			return;
		}
		Map<UUID, List<UUID>> copy = new HashMap<>(data.soldiers());
		if (soldiers.isEmpty()) {
			copy.remove(owner);
		} else {
			copy.put(owner, List.copyOf(soldiers));
		}
		overworld.setAttached(ModAttachments.ARMY, new Data(Map.copyOf(copy)));
	}
}
