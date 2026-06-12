package com.truerails.train;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TrainRuntime {
    private static final Map<UUID, TrainRuntime> MAP = new HashMap<>();

    public byte inputMask;
    public double envLimit = Double.MAX_VALUE;
    public double boostExtra;
    public BlockPos lastBoostRail;
    public double lastSyncedSpeed;
    public UUID driver;

    @Nullable public Vec3 lastPos;

    public int dwellTicks;
    public double graceBlocks;

    public int lowFuelCooldown;
    public boolean forcePassWhistled;

    public final PathRecorder path = new PathRecorder();

    public static TrainRuntime get(AbstractMinecart cart) {
        return MAP.computeIfAbsent(cart.getUUID(), u -> new TrainRuntime());
    }

    public static void remove(AbstractMinecart cart) {
        MAP.remove(cart.getUUID());
    }

    public static final class PathRecorder {
        private static final double MIN_STEP = 0.25;
        private static final double MAX_LENGTH = TrainGraph.MAX_SIZE * 1.65 + 16.0;

        private final ArrayDeque<Vec3> points = new ArrayDeque<>();

        public void record(Vec3 pos) {
            Vec3 newest = points.peekFirst();
            if (newest == null || newest.distanceTo(pos) >= MIN_STEP) {
                points.addFirst(pos);
                trim();
            }
        }

        @Nullable
        public Vec3 sample(double distBack, Vec3 headPos) {
            double acc = 0;
            Vec3 prev = headPos;
            for (Vec3 p : points) {
                double seg = prev.distanceTo(p);
                if (seg > 1.0e-6 && acc + seg >= distBack) {
                    return prev.lerp(p, (distBack - acc) / seg);
                }
                acc += seg;
                prev = p;
            }
            return null;
        }

        private void trim() {
            double acc = 0;
            Vec3 prev = null;
            int keep = 0;
            for (Vec3 p : points) {
                if (prev != null) acc += prev.distanceTo(p);
                keep++;
                if (acc > MAX_LENGTH) break;
            }
            while (points.size() > keep) points.removeLast();
        }
    }
}
