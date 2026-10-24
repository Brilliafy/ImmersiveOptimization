package net.conczin.entitycadence.mixin;

import net.conczin.entitycadence.TickScheduler;
import net.minecraft.entity.item.EntityItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Throttles dropped-item physics using the scheduler's cached priority table. Items within
 * pickup range (priority 1) are never skipped.
 */
@Mixin(EntityItem.class)
public abstract class EntityItemMixin {

    @Inject(method = "onUpdate", at = @At("HEAD"), cancellable = true)
    private void immersiveOptimization$throttleItem(CallbackInfo ci) {
        if (!TickScheduler.INSTANCE.shouldTick((EntityItem) (Object) this)) {
            ci.cancel();
        }
    }
}
