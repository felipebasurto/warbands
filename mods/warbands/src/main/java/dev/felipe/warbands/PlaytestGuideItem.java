package dev.felipe.warbands;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * The Tester's Guide, given by {@code /warbands playtest}: right-click for a panel of buttons (raids, camps, go home,
 * go to camp, time, weather, kit...) instead of scrolling back for the chat menu. The buttons run {@code /warbands}
 * commands, so they need cheats on, like the command that gave it.
 */
public class PlaytestGuideItem extends Item {
	/** Set by the client initializer; opens the tester's panel. */
	public static Runnable openScreen = () -> {
	};

	public PlaytestGuideItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			openScreen.run();
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("tooltip.warbands.playtest_guide").withStyle(ChatFormatting.GRAY));
	}
}
