package com.truerails.server;

import com.truerails.TrueRails;
import com.truerails.train.TrainRuntime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

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
