package dev.felipe.warbands.raid;

import dev.felipe.warbands.town.Age;
import dev.felipe.warbands.town.UnitType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Who comes on a raid. Each raid kind has its own roster per age (the spec's 4B table): a plain Warband is mixed,
 * the Night Stalkers are all archers and crossbowmen, the Shield Wall is spearmen and militia on foot, a Siege is
 * rams and crossbowmen behind an escort, and a Cavalry raid is mostly knights. Every raid beaten adds one more
 * raider (up to 6 in Feudal, 8 in Castle), and a revenge raid one more on top.
 */
public final class RaidComposition {
	private static final List<UnitType> FEUDAL_BASE = List.of(UnitType.MILITIA, UnitType.MILITIA, UnitType.MILITIA,
			UnitType.MILITIA, UnitType.ARCHER, UnitType.ARCHER);
	private static final List<UnitType> CASTLE_BASE = List.of(UnitType.MILITIA, UnitType.MILITIA, UnitType.MILITIA,
			UnitType.ARCHER, UnitType.ARCHER, UnitType.SPEARMAN, UnitType.SPEARMAN, UnitType.CROSSBOWMAN, UnitType.KNIGHT,
			UnitType.RAM);
	private static final List<UnitType> FEUDAL_EXTRA = List.of(UnitType.MILITIA, UnitType.ARCHER, UnitType.SPEARMAN,
			UnitType.CROSSBOWMAN);
	private static final List<UnitType> CASTLE_EXTRA = List.of(UnitType.MILITIA, UnitType.CROSSBOWMAN, UnitType.SPEARMAN,
			UnitType.KNIGHT);

	private static final Map<RaidKind, List<UnitType>> FEUDAL_BASES = Map.of(
			RaidKind.WARBAND, FEUDAL_BASE,
			RaidKind.NIGHT_STALKERS, List.of(UnitType.ARCHER, UnitType.ARCHER, UnitType.ARCHER, UnitType.ARCHER,
					UnitType.CROSSBOWMAN, UnitType.CROSSBOWMAN),
			RaidKind.SHIELD_WALL, List.of(UnitType.MILITIA, UnitType.MILITIA, UnitType.MILITIA, UnitType.MILITIA,
					UnitType.SPEARMAN, UnitType.SPEARMAN));
	private static final Map<RaidKind, List<UnitType>> CASTLE_BASES = Map.of(
			RaidKind.WARBAND, CASTLE_BASE,
			RaidKind.NIGHT_STALKERS, List.of(UnitType.ARCHER, UnitType.ARCHER, UnitType.ARCHER, UnitType.ARCHER,
					UnitType.ARCHER, UnitType.CROSSBOWMAN, UnitType.CROSSBOWMAN, UnitType.CROSSBOWMAN, UnitType.CROSSBOWMAN,
					UnitType.CROSSBOWMAN),
			RaidKind.SHIELD_WALL, List.of(UnitType.MILITIA, UnitType.MILITIA, UnitType.MILITIA, UnitType.MILITIA,
					UnitType.SPEARMAN, UnitType.SPEARMAN, UnitType.SPEARMAN, UnitType.SPEARMAN, UnitType.KNIGHT,
					UnitType.KNIGHT),
			RaidKind.SIEGE, List.of(UnitType.MILITIA, UnitType.MILITIA, UnitType.SPEARMAN, UnitType.KNIGHT,
					UnitType.CROSSBOWMAN, UnitType.CROSSBOWMAN, UnitType.CROSSBOWMAN, UnitType.CROSSBOWMAN, UnitType.RAM,
					UnitType.RAM),
			RaidKind.CAVALRY, List.of(UnitType.KNIGHT, UnitType.KNIGHT, UnitType.KNIGHT, UnitType.KNIGHT, UnitType.KNIGHT,
					UnitType.MILITIA, UnitType.MILITIA, UnitType.MILITIA, UnitType.ARCHER, UnitType.ARCHER));

	private static final Map<RaidKind, List<UnitType>> FEUDAL_EXTRAS = Map.of(
			RaidKind.WARBAND, FEUDAL_EXTRA,
			RaidKind.NIGHT_STALKERS, List.of(UnitType.ARCHER, UnitType.CROSSBOWMAN),
			RaidKind.SHIELD_WALL, List.of(UnitType.MILITIA, UnitType.SPEARMAN));
	private static final Map<RaidKind, List<UnitType>> CASTLE_EXTRAS = Map.of(
			RaidKind.WARBAND, CASTLE_EXTRA,
			RaidKind.NIGHT_STALKERS, List.of(UnitType.ARCHER, UnitType.CROSSBOWMAN),
			RaidKind.SHIELD_WALL, List.of(UnitType.SPEARMAN, UnitType.MILITIA, UnitType.KNIGHT),
			RaidKind.SIEGE, List.of(UnitType.CROSSBOWMAN, UnitType.RAM, UnitType.MILITIA),
			RaidKind.CAVALRY, List.of(UnitType.KNIGHT, UnitType.KNIGHT, UnitType.MILITIA));

	private RaidComposition() {
	}

	public static int size(Age age, int won, boolean retaliation) {
		boolean castle = age.atLeast(Age.CASTLE);
		int base = castle ? CASTLE_BASE.size() : FEUDAL_BASE.size();
		int extra = Math.min(won, castle ? 8 : 6) + (retaliation ? 1 : 0);
		return base + extra;
	}

	/** The roster of a raid of {@code kind} on a town of {@code age}: the base, then extras cycled to reach size. */
	public static List<UnitType> units(RaidKind kind, Age age, int size) {
		boolean castle = age.atLeast(Age.CASTLE);
		List<UnitType> base = (castle ? CASTLE_BASES : FEUDAL_BASES).getOrDefault(kind, castle ? CASTLE_BASE : FEUDAL_BASE);
		List<UnitType> extra = (castle ? CASTLE_EXTRAS : FEUDAL_EXTRAS).getOrDefault(kind, castle ? CASTLE_EXTRA : FEUDAL_EXTRA);
		List<UnitType> units = new ArrayList<>(base.subList(0, Math.min(size, base.size())));
		for (int i = 0; units.size() < size; i++) {
			units.add(extra.get(i % extra.size()));
		}
		return units;
	}

	/** Gold paid into the stockpile per raider of a raid that was beaten. */
	public static int goldPerRaider(Age age) {
		return age.atLeast(Age.CASTLE) ? 25 : 15;
	}
}
