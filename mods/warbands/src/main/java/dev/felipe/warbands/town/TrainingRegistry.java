package dev.felipe.warbands.town;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;

/**
 * Where each kind of training building is and who owns it, per dimension. Their queues share the
 * Town Center's population cap, so a building in an unloaded chunk still reserves a full queue.
 */
public final class TrainingRegistry {
	public record Entry(BlockPos pos, UUID owner) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
				UUIDUtil.CODEC.fieldOf("owner").forGetter(Entry::owner)).apply(i, Entry::new));
	}

	public record Data(List<Entry> entries) {
		public static final Data EMPTY = new Data(List.of());
		public static final Codec<Data> CODEC = Entry.CODEC.listOf().xmap(Data::new, Data::entries);
	}

	private TrainingRegistry() {
	}

	public static void add(ServerLevel level, TrainingSite site, BlockPos pos, UUID owner) {
		List<Entry> entries = new ArrayList<>(level.getAttachedOrCreate(site.attachment()).entries());
		entries.removeIf(entry -> entry.pos().equals(pos));
		entries.add(new Entry(pos.immutable(), owner));
		level.setAttached(site.attachment(), new Data(List.copyOf(entries)));
	}

	public static void remove(ServerLevel level, TrainingSite site, BlockPos pos) {
		List<Entry> entries = new ArrayList<>(level.getAttachedOrCreate(site.attachment()).entries());
		if (entries.removeIf(entry -> entry.pos().equals(pos))) {
			level.setAttached(site.attachment(), new Data(List.copyOf(entries)));
		}
	}

	/** Units already queued at this owner's training buildings of every kind near {@code center}. */
	public static int queuedNear(ServerLevel level, BlockPos center, int radius, UUID owner) {
		int queued = 0;
		for (TrainingSite site : TrainingSite.buildings()) {
			queued += queuedNear(level, site, center, radius, owner);
		}
		return queued;
	}

	private static int queuedNear(ServerLevel level, TrainingSite site, BlockPos center, int radius, UUID owner) {
		int queued = 0;
		double maxDistSqr = (double) radius * radius;
		for (Entry entry : level.getAttachedOrCreate(site.attachment()).entries()) {
			if (!entry.owner().equals(owner) || entry.pos().distSqr(center) > maxDistSqr) {
				continue;
			}
			if (!level.isLoaded(entry.pos())) {
				queued += TownCenterBlockEntity.MAX_QUEUE;
				continue;
			}
			if (!(level.getBlockEntity(entry.pos()) instanceof TrainingBlockEntity building) || building.site() != site) {
				remove(level, site, entry.pos());
				continue;
			}
			queued += building.queueSize();
		}
		return queued;
	}
}
