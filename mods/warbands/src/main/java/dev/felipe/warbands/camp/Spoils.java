package dev.felipe.warbands.camp;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

/**
 * War Chests: named vanilla chests whose loot rolls from a warbands table the first time they're opened (the
 * vanilla lazy-loot mechanism, so the JSON is easy to tune). A claimed camp's chest takes the standard's place;
 * a won raid's stands where the raiders came from, beside a signal campfire whose tall smoke is the landmark.
 */
public final class Spoils {
	/** A chest position with a spot beside it for the signal campfire. */
	public record Site(BlockPos chest, BlockPos fire) {
	}

	private Spoils() {
	}

	/**
	 * A surface spot in the ring {@code minRadius}..{@code maxRadius} around (x, z): air or replaceable over a
	 * sturdy floor, with room beside it for the campfire. Only loaded columns are searched. Null if none fits.
	 */
	public static @Nullable Site site(ServerLevel level, int x, int z, int minRadius, int maxRadius) {
		for (int r = minRadius; r <= maxRadius; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					Site site = siteAt(level, x + dx, z + dz);
					if (site != null) {
						return site;
					}
				}
			}
		}
		return null;
	}

	private static @Nullable Site siteAt(ServerLevel level, int x, int z) {
		if (!level.isLoaded(new BlockPos(x, 0, z))) {
			return null;
		}
		BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
		if (!fits(level, at)) {
			return null;
		}
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			BlockPos beside = at.relative(dir);
			if (fits(level, beside)) {
				return new Site(at, beside);
			}
		}
		return null;
	}

	/** {@code pos} can hold the chest or campfire: replaceable, over a floor that supports it. */
	private static boolean fits(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
				&& (level.getBlockState(pos).isAir() || level.getBlockState(pos).canBeReplaced());
	}

	/** A named chest at {@code pos} whose loot rolls from {@code loot} on first open. */
	public static void chest(ServerLevel level, BlockPos pos, Direction facing, ResourceKey<LootTable> loot, String nameKey) {
		level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), Block.UPDATE_ALL);
		if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
			chest.applyComponents(DataComponentMap.EMPTY, DataComponentPatch.builder()
					.set(DataComponents.CUSTOM_NAME, Component.translatable(nameKey)).build());
			chest.setLootTable(loot, level.getRandom().nextLong());
			chest.setChanged();
		}
	}

	/** Most chests of a camp that hold loot: the layouts have several as decor, but only a couple are stashes. */
	public static final int MAX_CACHES = 2;

	/**
	 * Which stash comes first: closer to the Camp Standard, then the lower packed position so a tie doesn't move
	 * when the footprint is scanned from another corner. Rotation used to stock whichever chests the scan hit first.
	 */
	public static int cacheOrder(BlockPos standard, BlockPos a, BlockPos b) {
		int byDistance = Double.compare(a.distSqr(standard), b.distSqr(standard));
		return byDistance != 0 ? byDistance : Long.compare(a.asLong(), b.asLong());
	}

	/**
	 * Fills up to {@link #MAX_CACHES} of the chests nearest {@code standard} from the kind's cache table. Rolled when
	 * a player first opens one (vanilla loot-table behavior), so in coop the first opener gets it. Breaking one spills
	 * that roll and not the chest block. They burn with the camp.
	 */
	public static int stock(ServerLevel level, BlockPos center, int half, int height, BlockPos standard, CampTier tier) {
		List<BlockPos> chests = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-half, 0, -half), center.offset(half, height, half))) {
			if (level.getBlockState(pos).is(Blocks.CHEST)) {
				chests.add(pos.immutable());
			}
		}
		chests.sort((a, b) -> cacheOrder(standard, a, b));
		int stocked = 0;
		for (BlockPos pos : chests) {
			if (stocked >= MAX_CACHES) {
				break;
			}
			if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
				chest.applyComponents(DataComponentMap.EMPTY, DataComponentPatch.builder()
						.set(DataComponents.CUSTOM_NAME, Component.translatable("container.warbands.cache")).build());
				chest.setLootTable(tier.cacheTable(), level.getRandom().nextLong());
				chest.setChanged();
				stocked++;
			}
		}
		return stocked;
	}

	/** A lit campfire with the tall smoke of a hay-bale fire: the landmark the victory message points at. */
	public static void campfire(ServerLevel level, BlockPos pos) {
		level.setBlock(pos, Blocks.CAMPFIRE.defaultBlockState()
				.setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, true), Block.UPDATE_ALL);
	}
}
