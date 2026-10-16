package dev.felipe.warbands.mixin;

import dev.felipe.warbands.camp.Camps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mob overrides getBaseExperienceReward, so a mixin on LivingEntity never runs for a camp's guards. Targets Mob directly.
 */
@Mixin(Mob.class)
public abstract class CampGuardExperienceMixin {
	@Inject(method = "getBaseExperienceReward", at = @At("HEAD"), cancellable = true)
	private void warbands$noGuardExperience(ServerLevel level, CallbackInfoReturnable<Integer> cir) {
		if (((Entity) (Object) this).entityTags().contains(Camps.GUARD_TAG)) {
			cir.setReturnValue(0);
		}
	}
}
