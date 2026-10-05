package dev.felipe.warbands.mixin;

import dev.felipe.warbands.camp.Camps;
import dev.felipe.warbands.camp.Traps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A camp's own defenders live there: they know where the tripwires and pressure plates are, so they don't trip
 * them (otherwise guards would blow their own powder kegs pacing the gate lane). Only inside an armed camp.
 */
@Mixin(Entity.class)
public abstract class CampTriggerMixin {
	@Inject(method = "isIgnoringBlockTriggers", at = @At("HEAD"), cancellable = true)
	private void warbands$campGuardsStepLight(CallbackInfoReturnable<Boolean> cir) {
		Entity self = (Entity) (Object) this;
		if (self.level() instanceof ServerLevel server && Traps.campOwn(self)
				&& Camps.campAt(server, self.blockPosition()).isPresent()) {
			cir.setReturnValue(true);
		}
	}
}
