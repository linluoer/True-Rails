package com.truerails.server;

import com.truerails.TrueRails;
import com.truerails.train.TrainRuntime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * M2：输入掩码的服务端兜底清理。
 * 客户端下车时会发一次全 0 输入，但丢包/掉线时必须由服务端清除，
 * 否则矿车会带着残留的 W 输入永远加速。
 */
@EventBusSubscriber(modid = TrueRails.MODID)
public final class PlayerEventHandler {

    @SubscribeEvent
    public static void onDismount(EntityMountEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!event.isDismounting()) return;
        if (!(event.getEntityMounting() instanceof Player player)) return;
        if (!(event.getEntityBeingMounted() instanceof AbstractMinecart cart)) return;

        TrainRuntime rt = TrainRuntime.get(cart);
        if (player.getUUID().equals(rt.driver)) {
            rt.inputMask = 0;
            rt.driver = null;
        }
        // 下车保留巡航（§11 可配置项，默认保留）：data.cruise 不清零
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (player.getVehicle() instanceof AbstractMinecart cart) {
            TrainRuntime rt = TrainRuntime.get(cart);
            rt.inputMask = 0;
            rt.driver = null;
        }
    }
}
