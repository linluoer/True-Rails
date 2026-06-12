package com.truerails.train;

import com.truerails.registry.TRAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.MinecartFurnace;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 编组遍历：邻接槽位无向图 → 从端点起的有序列表，车头在 index 0。 */
public final class TrainGraph {
    public static final int MAX_SIZE = 8;

    @Nullable
    public static AbstractMinecart resolve(ServerLevel level, @Nullable UUID id) {
        if (id == null) return null;
        return level.getEntity(id) instanceof AbstractMinecart cart && cart.isAlive() ? cart : null;
    }

    /**
     * 车头端点确定性选取（所有车厢 tick 必须得出同一车头）：
     * 1. 动力矿车（§2.3 永远车头）
     * 2. 第一乘客为玩家的端点（客户端权威驾驶；多个则取乘客 UUID 最小，
     *    与 ClientDrive.shouldSim 的仲裁规则一致）
     * 3. UUID 最小端点
     */
    public static List<AbstractMinecart> ordered(ServerLevel level, AbstractMinecart start) {
        Set<AbstractMinecart> component = new LinkedHashSet<>();
        collect(level, start, component);
        if (component.size() == 1) return List.of(start);

        List<AbstractMinecart> endpoints = new ArrayList<>();
        for (AbstractMinecart c : component) {
            if (resolvedNeighbors(level, c, component) <= 1) endpoints.add(c);
        }
        if (endpoints.isEmpty()) endpoints.add(start); // 环（理论不可达），兜底

        AbstractMinecart headEnd = null;
        for (AbstractMinecart c : endpoints) {
            if (c instanceof MinecartFurnace) { headEnd = c; break; }
        }
        if (headEnd == null) {
            for (AbstractMinecart c : endpoints) {
                if (!(c.getFirstPassenger() instanceof Player p)) continue;
                if (headEnd == null || p.getUUID().compareTo(
                        ((Player) headEnd.getFirstPassenger()).getUUID()) < 0) {
                    headEnd = c;
                }
            }
        }
        if (headEnd == null) {
            headEnd = endpoints.get(0);
            for (AbstractMinecart c : endpoints) {
                if (c.getUUID().compareTo(headEnd.getUUID()) < 0) headEnd = c;
            }
        }

        List<AbstractMinecart> out = new ArrayList<>();
        AbstractMinecart prev = null, cur = headEnd;
        while (cur != null && out.size() < MAX_SIZE) {
            out.add(cur);
            AbstractMinecart next = null;
            TrainData d = cur.getData(TRAttachments.TRAIN_DATA);
            AbstractMinecart a = resolve(level, d.front);
            AbstractMinecart b = resolve(level, d.back);
            if (a != null && a != prev && component.contains(a)) next = a;
            else if (b != null && b != prev && component.contains(b)) next = b;
            prev = cur;
            cur = next;
        }
        return out;
    }

    public static AbstractMinecart head(ServerLevel level, AbstractMinecart any) {
        return ordered(level, any).get(0);
    }

    private static void collect(ServerLevel level, AbstractMinecart cart, Set<AbstractMinecart> out) {
        if (out.size() >= MAX_SIZE || !out.add(cart)) return;
        TrainData d = cart.getData(TRAttachments.TRAIN_DATA);
        AbstractMinecart a = resolve(level, d.front);
        AbstractMinecart b = resolve(level, d.back);
        if (a != null) collect(level, a, out);
        if (b != null) collect(level, b, out);
    }

    private static int resolvedNeighbors(ServerLevel level, AbstractMinecart c, Set<AbstractMinecart> component) {
        TrainData d = c.getData(TRAttachments.TRAIN_DATA);
        int n = 0;
        AbstractMinecart a = resolve(level, d.front);
        AbstractMinecart b = resolve(level, d.back);
        if (a != null && component.contains(a)) n++;
        if (b != null && component.contains(b)) n++;
        return n;
    }

    private TrainGraph() {}
}
