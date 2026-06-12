package com.truerails.mixin;

import com.truerails.TRConfig;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.MinecartFurnace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractMinecart.class)
public abstract class AbstractMinecartMixin {

    @Inject(method = "getMaxSpeed", at = @At("RETURN"), cancellable = true)
    private void truerails$raiseMaxSpeed(CallbackInfoReturnable<Double> cir) {
        truerails$raise(cir);
    }

    @Inject(method = "getMaxSpeedWithRail", at = @At("RETURN"), cancellable = true, remap = false)
    private void truerails$raiseMaxSpeedWithRail(CallbackInfoReturnable<Double> cir) {
        truerails$raise(cir);
    }

    private void truerails$raise(CallbackInfoReturnable<Double> cir) {
        AbstractMinecart self = (AbstractMinecart) (Object) this;
        boolean controlled = !self.getPassengers().isEmpty() || self instanceof MinecartFurnace;
        if (!controlled) return;
        double cap = TRConfig.ABSOLUTE_CAP.get() / 20.0 * 1.5;
        if (cir.getReturnValueD() < cap) {
            cir.setReturnValue(cap);
        }
    }
}
