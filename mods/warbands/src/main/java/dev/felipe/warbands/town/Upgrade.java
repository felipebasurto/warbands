package dev.felipe.warbands.town;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.Nullable;

/**
 * One-time Blacksmith research: three in the Feudal Age, four in the Castle Age (each Castle one but Husbandry
 * improves a Feudal one, which it needs first). Costs are indexed by {@link Resource#ordinal()}: food, wood, stone,
 * gold. Research takes time at the Blacksmith, one at a time.
 */
public enum Upgrade implements StringRepresentable {
	FORGING("forging", Age.FEUDAL, null, 40, new int[] {200, 0, 0, 120}),
	FLETCHING("fletching", Age.FEUDAL, null, 40, new int[] {0, 150, 0, 120}),
	SCALE_ARMOR("scale_armor", Age.FEUDAL, null, 40, new int[] {200, 0, 0, 150}),
	BLAST_FORGING("blast_forging", Age.CASTLE, FORGING, 60, new int[] {300, 0, 0, 250}),
	BODKIN_ARROWS("bodkin_arrows", Age.CASTLE, FLETCHING, 60, new int[] {0, 250, 0, 200}),
	CHAIN_MAIL("chain_mail", Age.CASTLE, SCALE_ARMOR, 60, new int[] {300, 0, 0, 250}),
	HUSBANDRY("husbandry", Age.CASTLE, null, 60, new int[] {250, 0, 0, 150});

	public static final Codec<Upgrade> CODEC = StringRepresentable.fromEnum(Upgrade::values);

	private final String name;
	private final Age age;
	private final @Nullable Upgrade requires;
	private final int seconds;
	private final int[] cost;

	Upgrade(String name, Age age, @Nullable Upgrade requires, int seconds, int[] cost) {
		this.name = name;
		this.age = age;
		this.requires = requires;
		this.seconds = seconds;
		this.cost = cost;
	}

	public int cost(Resource resource) {
		return cost[resource.ordinal()];
	}

	/** The Town Center age needed to research it. */
	public Age age() {
		return age;
	}

	/** The upgrade that must be owned first, or null. */
	public @Nullable Upgrade requires() {
		return requires;
	}

	public int researchTicks() {
		return seconds * 20;
	}

	public Component displayName() {
		return Component.translatable("upgrade.warbands." + name);
	}

	public Component effect() {
		return Component.translatable("upgrade.warbands." + name + ".effect");
	}

	/** Advancement id, granted when this upgrade is bought. Castle upgrades share one. */
	public String advancement() {
		return age == Age.CASTLE ? "castle_smithing" : name;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	public static @Nullable Upgrade byId(int id) {
		Upgrade[] values = values();
		return id >= 0 && id < values.length ? values[id] : null;
	}
}
