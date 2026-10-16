package dev.felipe.warbands.town;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.felipe.warbands.registry.ModAttachments;
import dev.felipe.warbands.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Houses built from blueprints, so a Town Center can count its owner's houses cheaply.
 * A house counts while its hearth stands and at least half of its blocks are still there,
 * so breaking a house (or stripping it down to the hearth) lowers the population limit.
 */
public final class HouseRegistry {
	/** A house stops counting once fewer than this share of its blocks remain. */
	private static final float MIN_INTACT = 0.5F;

	/**
	 * One house. {@code blocks} is how many blueprint blocks it had between {@code min} and {@code max}.
	 * Houses saved before this existed have no owner or box: they count for anyone while the hearth stands.
	 */
	public record House(BlockPos hearth, Optional<UUID> owner, BlockPos min, BlockPos max, int blocks) {
		public static final Codec<House> FULL_CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("hearth").forGetter(House::hearth),
				UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(House::owner),
				BlockPos.CODEC.fieldOf("min").forGetter(House::min),
				BlockPos.CODEC.fieldOf("max").forGetter(House::max),
				Codec.INT.fieldOf("blocks").forGetter(House::blocks)).apply(i, House::new));
		public static final Codec<House> CODEC = Codec.withAlternative(FULL_CODEC, BlockPos.CODEC.xmap(House::legacy, House::hearth));

		static House legacy(BlockPos hearth) {
			return new House(hearth, Optional.empty(), hearth, hearth, 0);
		}

		boolean belongsTo(UUID player) {
			return owner.isEmpty() || owner.get().equals(player);
		}
	}

	public record Data(List<House> houses) {
		public static final Data EMPTY = new Data(List.of());
		public static final Codec<Data> CODEC = House.CODEC.listOf().xmap(Data::new, Data::houses);
	}

	private HouseRegistry() {
	}

	/** Called once a house blueprint is built. {@code min}/{@code max} is the building's box. */
	public static void add(ServerLevel level, BlockPos hearth, @Nullable UUID owner, BlockPos min, BlockPos max, int blocks) {
		List<House> houses = new ArrayList<>(level.getAttachedOrCreate(ModAttachments.HOUSES).houses());
		houses.removeIf(house -> house.hearth().equals(hearth));
		houses.add(new House(hearth.immutable(), Optional.ofNullable(owner), min.immutable(), max.immutable(), blocks));
		level.setAttached(ModAttachments.HOUSES, new Data(List.copyOf(houses)));
	}

	public static void remove(ServerLevel level, BlockPos hearth) {
		List<House> houses = new ArrayList<>(level.getAttachedOrCreate(ModAttachments.HOUSES).houses());
		if (houses.removeIf(house -> house.hearth().equals(hearth))) {
			level.setAttached(ModAttachments.HOUSES, new Data(List.copyOf(houses)));
		}
	}

	/** Counts {@code owner}'s standing houses within {@code radius} blocks, forgetting ones whose hearth is gone. */
	public static int countNear(ServerLevel level, BlockPos center, int radius, UUID owner) {
		int count = 0;
		double maxDistSqr = (double) radius * radius;
		for (House house : level.getAttachedOrCreate(ModAttachments.HOUSES).houses()) {
			if (house.hearth().distSqr(center) > maxDistSqr || !house.belongsTo(owner)) {
				continue;
			}
			if (level.isLoaded(house.hearth()) && !level.getBlockState(house.hearth()).is(ModBlocks.HEARTH)) {
				remove(level, house.hearth());
				continue;
			}
			if (isStanding(level, house)) {
				count++;
			}
		}
		return count;
	}

	/** True while enough of the house's blueprint blocks remain. Broken blocks are forgotten by {@link StructureBlocks}. */
	private static boolean isStanding(ServerLevel level, House house) {
		if (house.blocks() <= 0) {
			return true;
		}
		int needed = (int) Math.ceil(house.blocks() * MIN_INTACT);
		return StructureBlocks.countIn(level, house.min(), house.max(), needed) >= needed;
	}
}
