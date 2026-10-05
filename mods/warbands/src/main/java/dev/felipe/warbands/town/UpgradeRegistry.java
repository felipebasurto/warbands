package dev.felipe.warbands.town;

import com.mojang.serialization.Codec;
import dev.felipe.warbands.entity.Gear;
import dev.felipe.warbands.entity.SoldierEntity;
import dev.felipe.warbands.registry.ModAttachments;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * Which Feudal upgrades each commander has bought. Stored on the overworld, like the army,
 * so a soldier trained later, or loaded in another dimension, still receives them.
 */
public final class UpgradeRegistry {
	public record Data(Map<UUID, List<Upgrade>> owned) {
		public static final Data EMPTY = new Data(Map.of());
		public static final Codec<Data> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Upgrade.CODEC.listOf())
				.xmap(Data::new, Data::owned);
	}

	private UpgradeRegistry() {
	}

	public static boolean has(MinecraftServer server, UUID owner, Upgrade upgrade) {
		return data(server).owned().getOrDefault(owner, List.of()).contains(upgrade);
	}

	public static int mask(MinecraftServer server, UUID owner) {
		int mask = 0;
		for (Upgrade upgrade : data(server).owned().getOrDefault(owner, List.of())) {
			mask |= 1 << upgrade.ordinal();
		}
		return mask;
	}

	public static void grant(MinecraftServer server, UUID owner, Upgrade upgrade) {
		ServerLevel overworld = server.overworld();
		Map<UUID, List<Upgrade>> copy = new HashMap<>(data(server).owned());
		List<Upgrade> owned = new ArrayList<>(copy.getOrDefault(owner, List.of()));
		if (!owned.contains(upgrade)) {
			owned.add(upgrade);
			copy.put(owner, List.copyOf(owned));
			overworld.setAttached(ModAttachments.UPGRADES, new Data(Map.copyOf(copy)));
		}
		applyLoaded(server, owner);
	}

	/** Pushes the owner's current upgrades onto soldiers that are loaded right now. */
	public static void applyLoaded(MinecraftServer server, UUID owner) {
		for (UUID id : ArmyRegistry.ids(server, owner)) {
			Entity entity = server.overworld().getEntityInAnyDimension(id);
			if (entity instanceof SoldierEntity soldier) {
				Gear.apply(soldier);
			}
		}
	}

	private static Data data(MinecraftServer server) {
		return server.overworld().getAttachedOrCreate(ModAttachments.UPGRADES);
	}
}
