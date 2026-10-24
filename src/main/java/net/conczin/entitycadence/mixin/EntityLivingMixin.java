package net.conczin.entitycadence.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.conczin.entitycadence.TickScheduler;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityLiving.class)
public abstract class EntityLivingMixin extends EntityLivingBase {

    public EntityLivingMixin(World worldIn) {
        super(worldIn);
    }

    @WrapOperation(
            method = "updateEntityActionState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/ai/EntityAITasks;onUpdateTasks()V"
            )
    )
    private void immersiveOptimization$wrapAiTasks(EntityAITasks tasks, Operation<Void> original) {
        if (TickScheduler.INSTANCE.shouldRunAi((EntityLiving) (Object) this)) {
            original.call(tasks);
        }
    }
}
