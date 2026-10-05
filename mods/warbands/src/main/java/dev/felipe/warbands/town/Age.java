package dev.felipe.warbands.town;

import dev.felipe.warbands.Warbands;
import dev.felipe.warbands.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * A town's age. Costs and the structure file belong to the age you advance into.
 * Village is free and uses the original Town Center. Each age also makes all of the owner's
 * soldiers stronger (see {@code SoldierEntity}) and raises that town's population limit.
 */
public enum Age {
	VILLAGE("village", 0, 0, 0, null, 0, "town_center"),
	FEUDAL("feudal", 500, 200, 200, () -> ModItems.BANDIT_TROPHY, 60 * 20, "town_center_feudal"),
	CASTLE("castle", 800, 400, 500, () -> ModItems.FORT_TROPHY, 90 * 20, "town_center_castle");

	/** Full bow draw, as in {@code SoldierBowAttackGoal}. */
	public static final int BOW_DRAW_TICKS = 20;

	private final String name;
	private final int food;
	private final int stone;
	private final int gold;
	private final @Nullable Supplier<Item> requirement;
	private final int researchTicks;
	private final String structure;

	Age(String name, int food, int stone, int gold, @Nullable Supplier<Item> requirement, int researchTicks, String structure) {
		this.name = name;
		this.food = food;
		this.stone = stone;
		this.gold = gold;
		this.requirement = requirement;
		this.researchTicks = researchTicks;
		this.structure = structure;
	}

	/** Stockpile cost of advancing into this age. The Town Center is rebuilt in stone, so it takes stone too. */
	public int cost(Resource resource) {
		return switch (resource) {
			case FOOD -> food;
			case WOOD -> 0;
			case STONE -> stone;
			case GOLD -> gold;
		};
	}

	/**
	 * Item that must sit in the deposit slot: the trophy of the camp for the age you're leaving (Bandit Camp for Feudal,
	 * Raider Fort for Castle). It's used up. A supplier, since items register after this enum may load.
	 */
	public @Nullable Item requirement() {
		return requirement == null ? null : requirement.get();
	}

	public int researchTicks() {
		return researchTicks;
	}

	public Identifier structureId() {
		return Warbands.id(structure);
	}

	/** Extra max health for every soldier of an owner in this age. */
	public int soldierHealthBonus() {
		return switch (this) {
			case VILLAGE -> 0;
			case FEUDAL -> 4;
			case CASTLE -> 8;
		};
	}

	/** Extra armor points for every soldier of an owner in this age. */
	public int soldierArmorBonus() {
		return switch (this) {
			case VILLAGE -> 0;
			case FEUDAL -> 2;
			case CASTLE -> 4;
		};
	}

	/** The Militia's sword: stone, iron, diamond. */
	public Item militiaWeapon() {
		return switch (this) {
			case VILLAGE -> Items.STONE_SWORD;
			case FEUDAL -> Items.IRON_SWORD;
			case CASTLE -> Items.DIAMOND_SWORD;
		};
	}

	/** The Spearman's vanilla spear: stone, iron, diamond. */
	public Item spearmanWeapon() {
		return switch (this) {
			case VILLAGE -> Items.STONE_SPEAR;
			case FEUDAL -> Items.IRON_SPEAR;
			case CASTLE -> Items.DIAMOND_SPEAR;
		};
	}

	/** Ticks an Archer waits after a shot before drawing again (the draw itself takes {@link #BOW_DRAW_TICKS}). */
	public int archerShotTicks() {
		return switch (this) {
			case VILLAGE -> 20;
			case FEUDAL -> 12;
			case CASTLE -> 5;
		};
	}

	/** How much faster Archers shoot than in the Village Age, in percent (full draw-and-wait cycle). */
	public int archerSpeedBonusPercent() {
		int base = VILLAGE.archerShotTicks() + BOW_DRAW_TICKS;
		int now = archerShotTicks() + BOW_DRAW_TICKS;
		return Math.round(100.0F * (base - now) / now);
	}

	/** Added to the town's base population and to its maximum. */
	public int populationBonus() {
		return switch (this) {
			case VILLAGE -> 0;
			case FEUDAL -> 5;
			case CASTLE -> 10;
		};
	}

	/** Lines describing what this age gives, for tooltips and the age-up announcement. */
	public List<Component> benefits() {
		if (this == VILLAGE) {
			return List.of();
		}
		List<Component> lines = new ArrayList<>(List.of(
				Component.translatable("age.warbands.benefit.health", soldierHealthBonus() / 2),
				Component.translatable("age.warbands.benefit.armor", soldierArmorBonus()),
				Component.translatable("age.warbands.benefit.militia", Component.translatable(militiaWeapon().getDescriptionId())),
				Component.translatable("age.warbands.benefit.spearman", Component.translatable(spearmanWeapon().getDescriptionId())),
				Component.translatable("age.warbands.benefit.archer", archerSpeedBonusPercent()),
				Component.translatable("age.warbands.benefit.population", populationBonus())));
		if (unlocksBuildings()) {
			lines.add(Component.translatable("age.warbands.unlocks." + name));
		}
		return lines;
	}

	/** True for ages whose blueprints only become craftable (and placeable) once the town reaches them. */
	public boolean unlocksBuildings() {
		return this == FEUDAL || this == CASTLE;
	}

	/** Chat/overlay message when something needs this age and the Town Center isn't there yet. */
	public Component notReachedMessage() {
		return Component.translatable("message.warbands.need_" + name);
	}

	/** Red tooltip line on a disabled button that needs this age. */
	public Component notReachedReason() {
		return Component.translatable("gui.warbands.reason.need_" + name);
	}

	/** Short status label in a building's menu ("Need Feudal"). */
	public Component notReachedStatus() {
		return Component.translatable("gui.warbands.need_" + name);
	}

	public Component displayName() {
		return Component.translatable("age.warbands." + name);
	}

	public @Nullable Age next() {
		int next = ordinal() + 1;
		Age[] values = values();
		return next < values.length ? values[next] : null;
	}

	public boolean atLeast(Age other) {
		return ordinal() >= other.ordinal();
	}

	public static Age byName(String name) {
		for (Age age : values()) {
			if (age.name.equals(name)) {
				return age;
			}
		}
		return VILLAGE;
	}

	public String serializedName() {
		return name;
	}
}
