package dev.felipe.warbands.camp;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;

/**
 * The War Map. Bought at the Town Center, it marks your enemy camp: hold it and the action bar shows the camp, how far,
 * and which way. It points to your one active camp, so an old map points to the next one too.
 */
public class WarMapItem extends Item {
	public WarMapItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		if (owner instanceof Player player && level.getGameTime() % 10 == 0
				&& (player.getMainHandItem() == stack || player.getOffhandItem() == stack)) {
			player.sendOverlayMessage(Camps.hint(level, player));
		}
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("tooltip.warbands.war_map").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("tooltip.warbands.war_map.buy").withStyle(ChatFormatting.DARK_GRAY));
	}
}
