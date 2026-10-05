package dev.felipe.warbands.town;

import com.mojang.serialization.Codec;
import dev.felipe.warbands.entity.Stance;
import dev.felipe.warbands.registry.ModAttachments;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;

/**
 * The order each commander's whole army is under (Follow, Hold or Attack), set by the War Horn. Soldiers check it
 * every second, so ones far away, in unloaded chunks, or trained after the horn sounded all end up under the same
 * order. Soldiers posted on a Watch Tower keep their post.
 */
public final class ArmyOrders {
	public record Data(Map<UUID, String> orders) {
		public static final Data EMPTY = new Data(Map.of());
		public static final Codec<Data> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING).xmap(Data::new, Data::orders);
	}

	private ArmyOrders() {
	}

	public static Stance get(MinecraftServer server, UUID owner) {
		String name = data(server).orders().get(owner);
		return name == null ? Stance.FOLLOW : Stance.byName(name);
	}

	public static void set(MinecraftServer server, UUID owner, Stance stance) {
		Map<UUID, String> copy = new HashMap<>(data(server).orders());
		copy.put(owner, stance.serializedName());
		server.overworld().setAttached(ModAttachments.ARMY_ORDERS, new Data(Map.copyOf(copy)));
	}

	private static Data data(MinecraftServer server) {
		return server.overworld().getAttachedOrCreate(ModAttachments.ARMY_ORDERS);
	}
}
