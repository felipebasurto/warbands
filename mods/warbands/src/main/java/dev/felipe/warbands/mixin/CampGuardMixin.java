package dev.felipe.warbands.mixin;

import dev.felipe.warbands.camp.Camps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A camp's vanilla-mob guards (witches, strays, illagers...) behave like the raider soldiers they fight beside:
 * they drop nothing, so taking a camp can't be farmed for mob loot. Experience is in {@link CampGuardExperienceMixin}.
 */
@Mixin(LivingEntity.class)
public abstract class CampGuardMixin {
	@Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true)
	private void warbands$noGuardDrops(ServerLevel level, DamageSource source, CallbackInfo ci) {
		if (((Entity) (Object) this).entityTags().contains(Camps.GUARD_TAG)) {
			ci.cancel();
		}
	}
}
