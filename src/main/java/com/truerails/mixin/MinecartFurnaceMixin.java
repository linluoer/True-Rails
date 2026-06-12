package com.truerails.mixin;

import com.truerails.TRConfig;
import net.minecraft.world.entity.vehicle.MinecartFurnace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecartFurnace.class)
public abstract class MinecartFurnaceMixin {

    @Inject(method = "getMaxSpeed", at = @At("RETURN"), cancellable = true)
    private void truerails$raiseMaxSpeed(CallbackInfoReturnable<Double> cir) {
        double cap = TRConfig.ABSOLUTE_CAP.get() / 20.0;
        if (cir.getReturnValueD() < cap) {
            cir.setReturnValue(cap);
        }
    }
}
