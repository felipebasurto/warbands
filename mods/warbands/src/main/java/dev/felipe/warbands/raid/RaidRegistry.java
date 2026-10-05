package dev.felipe.warbands.raid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.felipe.warbands.registry.ModAttachments;
import dev.felipe.warbands.town.Age;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;

/** Saved raid state per Town Center: when the next raid is due, how many were beaten, and the raid in progress. */
public final class RaidRegistry {
	private static final Codec<Age> AGE_CODEC = Codec.STRING.xmap(Age::byName, Age::serializedName);

	public enum Phase {
		/** The horn has sounded; raiders appear when the warning runs out. */
		WARNING,
		/** Raiders are marching on the town or fighting. */
		ATTACK,
		/** The raid is over for them (plunder taken or time up): they walk away and vanish. */
		RETREAT;

		static final Codec<Phase> CODEC = Codec.STRING.xmap(Phase::valueOf, Phase::name);
	}

	/**
	 * {@code raiders}: the ones still alive (a raider leaves the list when it dies). {@code elapsed}: attack time that
	 * counted toward the timeout, i.e. only while the owner was near. {@code kind}: which warband came (set when the
	 * raid starts; raids saved before raid kinds read as {@link RaidKind#WARBAND}).
	 */
	public record ActiveRaid(Phase phase, long phaseStart, long started, BlockPos spawn, List<UUID> raiders, int total,
			Age age, int plunderTicks, int elapsed, RaidKind kind) {
		public static final Codec<ActiveRaid> CODEC = RecordCodecBuilder.create(i -> i.group(
				Phase.CODEC.fieldOf("phase").forGetter(ActiveRaid::phase),
				Codec.LONG.fieldOf("phase_start").forGetter(ActiveRaid::phaseStart),
				Codec.LONG.fieldOf("started").forGetter(ActiveRaid::started),
				BlockPos.CODEC.fieldOf("spawn").forGetter(ActiveRaid::spawn),
				UUIDUtil.CODEC.listOf().fieldOf("raiders").forGetter(ActiveRaid::raiders),
				Codec.INT.fieldOf("total").forGetter(ActiveRaid::total),
				AGE_CODEC.fieldOf("age").forGetter(ActiveRaid::age),
				Codec.INT.fieldOf("plunder_ticks").forGetter(ActiveRaid::plunderTicks),
				Codec.INT.optionalFieldOf("elapsed", 0).forGetter(ActiveRaid::elapsed),
				RaidKind.CODEC.optionalFieldOf("kind", RaidKind.WARBAND).forGetter(ActiveRaid::kind))
				.apply(i, ActiveRaid::new));

		/** For tests and other callers that don't care about the kind: a plain warband. */
		public ActiveRaid(Phase phase, long phaseStart, long started, BlockPos spawn, List<UUID> raiders, int total, Age age,
				int plunderTicks, int elapsed) {
			this(phase, phaseStart, started, spawn, raiders, total, age, plunderTicks, elapsed, RaidKind.WARBAND);
		}

		ActiveRaid withPhase(Phase phase, long now) {
			return new ActiveRaid(phase, now, started, spawn, raiders, total, age, 0, elapsed, kind);
		}

		ActiveRaid withRaiders(List<UUID> raiders, int total) {
			return new ActiveRaid(phase, phaseStart, started, spawn, List.copyOf(raiders), total, age, plunderTicks, elapsed, kind);
		}

		ActiveRaid withProgress(int plunderTicks, int elapsed) {
			return new ActiveRaid(phase, phaseStart, started, spawn, raiders, total, age, plunderTicks, elapsed, kind);
		}
	}

	/**
	 * {@code retaliation}: the next raid comes the coming night and is one raider bigger (a camp was taken).
	 * {@code bag}: the raid kinds still to be drawn, shuffled; refilled when it empties, never serving the
	 * {@code last} kind first, so the same kind can't come twice in a row. Both fields default to empty on old saves.
	 */
	public record TownRaids(BlockPos town, UUID owner, long nextRaid, int won, boolean retaliation, Optional<ActiveRaid> active,
			List<RaidKind> bag, Optional<RaidKind> last) {
		public static final Codec<TownRaids> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("town").forGetter(TownRaids::town),
				UUIDUtil.CODEC.fieldOf("owner").forGetter(TownRaids::owner),
				Codec.LONG.fieldOf("next_raid").forGetter(TownRaids::nextRaid),
				Codec.INT.fieldOf("won").forGetter(TownRaids::won),
				Codec.BOOL.fieldOf("retaliation").forGetter(TownRaids::retaliation),
				ActiveRaid.CODEC.optionalFieldOf("active").forGetter(TownRaids::active),
				RaidKind.CODEC.listOf().optionalFieldOf("bag", List.of()).forGetter(TownRaids::bag),
				RaidKind.CODEC.optionalFieldOf("last").forGetter(TownRaids::last)).apply(i, TownRaids::new));

		/** A town that hasn't drawn a kind yet (fresh or pre-4B save): the bag fills on the next draw. */
		public TownRaids(BlockPos town, UUID owner, long nextRaid, int won, boolean retaliation, Optional<ActiveRaid> active) {
			this(town, owner, nextRaid, won, retaliation, active, List.of(), Optional.empty());
		}

		TownRaids withActive(Optional<ActiveRaid> active) {
			return new TownRaids(town, owner, nextRaid, won, retaliation, active, bag, last);
		}

		TownRaids withNext(long nextRaid, int won, boolean retaliation) {
			return new TownRaids(town, owner, nextRaid, won, retaliation, active, bag, last);
		}
	}

	public record Data(List<TownRaids> towns) {
		public static final Data EMPTY = new Data(List.of());
		public static final Codec<Data> CODEC = TownRaids.CODEC.listOf().xmap(Data::new, Data::towns);
	}

	private RaidRegistry() {
	}

	public static List<TownRaids> all(ServerLevel level) {
		return level.getAttachedOrCreate(ModAttachments.RAIDS).towns();
	}

	public static Optional<TownRaids> get(ServerLevel level, BlockPos town) {
		return all(level).stream().filter(t -> t.town().equals(town)).findFirst();
	}

	public static void put(ServerLevel level, TownRaids raids) {
		List<TownRaids> towns = new ArrayList<>(all(level));
		towns.removeIf(t -> t.town().equals(raids.town()));
		towns.add(raids);
		level.setAttached(ModAttachments.RAIDS, new Data(List.copyOf(towns)));
	}

	public static void remove(ServerLevel level, BlockPos town) {
		List<TownRaids> towns = new ArrayList<>(all(level));
		if (towns.removeIf(t -> t.town().equals(town))) {
			level.setAttached(ModAttachments.RAIDS, new Data(List.copyOf(towns)));
		}
	}

	/** True while a raid on {@code town} is in progress. Raiders of a raid that's over disappear. */
	public static boolean isActive(ServerLevel level, BlockPos town) {
		return get(level, town).flatMap(TownRaids::active).isPresent();
	}
}
