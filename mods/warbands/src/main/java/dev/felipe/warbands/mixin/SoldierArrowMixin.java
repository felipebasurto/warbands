package dev.felipe.warbands.mixin;

import dev.felipe.warbands.entity.Gear;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A soldier's arrow vanishes a few seconds after it sticks, instead of the vanilla minute: archers shoot a lot, and
 * nobody can pick those arrows up anyway.
 */
@Mixin(AbstractArrow.class)
public abstract class SoldierArrowMixin {
	@Shadow
	protected int inGroundTime;

	@Inject(method = "tickDespawn", at = @At("HEAD"), cancellable = true)
	private void warbands$vanishSoon(CallbackInfo ci) {
		AbstractArrow arrow = (AbstractArrow) (Object) this;
		if (this.inGroundTime >= Gear.ARROW_LIFE_TICKS && arrow.entityTags().contains(Gear.ARROW_TAG)) {
			arrow.discard();
			ci.cancel();
		}
	}
}
