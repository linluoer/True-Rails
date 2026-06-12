package com.truerails.server;

import com.truerails.TrueRails;
import com.truerails.gui.FurnaceCartMenu;
import com.truerails.registry.TRAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.vehicle.MinecartFurnace;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = TrueRails.MODID)
public final class FuelHandler {

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getTarget() instanceof MinecartFurnace cart)) return;

        ItemStack held = event.getEntity().getMainHandItem();
        if (held.is(Items.CHAIN)) return;
        if (held.isEmpty() && event.getEntity().isShiftKeyDown()
                && cart.getData(TRAttachments.TRAIN_DATA).linkCount() > 0) {
            return;
        }

        event.setCanceled(true);
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        player.openMenu(new SimpleMenuProvider(
                        (id, inv, p) -> new FurnaceCartMenu(id, inv, cart.getId()),
                        Component.translatable("truerails.gui.fuel")),
                buf -> buf.writeVarInt(cart.getId()));
    }

    private FuelHandler() {}
}
