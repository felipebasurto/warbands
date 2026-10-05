package dev.felipe.warbands.town;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public enum Resource {
	FOOD("food", 0xFFA8321C, Items.BREAD),
	WOOD("wood", 0xFF6E4518, Items.OAK_LOG),
	STONE("stone", 0xFF4A4A4A, Items.COBBLESTONE),
	GOLD("gold", 0xFFA87B00, Items.GOLD_INGOT);

	private final String key;
	private final int color;
	private final Item icon;

	Resource(String key, int color, Item icon) {
		this.key = key;
		this.color = color;
		this.icon = icon;
	}

	public Component displayName() {
		return Component.translatable("resource.warbands." + key);
	}

	/** ARGB color used for this resource's numbers in the GUI. */
	public int color() {
		return color;
	}

	public Item icon() {
		return icon;
	}
}
