package dev.felipe.warbands.camp;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * The prize for taking a camp: the raiders' standard. A Bandit Trophy is what the Town Center needs to reach the Feudal
 * Age, a Fort Trophy the Castle Age, so taking camps is part of the path. Only a claimed standard gives one.
 */
public class TrophyItem extends Item {
	private final String use;

	/** {@code use} is the lang key suffix of the line that says what it's for. */
	public TrophyItem(String use, Item.Properties properties) {
		super(properties);
		this.use = use;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("tooltip.warbands.trophy." + use).withStyle(ChatFormatting.GOLD));
		if (!use.equals("prize")) {
			tooltip.accept(Component.translatable("tooltip.warbands.trophy.how").withStyle(ChatFormatting.GRAY));
		}
	}
}
