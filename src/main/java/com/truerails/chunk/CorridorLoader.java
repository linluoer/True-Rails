package com.truerails.chunk;

import com.truerails.TRConfig;
import com.truerails.TrueRails;
import com.truerails.registry.TRAttachments;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = TrueRails.MODID)
public final class CorridorLoader {

    public static final TicketController CONTROLLER = new TicketController(
            ResourceLocation.fromNamespaceAndPath(TrueRails.MODID, "corridor"),
            (level, helper) ->
                    new HashSet<>(helper.getEntityTickets().keySet()).forEach(helper::removeAllTickets));

    public static void registerController(RegisterTicketControllersEvent event) {
        event.register(CONTROLLER);
    }

    private static final class Entry {
        final Set<Long> chunks = new HashSet<>();
        UUID ownerKey;
        long graceUntil;
    }

    private static final Map<UUID, Entry> ACTIVE = new HashMap<>();
    private static final Map<UUID, Set<UUID>> OWNER_TRAINS = new HashMap<>();

    public static void update(ServerLevel level, AbstractMinecart head,
                              List<AbstractMinecart> train, boolean manned, boolean fueled) {
        if (!TRConfig.CHUNK_ENABLED.get()) {
            release(level, head);
            return;
        }
        Entry e = ACTIVE.get(head.getUUID());
        boolean qualified = !manned && fueled;

        if (!qualified) {
            if (e == null) return;
            if (manned) {
                release(level, head);
                return;
            }

            long now = level.getGameTime();
            if (e.graceUntil == 0) {
                e.graceUntil = now + (long) (TRConfig.CHUNK_GRACE_SEC.get() * 20.0);
            } else if (now > e.graceUntil) {
                release(level, head);
            }
            return;
        }

        if (e == null) {

            UUID owner = head.getData(TRAttachments.TRAIN_DATA).owner;
            UUID ownerKey = owner != null ? owner : head.getUUID();
            Set<UUID> trains = OWNER_TRAINS.computeIfAbsent(ownerKey, k -> new HashSet<>());
            if (!trains.contains(head.getUUID())
                    && trains.size() >= TRConfig.CHUNK_OWNER_QUOTA.get()) {
                return;
            }
            trains.add(head.getUUID());
            e = new Entry();
            e.ownerKey = ownerKey;
            ACTIVE.put(head.getUUID(), e);
        }
        e.graceUntil = 0;

        if (head.tickCount % 20 == 0 || e.chunks.isEmpty()) {
            refresh(level, head, train, e);
        }
    }

    private static void refresh(ServerLevel level, AbstractMinecart head,
                                List<AbstractMinecart> train, Entry e) {
        int cap = TRConfig.CHUNK_TRAIN_CAP.get();
        Set<Long> desired = new LinkedHashSet<>();

        for (AbstractMinecart c : train) {
            desired.add(ChunkPos.asLong(
                    SectionPos.blockToSectionCoord(c.getBlockX()),
                    SectionPos.blockToSectionCoord(c.getBlockZ())));
        }

        Vec3 m = head.getDeltaMovement();
        double speed = m.horizontalDistance() * 20.0;
        int hx = SectionPos.blockToSectionCoord(head.getBlockX());
        int hz = SectionPos.blockToSectionCoord(head.getBlockZ());

        if (speed > 0.5) {
            int dx = Math.abs(m.x) >= Math.abs(m.z) ? (m.x >= 0 ? 1 : -1) : 0;
            int dz = dx == 0 ? (m.z >= 0 ? 1 : -1) : 0;
            int lx = dz != 0 ? 1 : 0;
            int lz = dx != 0 ? 1 : 0;

            int ahead = (int) Math.ceil(speed * TRConfig.CHUNK_AHEAD_SEC.get() / 16.0);
            for (int i = 0; i <= ahead && desired.size() < cap; i++) {
                int cx = hx + dx * i, cz = hz + dz * i;
                desired.add(ChunkPos.asLong(cx, cz));
                if (desired.size() < cap) desired.add(ChunkPos.asLong(cx + lx, cz + lz));
                if (desired.size() < cap) desired.add(ChunkPos.asLong(cx - lx, cz - lz));
            }

            AbstractMinecart tail = train.get(train.size() - 1);
            desired.add(ChunkPos.asLong(
                    SectionPos.blockToSectionCoord(tail.getBlockX()) - dx,
                    SectionPos.blockToSectionCoord(tail.getBlockZ()) - dz));
        }

        for (Long c : desired) {
            if (e.chunks.add(c)) {
                ChunkPos p = new ChunkPos(c);
                CONTROLLER.forceChunk(level, head, p.x, p.z, true, true);
            }
        }
        e.chunks.removeIf(c -> {
            if (desired.contains(c)) return false;
            ChunkPos p = new ChunkPos(c);
            CONTROLLER.forceChunk(level, head, p.x, p.z, false, true);
            return true;
        });
    }

    public static void release(ServerLevel level, AbstractMinecart cart) {
        Entry e = ACTIVE.remove(cart.getUUID());
        if (e == null) return;
        for (Long c : e.chunks) {
            ChunkPos p = new ChunkPos(c);
            CONTROLLER.forceChunk(level, cart, p.x, p.z, false, true);
        }
        Set<UUID> trains = OWNER_TRAINS.get(e.ownerKey);
        if (trains != null) {
            trains.remove(cart.getUUID());
            if (trains.isEmpty()) OWNER_TRAINS.remove(e.ownerKey);
        }
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof AbstractMinecart cart
                && event.getLevel() instanceof ServerLevel level) {
            release(level, cart);
        }
    }

    private CorridorLoader() {}
}
