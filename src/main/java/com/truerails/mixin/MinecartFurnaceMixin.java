package com.truerails.mixin;

import com.truerails.TRConfig;
import net.minecraft.world.entity.vehicle.MinecartFurnace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * MinecartFurnace 自身重写了 getMaxSpeed()（约 0.2 格/刻 = 4 格/秒），
 * 父类 AbstractMinecart 上的 Mixin 对子类重写无效——动力车头因此被钳死，
 * 油耗∝速度² 也随之近乎为零。必须在子类上再抬一次。
 */
@Mixin(MinecartFurnace.class)
public abstract class MinecartFurnaceMixin {

    @Inject(method = "getMaxSpeed", at = @At("RETURN"), cancellable = true)
    private void truerails$raiseMaxSpeed(CallbackInfoReturnable<Double> cir) {
        double cap = TRConfig.ABSOLUTE_CAP.get() / 20.0; // 格/刻
        if (cir.getReturnValueD() < cap) {
            cir.setReturnValue(cap);
        }
    }
}
