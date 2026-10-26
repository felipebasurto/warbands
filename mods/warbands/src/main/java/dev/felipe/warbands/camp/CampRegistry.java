package dev.felipe.warbands.camp;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.felipe.warbands.registry.ModAttachments;
import dev.felipe.warbands.town.Age;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;

/**
 * Enemy camps sent by War Maps, per dimension. A camp is chosen when the map is bought, built when a player comes
 * close, conquered when its guards are dead and its standard is claimed, and paid out to the Town Center that
 * sold the map the next time that town is loaded. An abandoned camp frees the owner to buy a new map; if it was already
 * built it stays standing (and can still be taken), otherwise it's forgotten.
 */
public final class CampRegistry {
	private static final Codec<CampTier> TIER_CODEC = Codec.STRING.xmap(
			name -> {
				CampTier tier = CampTier.byName(name);
				return tier == null ? CampTier.BANDIT_CAMP : tier;
			}, CampTier::serializedName);

	/**
	 * {@code cleared}: its guards are all dead and the owner was told to claim it. {@code razeAt}: game time a taken camp
	 * finishes burning and is cleared away (0 while standing); {@code razed}: already cleared.
	 * RecordCodecBuilder takes 16 fields and this record is already there, so participants and a burn pause are stored
	 * beside the camps.
	 */
	public record Camp(UUID owner, BlockPos town, CampTier tier, BlockPos center, Rotation rotation, boolean built,
			List<UUID> guards, boolean conquered, boolean paid, boolean abandoned, boolean cleared, long razeAt, boolean razed,
			String layoutId, Threat threat, boolean tracked) {
		public static final Codec<Camp> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("owner").forGetter(Camp::owner),
				BlockPos.CODEC.fieldOf("town").forGetter(Camp::town),
				TIER_CODEC.fieldOf("tier").forGetter(Camp::tier),
				BlockPos.CODEC.fieldOf("center").forGetter(Camp::center),
				Rotation.CODEC.fieldOf("rotation").forGetter(Camp::rotation),
				Codec.BOOL.fieldOf("built").forGetter(Camp::built),
				UUIDUtil.CODEC.listOf().fieldOf("guards").forGetter(Camp::guards),
				Codec.BOOL.fieldOf("conquered").forGetter(Camp::conquered),
				Codec.BOOL.fieldOf("paid").forGetter(Camp::paid),
				Codec.BOOL.optionalFieldOf("abandoned", false).forGetter(Camp::abandoned),
				Codec.BOOL.optionalFieldOf("cleared", false).forGetter(Camp::cleared),
				Codec.LONG.optionalFieldOf("raze_at", 0L).forGetter(Camp::razeAt),
				// Camps taken before burning existed are left standing.
				Codec.BOOL.optionalFieldOf("razed", true).forGetter(Camp::razed),
				// Camps chosen before layouts existed use their kind's first (original) layout.
				Codec.STRING.optionalFieldOf("layout", "").forGetter(Camp::layoutId),
				Threat.CODEC.optionalFieldOf("threat", Threat.NORMAL).forGetter(Camp::threat),
				// Only camps built since guard deaths were recorded list just their living guards; older ones list dead ones too.
				Codec.BOOL.optionalFieldOf("tracked", false).forGetter(Camp::tracked)).apply(i, Camp::new));

		/** A camp just chosen by a War Map: not built yet, nobody guarding it. */
		public static Camp chosen(UUID owner, BlockPos town, CampTier tier, BlockPos center, Rotation rotation, String layoutId,
				Threat estimate) {
			return new Camp(owner, town, tier, center, rotation, false, List.of(), false, false, false, false, 0L, false,
					layoutId, estimate, false);
		}

		/** A camp of the kind's first (original) layout at the normal threat: the game tests build these. */
		public static Camp chosen(UUID owner, BlockPos town, CampTier tier, BlockPos center, Rotation rotation) {
			return chosen(owner, town, tier, center, rotation, tier.layouts().get(0).id(), Threat.NORMAL);
		}

		/** This camp's layout: size, shape, core and guard spots. */
		public CampLayout layout() {
			return tier.layout(layoutId);
		}

		Camp asBuilt(BlockPos center, List<UUID> guards, Threat threat) {
			return new Camp(owner, town, tier, center, rotation, true, List.copyOf(guards), false, false, abandoned, false, 0L, false,
					layoutId, threat, true);
		}

		Camp withoutGuard(UUID id) {
			List<UUID> rest = new ArrayList<>(guards);
			rest.remove(id);
			return new Camp(owner, town, tier, center, rotation, built, List.copyOf(rest), conquered, paid, abandoned, cleared, razeAt, razed, layoutId, threat, tracked);
		}

		Camp asCleared() {
			return new Camp(owner, town, tier, center, rotation, built, guards, conquered, paid, abandoned, true, razeAt, razed, layoutId, threat, tracked);
		}

		Camp asConquered(long razeAt) {
			return new Camp(owner, town, tier, center, rotation, true, List.of(), true, false, abandoned, true, razeAt, false, layoutId, threat, tracked);
		}

		/** Same camp, with the burn's deadline moved once when a pause ends. */
		Camp withRazeAt(long razeAt) {
			return new Camp(owner, town, tier, center, rotation, built, guards, conquered, paid, abandoned, cleared, razeAt, razed,
					layoutId, threat, tracked);
		}

		Camp asPaid() {
			return new Camp(owner, town, tier, center, rotation, built, guards, conquered, true, abandoned, cleared, razeAt, razed, layoutId, threat, tracked);
		}

		Camp asAbandoned() {
			return new Camp(owner, town, tier, center, rotation, built, guards, conquered, paid, true, cleared, razeAt, razed, layoutId, threat, tracked);
		}

		Camp asRazed() {
			return new Camp(owner, town, tier, center, rotation, built, guards, conquered, paid, abandoned, cleared, razeAt, true, layoutId, threat, tracked);
		}

		/** Still burning: taken, not yet cleared away. */
		public boolean burning() {
			return conquered && !razed && razeAt > 0;
		}
	}

	/** A commander's remaining draws for one age: refilled shuffled when empty, and never serves {@code last} twice. */
	public record Bag(Age age, List<CampTier> bag, Optional<CampTier> last) {
		public static final Codec<Bag> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.xmap(Age::byName, Age::serializedName).fieldOf("age").forGetter(Bag::age),
				TIER_CODEC.listOf().fieldOf("bag").forGetter(Bag::bag),
				TIER_CODEC.optionalFieldOf("last").forGetter(Bag::last)).apply(i, Bag::new));
	}

	/** NBT map keys have to be strings; a numeric key drops the whole attachment on save. */
	private static final Codec<Long> LONG_KEY = Codec.STRING.xmap(Long::parseLong, center -> Long.toString(center));

	/**
	 * {@code camps}: every chosen camp. {@code bags}: per player the kinds still to be drawn (Phase 4C). Saved as a
	 * map; worlds saved before the kinds existed keep a bare list, which loads as camps with no bags.
	 * {@code participants}: players who traded blows with a camp, keyed by {@link #key(Camp)}. Missing on old saves,
	 * so only the owner can claim those.
	 * {@code burnPauses}: game time a burning camp first found its footprint not ticking, keyed by the camp center
	 * ({@link BlockPos#asLong()}). Old saves omit it.
	 * Both sit beside the camps because {@link Camp} is already at the codec's field limit.
	 */
	public record Data(List<Camp> camps, Map<UUID, Bag> bags, Map<String, List<UUID>> participants, Map<Long, Long> burnPauses) {
		/** Camps and bags only: saves from before participants and burn pauses, and callers that have neither. */
		public Data(List<Camp> camps, Map<UUID, Bag> bags) {
			this(camps, bags, Map.of(), Map.of());
		}

		public static final Data EMPTY = new Data(List.of(), Map.of());
		private static final Codec<Data> CURRENT = RecordCodecBuilder.create(i -> i.group(
				Camp.CODEC.listOf().fieldOf("camps").forGetter(Data::camps),
				Codec.unboundedMap(UUIDUtil.STRING_CODEC, Bag.CODEC).optionalFieldOf("bags", Map.of()).forGetter(Data::bags),
				Codec.unboundedMap(Codec.STRING, UUIDUtil.CODEC.listOf()).optionalFieldOf("participants", Map.of()).forGetter(Data::participants),
				Codec.unboundedMap(LONG_KEY, Codec.LONG).optionalFieldOf("burn_pauses", Map.of()).forGetter(Data::burnPauses))
				.apply(i, Data::new));
		public static final Codec<Data> CODEC = Codec.either(Camp.CODEC.listOf(), CURRENT).xmap(
				either -> either.map(camps -> new Data(camps, Map.of()), d -> d), Either::right);
	}

	private CampRegistry() {
	}

	public static List<Camp> all(ServerLevel level) {
		return level.getAttachedOrCreate(ModAttachments.CAMPS).camps();
	}

	/** The kinds this player still draws before the bag refills (empty when the age changed or it's all spent). */
	public static Bag bag(ServerLevel level, UUID owner) {
		Bag bag = level.getAttachedOrCreate(ModAttachments.CAMPS).bags().get(owner);
		return bag == null ? new Bag(Age.VILLAGE, List.of(), Optional.empty()) : bag;
	}

	static void putBag(ServerLevel level, UUID owner, Bag bag) {
		Data data = level.getAttachedOrCreate(ModAttachments.CAMPS);
		Map<UUID, Bag> bags = new HashMap<>(data.bags());
		bags.put(owner, bag);
		level.setAttached(ModAttachments.CAMPS, new Data(data.camps(), Map.copyOf(bags), data.participants(), data.burnPauses()));
	}

	/** Players recorded as having fought {@code camp}. Empty when nobody has, including on old saves. */
	public static List<UUID> participants(ServerLevel level, Camp camp) {
		List<UUID> found = level.getAttachedOrCreate(ModAttachments.CAMPS).participants().get(key(camp));
		return found == null ? List.of() : found;
	}

	/** Records {@code fighter} on {@code camp}. A second record of the same player leaves the list as it is. */
	public static void addParticipant(ServerLevel level, Camp camp, UUID fighter) {
		Data data = level.getAttachedOrCreate(ModAttachments.CAMPS);
		List<UUID> current = data.participants().getOrDefault(key(camp), List.of());
		if (current.contains(fighter)) {
			return;
		}
		List<UUID> next = new ArrayList<>(current);
		next.add(fighter);
		Map<String, List<UUID>> participants = new HashMap<>(data.participants());
		participants.put(key(camp), List.copyOf(next));
		level.setAttached(ModAttachments.CAMPS, new Data(data.camps(), data.bags(), Map.copyOf(participants), data.burnPauses()));
	}

	/** Game time this camp's burn was paused, or {@link BurnSchedule#NOT_PAUSED}. */
	static long burnPausedAt(ServerLevel level, Camp camp) {
		return level.getAttachedOrCreate(ModAttachments.CAMPS).burnPauses().getOrDefault(camp.center().asLong(), BurnSchedule.NOT_PAUSED);
	}

	/** Records the start of a pause. A second call with the same time does not rewrite the attachment. */
	static void noteBurnPaused(ServerLevel level, Camp camp, long pausedAt) {
		Data data = level.getAttachedOrCreate(ModAttachments.CAMPS);
		long center = camp.center().asLong();
		if (data.burnPauses().getOrDefault(center, BurnSchedule.NOT_PAUSED) == pausedAt) {
			return;
		}
		Map<Long, Long> pauses = new HashMap<>(data.burnPauses());
		pauses.put(center, pausedAt);
		level.setAttached(ModAttachments.CAMPS, new Data(data.camps(), data.bags(), data.participants(), Map.copyOf(pauses)));
	}

	/**
	 * Ends a pause: drops the saved start and, when {@code razeAt} changed, stores the shifted deadline.
	 * Fighter lists and any other pauses are kept for camps that remain. Returns the camp the rest of the tick should use.
	 */
	static Camp finishBurnPause(ServerLevel level, Camp camp, long razeAt) {
		Data data = level.getAttachedOrCreate(ModAttachments.CAMPS);
		Map<Long, Long> pauses = new HashMap<>(data.burnPauses());
		pauses.remove(camp.center().asLong());
		List<Camp> camps = new ArrayList<>(data.camps());
		Camp updated = camp;
		if (razeAt != camp.razeAt()) {
			updated = camp.withRazeAt(razeAt);
			int index = camps.indexOf(camp);
			if (index >= 0) {
				camps.set(index, updated);
			} else {
				camps.add(updated);
			}
		}
		level.setAttached(ModAttachments.CAMPS, stored(camps, data.bags(), data.participants(), pauses));
		return updated;
	}

	/** Owner and center: both stay put once a camp is built, which is when a fight can be recorded. */
	private static String key(Camp camp) {
		return camp.owner() + ":" + camp.center().asLong();
	}

	/** Drops fighter lists for camps that are no longer saved. */
	private static Map<String, List<UUID>> kept(List<Camp> camps, Map<String, List<UUID>> participants) {
		Map<String, List<UUID>> next = new HashMap<>();
		for (Camp camp : camps) {
			List<UUID> fought = participants.get(key(camp));
			if (fought != null) {
				next.put(key(camp), fought);
			}
		}
		return Map.copyOf(next);
	}

	/** Drops pause stamps for camps that are no longer saved. */
	private static Map<Long, Long> keptPauses(List<Camp> camps, Map<Long, Long> burnPauses) {
		Map<Long, Long> next = new HashMap<>();
		for (Camp camp : camps) {
			Long pausedAt = burnPauses.get(camp.center().asLong());
			if (pausedAt != null) {
				next.put(camp.center().asLong(), pausedAt);
			}
		}
		return Map.copyOf(next);
	}

	/** Saves the camp list and prunes both side maps down to camps that are still in it. */
	private static Data stored(List<Camp> camps, Map<UUID, Bag> bags, Map<String, List<UUID>> participants, Map<Long, Long> burnPauses) {
		List<Camp> keptCamps = List.copyOf(camps);
		return new Data(keptCamps, bags, kept(keptCamps, participants), keptPauses(keptCamps, burnPauses));
	}

	/** The player's camp still to conquer (at most one at a time). An abandoned camp no longer counts. */
	public static Optional<Camp> active(ServerLevel level, UUID owner) {
		return all(level).stream().filter(c -> c.owner().equals(owner) && !c.conquered() && !c.abandoned()).findFirst();
	}

	static void remove(ServerLevel level, Camp camp) {
		Data data = level.getAttachedOrCreate(ModAttachments.CAMPS);
		List<Camp> camps = new ArrayList<>(data.camps());
		if (camps.remove(camp)) {
			level.setAttached(ModAttachments.CAMPS, stored(camps, data.bags(), data.participants(), data.burnPauses()));
		}
	}

	static void replace(ServerLevel level, Camp old, Camp updated) {
		Data data = level.getAttachedOrCreate(ModAttachments.CAMPS);
		List<Camp> camps = new ArrayList<>(data.camps());
		int index = camps.indexOf(old);
		if (index >= 0) {
			camps.set(index, updated);
		} else {
			camps.add(updated);
		}
		level.setAttached(ModAttachments.CAMPS, stored(camps, data.bags(), data.participants(), data.burnPauses()));
	}

	static void add(ServerLevel level, Camp camp) {
		Data data = level.getAttachedOrCreate(ModAttachments.CAMPS);
		List<Camp> camps = new ArrayList<>(data.camps());
		camps.add(camp);
		level.setAttached(ModAttachments.CAMPS, stored(camps, data.bags(), data.participants(), data.burnPauses()));
	}

	/**
	 * Paid-out camps are kept so their standard stays inert and new camps keep their distance; this drops the oldest
	 * finished ones (paid and burnt down) past a hundred.
	 */
	static void trim(ServerLevel level) {
		Data data = level.getAttachedOrCreate(ModAttachments.CAMPS);
		List<Camp> camps = new ArrayList<>(data.camps());
		int finished = (int) camps.stream().filter(c -> c.paid() && !c.burning()).count();
		for (int i = 0; i < camps.size() && finished > 100; i++) {
			if (camps.get(i).paid() && !camps.get(i).burning()) {
				camps.remove(i--);
				finished--;
			}
		}
		level.setAttached(ModAttachments.CAMPS, stored(camps, data.bags(), data.participants(), data.burnPauses()));
	}
}
