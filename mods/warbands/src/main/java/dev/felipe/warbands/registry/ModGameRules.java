package dev.felipe.warbands.registry;

import dev.felipe.warbands.Warbands;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

/** {@code /gamerule warbands:raids false} turns raids off on any difficulty (they're always off on Peaceful). */
public final class ModGameRules {
	public static final GameRule<Boolean> RAIDS = GameRuleBuilder.forBoolean(true)
			.category(GameRuleCategory.MOBS)
			.buildAndRegister(Warbands.id("raids"));

	private ModGameRules() {
	}

	public static void initialize() {
	}
}
