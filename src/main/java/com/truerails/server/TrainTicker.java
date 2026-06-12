package com.truerails.server;

import com.truerails.TRConfig;
import com.truerails.TrueRails;
import com.truerails.chunk.CorridorLoader;
import com.truerails.network.TrainHudPayload;
import com.truerails.network.TrainLinkPayload;
import com.truerails.physics.CruiseController;
import com.truerails.registry.TRAttachments;
import com.truerails.train.TrainData;
import com.truerails.train.TrainGraph;
import com.truerails.train.TrainRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.MinecartFurnace;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

@EventBusSubscriber(modid = TrueRails.MODID)
public final class TrainTicker {
    private static final double SYNC_DELTA = 1.5;
    private static final double SPACING = 1.65;
    private static final double MIN_GAP = 1.25;
    private static final double AUTO_UNLINK = 8.0;

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof AbstractMinecart cart)) return;
        if (!(cart.level() instanceof ServerLevel level)) return;

        TrainData data = cart.getData(TRAttachments.TRAIN_DATA);

        if (data.linkCount() == 0) {
            boolean manned = hasPlayer(cart);
            boolean fueled = isFueled(cart, data);
            if (manned || fueled) {
                CruiseController.tick(level, cart, List.of(cart));
            }
            if (cart instanceof MinecartFurnace) {
                CorridorLoader.update(level, cart, List.of(cart), manned, fueled);
            }
            syncObservers(cart);
            sendHud(cart, data, List.of(cart));
            return;
        }

        List<AbstractMinecart> train = TrainGraph.ordered(level, cart);
        AbstractMinecart head = train.get(0);
        TrainData headData = head.getData(TRAttachments.TRAIN_DATA);

        if (cart == head) {
            boolean manned = isManned(train);
            boolean fueled = isFueled(head, headData);
            if (manned || fueled) {
                CruiseController.tick(level, head, train);
            }
            CorridorLoader.update(level, head, train, manned, fueled);
            TrainRuntime.get(head).path.record(head.position());
            syncObservers(head);
            sendHud(head, headData, train);
            if (head.tickCount % 20 == 0) {
                for (int i = 1; i < train.size(); i++) {
                    PacketDistributor.sendToPlayersTrackingEntity(train.get(i - 1),
                            new TrainLinkPayload(train.get(i - 1).getId(), train.get(i).getId()));
                }
            }
            return;
        }

        CorridorLoader.release(level, cart);

        int idx = train.indexOf(cart);
        if (idx < 1) return;
        AbstractMinecart prev = train.get(idx - 1);

        if (cart.distanceTo(prev) > AUTO_UNLINK) {
            LinkHandler.unlinkPair(level, prev, cart);
            return;
        }

        Vec3 target = TrainRuntime.get(head).path.sample(idx * SPACING, head.position());
        if (target == null) {
            target = fallbackBehind(prev, cart);
        }
        target = enforceMinGap(target, prev, cart);

        cart.setPos(target.x, target.y, target.z);
        cart.setDeltaMovement(head.getDeltaMovement());
        syncObservers(cart);
    }

    private static boolean isFueled(AbstractMinecart cart, TrainData data) {
        return cart instanceof MinecartFurnace && data.fuel > 0.0;
    }

    private static Vec3 fallbackBehind(AbstractMinecart prev, AbstractMinecart cur) {
        Vec3 sep = new Vec3(cur.getX() - prev.getX(), 0, cur.getZ() - prev.getZ());
        Vec3 dir;
        if (sep.lengthSqr() > 1.0e-4) {
            dir = sep.normalize();
        } else {
            Vec3 m = prev.getDeltaMovement();
            dir = Math.hypot(m.x, m.z) > 1.0e-4
                    ? new Vec3(-m.x, 0, -m.z).normalize() : new Vec3(1, 0, 0);
        }
        return new Vec3(prev.getX() + dir.x * SPACING, prev.getY(), prev.getZ() + dir.z * SPACING);
    }

    private static Vec3 enforceMinGap(Vec3 target, AbstractMinecart prev, AbstractMinecart cur) {
        Vec3 d = new Vec3(target.x - prev.getX(), 0, target.z - prev.getZ());
        if (d.lengthSqr() >= MIN_GAP * MIN_GAP) return target;
        Vec3 dir = d.lengthSqr() > 1.0e-4 ? d.normalize()
                : fallbackBehind(prev, cur).subtract(prev.position()).normalize();
        return new Vec3(prev.getX() + dir.x * MIN_GAP, target.y, prev.getZ() + dir.z * MIN_GAP);
    }

    private static void sendHud(AbstractMinecart head, TrainData data, List<AbstractMinecart> train) {
        if (head.tickCount % 5 != 0) return;
        boolean furnace = head instanceof MinecartFurnace;
        TrainHudPayload payload = new TrainHudPayload(
                (float) data.cruise,
                furnace ? (float) (data.fuel / TRConfig.FUEL_CAPACITY.get()) : 0.0f,
                furnace,
                data.state);
        for (AbstractMinecart c : train) {
            for (Entity e : c.getPassengers()) {
                if (e instanceof ServerPlayer sp) PacketDistributor.sendToPlayer(sp, payload);
            }
        }
    }

    private static boolean hasPlayer(AbstractMinecart cart) {
        for (Entity e : cart.getPassengers()) {
            if (e instanceof ServerPlayer) return true;
        }
        return false;
    }

    private static boolean isManned(List<AbstractMinecart> train) {
        for (AbstractMinecart c : train) {
            if (hasPlayer(c)) return true;
        }
        return false;
    }

    private static void syncObservers(AbstractMinecart cart) {
        TrainRuntime rt = TrainRuntime.get(cart);
        double speed = cart.getDeltaMovement().horizontalDistance() * 20.0;
        boolean periodic = speed > 8.0 && cart.tickCount % 10 == 0;
        if (Math.abs(speed - rt.lastSyncedSpeed) > SYNC_DELTA || periodic) {
            cart.hurtMarked = true;
            rt.lastSyncedSpeed = speed;
        }
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof AbstractMinecart cart && !cart.level().isClientSide) {
            TrainRuntime.remove(cart);
        }
    }
}
