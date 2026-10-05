package dev.felipe.warbands;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** The Warbands Handbook: a guide to buildings, resources and units. Opens the illustrated guide (GuideScreen) on the client. */
public class HandbookItem extends Item {
	/** Set by the client initializer; opens the handbook screen. */
	public static Runnable openScreen = () -> {
	};

	public HandbookItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			openScreen.run();
		}
		return InteractionResult.SUCCESS;
	}
}
