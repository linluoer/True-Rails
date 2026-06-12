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

/**
 * 移动走廊区块加载（§9）：仅接管"无人 + 烧燃料"的列车。
 * 前方预加载 = 速度×2 秒，横向 ±1 区块；后方尾车 +1；ticking 票据。
 * 约束：单列车 ≤24 区块；每车主 ≤3 列。
 * 重启校验：实体是否存活/烧燃料在票据验证阶段无法判定（实体尚未加载），
 * 一律清除持久化票据——运行中的列车下一刻会重新注册；深处未加载的
 * 无人列车冻结，玩家接近后恢复（§9 边界冻结语义）。
 */
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
        long graceUntil; // 0 = 未进入宽限
    }

    private static final Map<UUID, Entry> ACTIVE = new HashMap<>();
    private static final Map<UUID, Set<UUID>> OWNER_TRAINS = new HashMap<>();

    /** 每刻由 TrainTicker 对车头调用。 */
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
            if (manned) { // 有玩家：玩家自身加载，立即交还（§9）
                release(level, head);
                return;
            }
            // 无人且燃料耗尽：宽限 10 秒（§9）
            long now = level.getGameTime();
            if (e.graceUntil == 0) {
                e.graceUntil = now + (long) (TRConfig.CHUNK_GRACE_SEC.get() * 20.0);
            } else if (now > e.graceUntil) {
                release(level, head);
            }
            return;
        }

        if (e == null) {
            // 车主配额（连接者=车主，无主单车以自身计）
            UUID owner = head.getData(TRAttachments.TRAIN_DATA).owner;
            UUID ownerKey = owner != null ? owner : head.getUUID();
            Set<UUID> trains = OWNER_TRAINS.computeIfAbsent(ownerKey, k -> new HashSet<>());
            if (!trains.contains(head.getUUID())
                    && trains.size() >= TRConfig.CHUNK_OWNER_QUOTA.get()) {
                return; // 超配额：不加载
            }
            trains.add(head.getUUID());
            e = new Entry();
            e.ownerKey = ownerKey;
            ACTIVE.put(head.getUUID(), e);
        }
        e.graceUntil = 0;

        // 每秒重算走廊（§9 票据每秒更新）
        if (head.tickCount % 20 == 0 || e.chunks.isEmpty()) {
            refresh(level, head, train, e);
        }
    }

    private static void refresh(ServerLevel level, AbstractMinecart head,
                                List<AbstractMinecart> train, Entry e) {
        int cap = TRConfig.CHUNK_TRAIN_CAP.get();
        Set<Long> desired = new LinkedHashSet<>();

        // 本体（先入集，预算优先保证）
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
            int lx = dz != 0 ? 1 : 0; // 横向轴（与前进方向垂直）
            int lz = dx != 0 ? 1 : 0;

            // 前方：速度×aheadSeconds，横向 ±1（弯道时方向轴变化自然覆盖加宽）
            int ahead = (int) Math.ceil(speed * TRConfig.CHUNK_AHEAD_SEC.get() / 16.0);
            for (int i = 0; i <= ahead && desired.size() < cap; i++) {
                int cx = hx + dx * i, cz = hz + dz * i;
                desired.add(ChunkPos.asLong(cx, cz));
                if (desired.size() < cap) desired.add(ChunkPos.asLong(cx + lx, cz + lz));
                if (desired.size() < cap) desired.add(ChunkPos.asLong(cx - lx, cz - lz));
            }

            // 后方：尾车区块 +1（§9）
            AbstractMinecart tail = train.get(train.size() - 1);
            desired.add(ChunkPos.asLong(
                    SectionPos.blockToSectionCoord(tail.getBlockX()) - dx,
                    SectionPos.blockToSectionCoord(tail.getBlockZ()) - dz));
        }

        // 差分换票
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

    /** 解体/换头/卸载时释放。 */
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
