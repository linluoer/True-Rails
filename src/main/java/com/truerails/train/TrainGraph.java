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

public final class TrainGraph {
    public static final int MAX_SIZE = 8;

    @Nullable
    public static AbstractMinecart resolve(ServerLevel level, @Nullable UUID id) {
        if (id == null) return null;
        return level.getEntity(id) instanceof AbstractMinecart cart && cart.isAlive() ? cart : null;
    }

    public static List<AbstractMinecart> ordered(ServerLevel level, AbstractMinecart start) {
        Set<AbstractMinecart> component = new LinkedHashSet<>();
        collect(level, start, component);
        if (component.size() == 1) return List.of(start);

        List<AbstractMinecart> endpoints = new ArrayList<>();
        for (AbstractMinecart c : component) {
            if (resolvedNeighbors(level, c, component) <= 1) endpoints.add(c);
        }
        if (endpoints.isEmpty()) endpoints.add(start);

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
