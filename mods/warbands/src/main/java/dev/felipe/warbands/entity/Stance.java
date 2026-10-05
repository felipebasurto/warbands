package dev.felipe.warbands.entity;

import net.minecraft.network.chat.Component;

/** What a soldier is doing right now. The War Horn cycles these (Shift goes back one) for the whole army. */
public enum Stance {
	FOLLOW("follow"),
	HOLD("hold"),
	CHARGE("charge");

	private final String name;

	Stance(String name) {
		this.name = name;
	}

	public String serializedName() {
		return name;
	}

	public Component displayName() {
		return Component.translatable("stance.warbands." + name);
	}

	public Stance next() {
		Stance[] values = values();
		return values[(ordinal() + 1) % values.length];
	}

	public Stance previous() {
		Stance[] values = values();
		return values[(ordinal() + values.length - 1) % values.length];
	}

	public static Stance byName(String name) {
		for (Stance stance : values()) {
			if (stance.name.equals(name)) {
				return stance;
			}
		}
		return FOLLOW;
	}
}
