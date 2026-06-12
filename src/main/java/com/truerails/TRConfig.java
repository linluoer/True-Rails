package com.truerails;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class TRConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue MAX_SPEED;
    public static final ModConfigSpec.DoubleValue CURVE_LIMIT;
    public static final ModConfigSpec.DoubleValue SLOPE_LIMIT;
    public static final ModConfigSpec.DoubleValue MANUAL_DRIVE_LIMIT;
    public static final ModConfigSpec.DoubleValue ACCELERATION;
    public static final ModConfigSpec.DoubleValue BRAKE_FORCE;
    public static final ModConfigSpec.DoubleValue EMERGENCY_BRAKE;
    public static final ModConfigSpec.DoubleValue BOOST_IMPULSE;
    public static final ModConfigSpec.DoubleValue ABSOLUTE_CAP;
    public static final ModConfigSpec.DoubleValue OVERSPEED_HALF_LIFE;
    public static final ModConfigSpec.DoubleValue BRAKE_SAFETY_MARGIN;
    public static final ModConfigSpec.DoubleValue FUEL_CAPACITY;
    public static final ModConfigSpec.DoubleValue FUEL_RATE;
    public static final ModConfigSpec.BooleanValue CHUNK_ENABLED;
    public static final ModConfigSpec.DoubleValue CHUNK_AHEAD_SEC;
    public static final ModConfigSpec.IntValue CHUNK_TRAIN_CAP;
    public static final ModConfigSpec.IntValue CHUNK_OWNER_QUOTA;
    public static final ModConfigSpec.DoubleValue CHUNK_GRACE_SEC;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("speed");
        MAX_SPEED = b.comment("直线巡航上限（格/秒），需有燃料动力车头。",
                        "原版反作弊上限约 200 格/秒，128 以内安全。")
                .defineInRange("maxSpeed", 48.0, 1.0, 128.0);
        CURVE_LIMIT = b.comment("弯道限速（格/秒）")
                .defineInRange("curveLimit", 12.0, 1.0, 128.0);
        SLOPE_LIMIT = b.comment("坡道限速（格/秒）")
                .defineInRange("slopeLimit", 24.0, 1.0, 128.0);
        MANUAL_DRIVE_LIMIT = b.comment("徒手驾驶（无动力车头）限速")
                .defineInRange("manualDriveLimit", 24.0, 1.0, 128.0);
        ACCELERATION = b.comment("加速度（格/秒²）")
                .defineInRange("acceleration", 7.0, 0.5, 64.0);
        BRAKE_FORCE = b.comment("常规制动（格/秒²）")
                .defineInRange("brakeForce", 10.0, 0.5, 64.0);
        EMERGENCY_BRAKE = b.comment("紧急制动（格/秒²）")
                .defineInRange("emergencyBrake", 28.0, 1.0, 128.0);
        b.pop();

        b.push("boost");
        BOOST_IMPULSE = b.comment("每格充能动力轨瞬时冲量（格/秒）")
                .defineInRange("boostImpulsePerRail", 6.0, 0.0, 32.0);
        ABSOLUTE_CAP = b.comment("绝对速度上限（格/秒）")
                .defineInRange("absoluteSpeedCap", 64.0, 1.0, 128.0);
        OVERSPEED_HALF_LIFE = b.comment("超速冲量衰减半衰期（秒）")
                .defineInRange("overspeedHalfLifeSeconds", 7.0, 0.5, 60.0);
        b.pop();

        b.push("station");
        BRAKE_SAFETY_MARGIN = b.comment("制动包络安全余量（格）")
                .defineInRange("brakeSafetyMargin", 3.0, 0.0, 16.0);
        b.pop();

        b.push("fuel");
        FUEL_CAPACITY = b.comment("燃料容量（燃烧值刻，102400 = 一组煤）")
                .defineInRange("fuelCapacity", 102400.0, 1600.0, 10000000.0);
        FUEL_RATE = b.comment("48 格/秒满速时每刻消耗的燃烧值（油耗∝速度平方）")
                .defineInRange("fuelRateAtMaxSpeed", 4.0, 0.0, 64.0);
        b.pop();

        b.push("chunkloading");
        CHUNK_ENABLED = b.comment("无人燃料列车的移动走廊区块加载总开关")
                .define("enabled", true);
        CHUNK_AHEAD_SEC = b.comment("前方预加载窗口（秒），预加载距离=速度×该值")
                .defineInRange("aheadSeconds", 2.0, 0.5, 10.0);
        CHUNK_TRAIN_CAP = b.comment("单列车最大强加载区块数")
                .defineInRange("trainChunkCap", 24, 4, 96);
        CHUNK_OWNER_QUOTA = b.comment("每车主最多强加载列车数")
                .defineInRange("ownerTrainQuota", 3, 1, 16);
        CHUNK_GRACE_SEC = b.comment("燃料耗尽后保留加载的宽限（秒）")
                .defineInRange("fuelOutGraceSeconds", 10.0, 0.0, 60.0);
        b.pop();

        SPEC = b.build();
    }

    private TRConfig() {}
}
