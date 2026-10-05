package dev.felipe.warbands;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Where the ground really is, and whether the world around a spot is ready. A heightmap read on a chunk that isn't
 * loaded answers "the bottom of the world" (that is how players got teleported into the void), and the
 * MOTION_BLOCKING heightmap counts tree trunks (that is how a camp or building ended up on a treetop).
 */
public final class Ground {
	private Ground() {
	}

	/**
	 * The top real ground block of a column: trees, leaves, plants and snow layers don't count. Loads (and generates,
	 * if needed) the chunk first, so only call it where waiting for one chunk is fine (commands, teleports).
	 */
	public static int surface(ServerLevel level, int x, int z) {
		level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
		return loadedSurface(level, x, z);
	}

	/** As {@link #surface}, but never loads a chunk: {@link Integer#MIN_VALUE} if the column isn't loaded. */
	public static int surfaceIfLoaded(ServerLevel level, int x, int z) {
		if (!level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
			return Integer.MIN_VALUE;
		}
		return loadedSurface(level, x, z);
	}

	private static int loadedSurface(ServerLevel level, int x, int z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x,
				level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
		while (pos.getY() > level.getMinY()) {
			BlockState state = level.getBlockState(pos);
			boolean notGround = state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)
					|| state.canBeReplaced() && state.getFluidState().isEmpty();
			if (!notGround) {
				break;
			}
			pos.move(0, -1, 0);
		}
		return pos.getY();
	}

	/**
	 * The floor for a building of this half-width: the median ground over a 5x5 sample of its footprint, so one tree,
	 * pit or boulder under the center doesn't lift or sink the whole thing. Columns that aren't loaded are skipped;
	 * returns {@link Integer#MIN_VALUE} if none are.
	 */
	public static int footprint(ServerLevel level, BlockPos center, int half) {
		List<Integer> heights = new ArrayList<>();
		for (int i = -2; i <= 2; i++) {
			for (int j = -2; j <= 2; j++) {
				int y = surfaceIfLoaded(level, center.getX() + i * half / 2, center.getZ() + j * half / 2);
				if (y != Integer.MIN_VALUE) {
					heights.add(y);
				}
			}
		}
		if (heights.isEmpty()) {
			return Integer.MIN_VALUE;
		}
		heights.sort(null);
		return heights.get(heights.size() / 2);
	}

	/**
	 * Every chunk touching the square of {@code radius} blocks around {@code center} is loaded with its entities and
	 * ticking. Build and spawn only then: blocks placed at the edge of loading drift out of sync with the ground next
	 * to them, and mobs added to a chunk that isn't ticking freeze or vanish.
	 */
	public static boolean ready(ServerLevel level, BlockPos center, int radius) {
		int minX = SectionPos.blockToSectionCoord(center.getX() - radius);
		int maxX = SectionPos.blockToSectionCoord(center.getX() + radius);
		int minZ = SectionPos.blockToSectionCoord(center.getZ() - radius);
		int maxZ = SectionPos.blockToSectionCoord(center.getZ() + radius);
		for (int cx = minX; cx <= maxX; cx++) {
			for (int cz = minZ; cz <= maxZ; cz++) {
				if (!level.areEntitiesActuallyLoadedAndTicking(new ChunkPos(cx, cz))) {
					return false;
				}
			}
		}
		return true;
	}
}
