package net.conczin.entitycadence.mixin;

import net.conczin.entitycadence.TickScheduler;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(World.class)
public abstract class WorldMixin {

    @Shadow
    public boolean isRemote;

    @Inject(method = "updateEntities", at = @At("HEAD"))
    private void immersiveOptimization$startLevelTick(CallbackInfo ci) {
        if (!this.isRemote) {
            TickScheduler.INSTANCE.startLevelTick((World) (Object) this);
        }
    }
}
