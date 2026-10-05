package dev.felipe.warbands.item;

import java.util.function.Consumer;

import dev.felipe.warbands.camp.CampTier;
import dev.felipe.warbands.town.Relics;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A fantasy camp's relic: the tooltip says what it does and is its only signpost toward the Relic Pedestal. */
public class RelicItem extends Item {
	public RelicItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		CampTier kind = Relics.kindOf(stack.getItem());
		if (kind != null) {
			tooltip.accept(Component.translatable("handbook.warbands.relics." + kind.serializedName() + ".effect")
					.withStyle(ChatFormatting.BLUE));
		}
		tooltip.accept(Component.translatable("tooltip.warbands.relic").withStyle(ChatFormatting.GRAY));
	}
}
