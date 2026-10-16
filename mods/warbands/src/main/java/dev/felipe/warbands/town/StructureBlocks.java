package dev.felipe.warbands.town;

import com.mojang.serialization.Codec;
import dev.felipe.warbands.registry.ModAttachments;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blocks placed by a blueprint. Breaking one gives nothing back, so buildings can't be
 * mined for their materials. Container contents (e.g. a barrel you filled) still drop.
 * Once broken, the position is forgotten: whatever the player builds there later drops normally.
 */
public final class StructureBlocks {
	/** Holder for the set of protected positions (BlockPos.asLong). */
	public static final class Data {
		public static final Codec<Data> CODEC = Codec.LONG_STREAM.xmap(
				stream -> new Data(new LongOpenHashSet(stream.toArray())),
				data -> java.util.Arrays.stream(data.positions.toLongArray()));

		private final LongSet positions;

		public Data() {
			this(new LongOpenHashSet());
		}

		private Data(LongSet positions) {
			this.positions = positions;
		}
	}

	private StructureBlocks() {
	}

	public static boolean contains(ServerLevel level, BlockPos pos) {
		return data(level).positions.contains(pos.asLong());
	}

	/** How many protected positions are inside the box from {@code min} to {@code max}. */
	public static int countIn(ServerLevel level, BlockPos min, BlockPos max) {
		return countIn(level, min, max, Integer.MAX_VALUE);
	}

	/**
	 * As {@link #countIn}, but stops once the comparison with {@code stopAt} is settled: when {@code stopAt} positions
	 * are found, or when the positions left in the box can no longer reach it. A return value {@code >= stopAt} means
	 * the real count is {@code >= stopAt}; below it means the real count is below it too. Callers that only ask
	 * "are at least N still there" pass N and leave early on a standing or a hopeless building.
	 */
	public static int countIn(ServerLevel level, BlockPos min, BlockPos max, int stopAt) {
		LongSet set = data(level).positions;
		long left = (long) (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
		int count = 0;
		for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
			if (set.contains(pos.asLong()) && ++count >= stopAt) {
				return count;
			}
			if (stopAt < Integer.MAX_VALUE && --left + count < stopAt) {
				return count;
			}
		}
		return count;
	}

	public static void protect(ServerLevel level, Iterable<BlockPos> positions) {
		LongSet set = data(level).positions;
		for (BlockPos pos : positions) {
			set.add(pos.asLong());
		}
		markDirty(level, set);
	}

	/** Forgets {@code pos}. Returns true if it was a structure block. */
	public static boolean release(ServerLevel level, BlockPos pos) {
		LongSet set = data(level).positions;
		if (!set.remove(pos.asLong())) {
			return false;
		}
		markDirty(level, set);
		return true;
	}

	/** Fabric PlayerBlockBreakEvents.BEFORE handler: breaks structure blocks without drops. */
	public static boolean beforeBreak(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
		if (!(level instanceof ServerLevel serverLevel) || !release(serverLevel, pos) || player.isCreative()) {
			return true;
		}
		serverLevel.destroyBlock(pos, false, player, 512);
		return false;
	}

	private static Data data(ServerLevel level) {
		return level.getAttachedOrCreate(ModAttachments.STRUCTURE_BLOCKS, Data::new);
	}

	private static void markDirty(ServerLevel level, LongSet set) {
		// A fresh holder around the same set, so the attachment is flagged as changed and saved.
		level.setAttached(ModAttachments.STRUCTURE_BLOCKS, new Data(set));
	}
}
