package com.truerails.mixin;

import com.truerails.TRConfig;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.MinecartFurnace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 硬解除矿车速度钳制。
 * 上限 = 绝对上限 ÷ 0.75 再留余量：控制器为补偿原版"载人 ×0.75/刻"
 * 阻尼会写入 v/0.75，clamp 必须放得下补偿后的值。
 * 仅作用于受控矿车（载人/动力矿车），普通空矿车保持原版行为。
 */
@Mixin(AbstractMinecart.class)
public abstract class AbstractMinecartMixin {

    @Inject(method = "getMaxSpeed", at = @At("RETURN"), cancellable = true)
    private void truerails$raiseMaxSpeed(CallbackInfoReturnable<Double> cir) {
        truerails$raise(cir);
    }

    /** NeoForge 注入方法，remap=false。 */
    @Inject(method = "getMaxSpeedWithRail", at = @At("RETURN"), cancellable = true, remap = false)
    private void truerails$raiseMaxSpeedWithRail(CallbackInfoReturnable<Double> cir) {
        truerails$raise(cir);
    }

    private void truerails$raise(CallbackInfoReturnable<Double> cir) {
        AbstractMinecart self = (AbstractMinecart) (Object) this;
        boolean controlled = !self.getPassengers().isEmpty() || self instanceof MinecartFurnace;
        if (!controlled) return;
        double cap = TRConfig.ABSOLUTE_CAP.get() / 20.0 * 1.5; // ÷0.75 补偿余量
        if (cir.getReturnValueD() < cap) {
            cir.setReturnValue(cap);
        }
    }
}
