package dev.felipe.warbands.blueprint;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import dev.felipe.warbands.town.StructureBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Clears unsupported plants and trees without guessing which nearby item drops belong to the terrain. */
public final class SiteCleanup {
	/** No neighbour updates and no shape updates: nothing next to a cleared block is told to break itself. */
	private static final int SILENT = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
	private static final int PLANT_MARGIN = 3;
	public static final int TREE_MARGIN = 8;
	public static final int OVERHANG = 24;
	private SiteCleanup() {
	}

	/** Runs after a building or camp is placed on {@code center} (floor level). */
	public static void afterBuild(ServerLevel level, BlockPos center, int half, int height) {
		clearNatural(level, center, half, height);
		removeFloatingTrees(level, center, half + TREE_MARGIN, center.getY() + 1, center.getY() + height + OVERHANG);
		removeOrphanLeaves(level, center, half + TREE_MARGIN, center.getY() - 2, center.getY() + height + OVERHANG);
	}

	/** Runs again after the caller changes the terrain around the site (a camp's cleared approach lane). */
	public static void settle(ServerLevel level, BlockPos center, int half, int height) {
		removeFloatingTrees(level, center, half + TREE_MARGIN, center.getY() + 1, center.getY() + height + OVERHANG);
		removeOrphanLeaves(level, center, half + TREE_MARGIN, center.getY() - 2, center.getY() + height + OVERHANG);
	}

	/** Plants and snow around the footprint, and natural terrain, logs and leaves hanging over it above the building. */
	private static void clearNatural(ServerLevel level, BlockPos center, int half, int height) {
		int reach = half + PLANT_MARGIN;
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				boolean inside = Math.abs(dx) <= half && Math.abs(dz) <= half;
				BlockPos column = center.offset(dx, 0, dz);
				if (!level.hasChunkAt(column)) {
					continue;
				}
				for (int dy = 1; dy <= height + OVERHANG; dy++) {
					BlockPos pos = column.above(dy);
					BlockState state = level.getBlockState(pos);
					if (state.isAir() || !state.getFluidState().isEmpty()) {
						continue;
					}
					boolean plant = state.canBeReplaced();
					boolean overhang = inside && dy >= height
							&& (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(BlueprintPlacer.CLEARABLE));
					if ((plant || overhang) && !state.hasBlockEntity()) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), SILENT);
					}
				}
			}
		}
	}

	/**
	 * Clearing the terrain can leave a tree standing on nothing: its trunk (acacia ones lean, so diagonals count) hangs in
	 * the air above the cleared ground. Logs not connected to anything solid below are removed with their leaves, silently.
	 */
	private static void removeFloatingTrees(ServerLevel level, BlockPos center, int reach, int minY, int maxY) {
		Set<BlockPos> seen = new HashSet<>();
		for (int x = -reach; x <= reach; x++) {
			for (int z = -reach; z <= reach; z++) {
				if (!level.hasChunkAt(center.offset(x, 0, z))) {
					continue;
				}
				for (int y = minY; y <= maxY; y++) {
					BlockPos start = new BlockPos(center.getX() + x, y, center.getZ() + z);
					if (seen.contains(start) || !level.getBlockState(start).is(BlockTags.LOGS)) {
						continue;
					}
					List<BlockPos> tree = new ArrayList<>();
					boolean grounded = false;
					ArrayDeque<BlockPos> queue = new ArrayDeque<>();
					queue.add(start);
					seen.add(start);
					while (!queue.isEmpty() && tree.size() < 600) {
						BlockPos pos = queue.poll();
						tree.add(pos);
						BlockState below = level.getBlockState(pos.below());
						if (StructureBlocks.contains(level, pos) // part of a building: never ours to remove
								|| !below.isAir() && !below.is(BlockTags.LEAVES) && !below.is(BlockTags.LOGS) && below.blocksMotion()) {
							grounded = true;
						}
						for (BlockPos near : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
							if (level.hasChunkAt(near) && level.getBlockState(near).is(BlockTags.LOGS) && seen.add(near.immutable())) {
								queue.add(near.immutable());
							}
						}
					}
					if (!grounded && tree.size() < 600) {
						for (BlockPos pos : tree) {
							level.setBlock(pos, Blocks.AIR.defaultBlockState(), SILENT);
						}
					}
				}
			}
		}
	}

	/**
	 * Deletes every non-persistent leaf that decay would delete: not within 6 steps of a log through other leaves.
	 * Logs up to 7 blocks beyond the region count, so a crown attached to a trunk just outside is left alone.
	 */
	private static void removeOrphanLeaves(ServerLevel level, BlockPos center, int reach, int minY, int maxY) {
		for (BlockPos pos : orphanLeaves(level, center, reach, minY, maxY)) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), SILENT);
		}
	}

	/** The non-persistent leaves in the box that decay would delete (and drop saplings, sticks and apples for). */
	public static List<BlockPos> orphanLeaves(ServerLevel level, BlockPos center, int reach, int minY, int maxY) {
		int seed = reach + LeavesBlock.DECAY_DISTANCE;
		Map<BlockPos, Integer> distance = new HashMap<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		for (int x = -seed; x <= seed; x++) {
			for (int z = -seed; z <= seed; z++) {
				BlockPos column = new BlockPos(center.getX() + x, 0, center.getZ() + z);
				if (!level.hasChunkAt(column)) {
					continue;
				}
				for (int y = minY; y <= maxY; y++) {
					BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
					if (level.getBlockState(pos).is(BlockTags.LOGS)) {
						distance.put(pos, 0);
						queue.add(pos);
					}
				}
			}
		}
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			int next = distance.get(pos) + 1;
			if (next > LeavesBlock.DECAY_DISTANCE - 1) {
				continue;
			}
			for (BlockPos neighbour : new BlockPos[] {pos.above(), pos.below(), pos.north(), pos.south(), pos.east(), pos.west()}) {
				if (!distance.containsKey(neighbour) && level.hasChunkAt(neighbour)
						&& level.getBlockState(neighbour).is(BlockTags.LEAVES)) {
					distance.put(neighbour, next);
					queue.add(neighbour);
				}
			}
		}
		List<BlockPos> orphans = new ArrayList<>();
		for (int x = -reach; x <= reach; x++) {
			for (int z = -reach; z <= reach; z++) {
				BlockPos column = new BlockPos(center.getX() + x, 0, center.getZ() + z);
				if (!level.hasChunkAt(column)) {
					continue;
				}
				for (int y = minY; y <= maxY; y++) {
					BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
					BlockState state = level.getBlockState(pos);
					if (state.is(BlockTags.LEAVES) && !distance.containsKey(pos)
							&& !(state.hasProperty(LeavesBlock.PERSISTENT) && state.getValue(LeavesBlock.PERSISTENT))) {
						orphans.add(pos);
					}
				}
			}
		}
		return orphans;
	}
}
