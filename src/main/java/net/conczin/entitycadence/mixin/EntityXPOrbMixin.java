package net.conczin.entitycadence.mixin;

import net.conczin.entitycadence.TickScheduler;
import net.minecraft.entity.item.EntityXPOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Throttles distant XP orb physics using the scheduler's cached priority table. Orbs near a player
 * (priority 1) are never skipped.
 */
@Mixin(EntityXPOrb.class)
public abstract class EntityXPOrbMixin {

    @Inject(method = "onUpdate", at = @At("HEAD"), cancellable = true)
    private void immersiveOptimization$throttleXpOrb(CallbackInfo ci) {
        if (!TickScheduler.INSTANCE.shouldTick((EntityXPOrb) (Object) this)) {
            ci.cancel();
        }
    }
}
