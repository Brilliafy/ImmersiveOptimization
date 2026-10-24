package net.conczin.entitycadence.mixin.client;

import net.conczin.entitycadence.TickScheduler;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderGlobal.class)
public abstract class RenderGlobalMixin {

    @Inject(method = "setupTerrain", at = @At("HEAD"))
    private void immersiveOptimization$setupTerrain(Entity viewEntity, double partialTicks, ICamera camera, int frameCount, boolean playerSpectator, CallbackInfo ci) {
        if (camera != null) {
            TickScheduler.INSTANCE.frustum = camera::isBoundingBoxInFrustum;
        }
    }
}
