package dev.felipe.warbands.town;

import dev.felipe.warbands.Warbands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/** What each item is worth when deposited into a Town Center stockpile. */
public final class ResourceValues {
	/** Items that are never accepted (e.g. rotten flesh), editable via datapack. */
	public static final TagKey<Item> NOT_ACCEPTED = TagKey.create(Registries.ITEM, Warbands.id("not_accepted"));

	public record Value(Resource resource, int perItem) {
	}

	private ResourceValues() {
	}

	public static @Nullable Value of(ItemStack stack) {
		if (stack.isEmpty() || stack.is(NOT_ACCEPTED)) {
			return null;
		}
		FoodProperties food = stack.get(DataComponents.FOOD);
		if (food != null && food.nutrition() > 0) {
			return new Value(Resource.FOOD, food.nutrition());
		}
		if (stack.is(ItemTags.LOGS)) {
			return new Value(Resource.WOOD, 4);
		}
		if (stack.is(ItemTags.PLANKS)) {
			return new Value(Resource.WOOD, 1);
		}
		if (stack.is(Items.GOLD_BLOCK)) {
			return new Value(Resource.GOLD, 81);
		}
		if (stack.is(Items.GOLD_INGOT)) {
			return new Value(Resource.GOLD, 9);
		}
		if (stack.is(Items.GOLD_NUGGET)) {
			return new Value(Resource.GOLD, 1);
		}
		if (stack.is(ItemTags.STONE_CRAFTING_MATERIALS) || stack.is(ItemTags.STONE_TOOL_MATERIALS) || stack.is(Items.STONE)) {
			return new Value(Resource.STONE, 1);
		}
		return null;
	}
}
