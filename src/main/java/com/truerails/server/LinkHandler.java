package com.truerails.server;

import com.truerails.TrueRails;
import com.truerails.registry.TRAttachments;
import com.truerails.train.TrainData;
import com.truerails.train.TrainGraph;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.MinecartFurnace;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = TrueRails.MODID)
public final class LinkHandler {
    private static final Map<UUID, UUID> SELECTION = new HashMap<>();
    private static final double LINK_RANGE = 8.0;

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getTarget() instanceof AbstractMinecart cart)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerLevel level = (ServerLevel) event.getLevel();
        ItemStack held = player.getMainHandItem();

        if (held.is(Items.CHAIN)) {
            event.setCanceled(true);
            handleChain(level, player, cart, held);
        } else if (held.isEmpty() && player.isShiftKeyDown()) {
            TrainData d = cart.getData(TRAttachments.TRAIN_DATA);
            if (d.linkCount() > 0) {
                event.setCanceled(true);
                unlinkAll(level, player, cart);
            }
        }
    }

    private static void handleChain(ServerLevel level, ServerPlayer player,
                                    AbstractMinecart cart, ItemStack held) {
        UUID selectedId = SELECTION.get(player.getUUID());
        AbstractMinecart selected = TrainGraph.resolve(level, selectedId);

        if (selected == null || selected == cart) {
            SELECTION.put(player.getUUID(), cart.getUUID());
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    cart.getX(), cart.getY() + 0.6, cart.getZ(), 8, 0.3, 0.3, 0.3, 0.0);
            player.displayClientMessage(Component.translatable("truerails.link.selected"), true);
            return;
        }

        if (selected.distanceTo(cart) > LINK_RANGE) {
            SELECTION.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("truerails.link.too_far"), true);
            return;
        }

        String fail = validate(level, selected, cart);
        if (fail != null) {
            SELECTION.remove(player.getUUID());
            player.displayClientMessage(Component.translatable(fail), true);
            return;
        }

        TrainData da = selected.getData(TRAttachments.TRAIN_DATA);
        TrainData db = cart.getData(TRAttachments.TRAIN_DATA);
        da.addNeighbor(cart.getUUID());
        db.addNeighbor(selected.getUUID());
        da.owner = player.getUUID();
        db.owner = player.getUUID();

        if (!player.getAbilities().instabuild) held.shrink(1);
        SELECTION.remove(player.getUUID());

        level.playSound(null, cart.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0f, 1.0f);
        level.sendParticles(ParticleTypes.CRIT,
                (selected.getX() + cart.getX()) / 2, (selected.getY() + cart.getY()) / 2 + 0.5,
                (selected.getZ() + cart.getZ()) / 2, 6, 0.2, 0.2, 0.2, 0.0);
        player.displayClientMessage(Component.translatable("truerails.link.linked"), true);
    }

    private static String validate(ServerLevel level, AbstractMinecart a, AbstractMinecart b) {
        TrainData da = a.getData(TRAttachments.TRAIN_DATA);
        TrainData db = b.getData(TRAttachments.TRAIN_DATA);
        if (!da.hasFreeSlot() || !db.hasFreeSlot()) return "truerails.link.fail_full";

        if (a instanceof MinecartFurnace && da.linkCount() >= 1) return "truerails.link.fail_furnace_link";
        if (b instanceof MinecartFurnace && db.linkCount() >= 1) return "truerails.link.fail_furnace_link";

        List<AbstractMinecart> ta = TrainGraph.ordered(level, a);
        if (ta.contains(b)) return "truerails.link.fail_same_train";
        List<AbstractMinecart> tb = TrainGraph.ordered(level, b);
        if (ta.size() + tb.size() > TrainGraph.MAX_SIZE) return "truerails.link.fail_too_long";

        long furnaces = ta.stream().filter(c -> c instanceof MinecartFurnace).count()
                + tb.stream().filter(c -> c instanceof MinecartFurnace).count();
        if (furnaces > 1) return "truerails.link.fail_two_furnace";
        return null;
    }

    private static void unlinkAll(ServerLevel level, Player player, AbstractMinecart cart) {
        TrainData d = cart.getData(TRAttachments.TRAIN_DATA);
        int broken = 0;
        for (UUID nid : new UUID[]{d.front, d.back}) {
            AbstractMinecart n = TrainGraph.resolve(level, nid);
            if (nid == null) continue;
            d.removeNeighbor(nid);
            if (n != null) n.getData(TRAttachments.TRAIN_DATA).removeNeighbor(cart.getUUID());
            broken++;
        }
        if (broken > 0) {
            if (!player.getAbilities().instabuild) {
                player.getInventory().placeItemBackInInventory(new ItemStack(Items.CHAIN, broken));
            }
            level.playSound(null, cart.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f);
            player.displayClientMessage(Component.translatable("truerails.link.unlinked"), true);
        }
    }

    public static void unlinkPair(ServerLevel level, AbstractMinecart a, AbstractMinecart b) {
        a.getData(TRAttachments.TRAIN_DATA).removeNeighbor(b.getUUID());
        b.getData(TRAttachments.TRAIN_DATA).removeNeighbor(a.getUUID());
        ItemEntity item = new ItemEntity(level,
                (a.getX() + b.getX()) / 2, (a.getY() + b.getY()) / 2 + 0.5, (a.getZ() + b.getZ()) / 2,
                new ItemStack(Items.CHAIN));
        level.addFreshEntity(item);
        level.playSound(null, a.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 1.0f, 0.8f);
    }

    private LinkHandler() {}
}
