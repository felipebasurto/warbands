package dev.felipe.warbands.town;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.felipe.warbands.registry.ModAttachments;
import dev.felipe.warbands.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/** Where Town Centers are and who owns them, per dimension. Houses must be built near one of yours. */
public final class TownRegistry {
	public record Town(BlockPos pos, UUID owner) {
		public static final Codec<Town> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("pos").forGetter(Town::pos),
				UUIDUtil.CODEC.fieldOf("owner").forGetter(Town::owner)).apply(i, Town::new));
	}

	public record Data(List<Town> towns) {
		public static final Data EMPTY = new Data(List.of());
		public static final Codec<Data> CODEC = Town.CODEC.listOf().xmap(Data::new, Data::towns);
	}

	private TownRegistry() {
	}

	public static void add(ServerLevel level, BlockPos pos, UUID owner) {
		List<Town> towns = new ArrayList<>(level.getAttachedOrCreate(ModAttachments.TOWNS).towns());
		towns.removeIf(town -> town.pos().equals(pos));
		towns.add(new Town(pos.immutable(), owner));
		level.setAttached(ModAttachments.TOWNS, new Data(List.copyOf(towns)));
	}

	public static void remove(ServerLevel level, BlockPos pos) {
		List<Town> towns = new ArrayList<>(level.getAttachedOrCreate(ModAttachments.TOWNS).towns());
		if (towns.removeIf(town -> town.pos().equals(pos))) {
			level.setAttached(ModAttachments.TOWNS, new Data(List.copyOf(towns)));
		}
	}

	/** Every Town Center in this dimension. */
	public static List<Town> towns(ServerLevel level) {
		return level.getAttachedOrCreate(ModAttachments.TOWNS).towns();
	}

	/**
	 * Whether {@code owner} has a Town Center within {@code radius} of {@code pos}, loaded or not (the registry keeps
	 * every center's position). Spacing rules use this so unloading a town can't be used to build a second one too close.
	 */
	public static boolean hasOwnedWithin(ServerLevel level, BlockPos pos, UUID owner, int radius) {
		double limit = (double) radius * radius;
		for (Town town : level.getAttachedOrCreate(ModAttachments.TOWNS).towns()) {
			if (town.owner().equals(owner) && town.pos().distSqr(pos) <= limit
					&& (!level.isLoaded(town.pos()) || level.getBlockState(town.pos()).is(ModBlocks.TOWN_CENTER))) {
				return true;
			}
		}
		return false;
	}

	/** The closest loaded Town Center this player owns within {@code radius}, or null. */
	public static @Nullable TownCenterBlockEntity nearestOwned(ServerLevel level, BlockPos pos, UUID owner, int radius) {
		Town best = null;
		double bestDist = (double) radius * radius;
		for (Town town : level.getAttachedOrCreate(ModAttachments.TOWNS).towns()) {
			if (!town.owner().equals(owner)) {
				continue;
			}
			double dist = town.pos().distSqr(pos);
			if (dist > bestDist || !level.isLoaded(town.pos())) {
				continue;
			}
			if (!level.getBlockState(town.pos()).is(ModBlocks.TOWN_CENTER)) {
				continue;
			}
			best = town;
			bestDist = dist;
		}
		if (best == null) {
			return null;
		}
		BlockEntity blockEntity = level.getBlockEntity(best.pos());
		return blockEntity instanceof TownCenterBlockEntity townCenter ? townCenter : null;
	}
}
