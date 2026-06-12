package com.truerails.train;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * 持久化数据附加（挂在矿车实体上）。
 * front/back 为两个无向邻接槽位（不保证指向车头方向，编组顺序由 TrainGraph 现场推导）。
 * 卸载模组后字段被忽略，矿车退化为原版矿车（无残留原则）。
 */
public final class TrainData {
    public static final Codec<TrainData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("cruise", 0.0).forGetter(d -> d.cruise),
            Codec.INT.optionalFieldOf("state", 0).forGetter(d -> d.state),
            Codec.DOUBLE.optionalFieldOf("fuel", 0.0).forGetter(d -> d.fuel),
            UUIDUtil.CODEC.optionalFieldOf("front").forGetter(d -> Optional.ofNullable(d.front)),
            UUIDUtil.CODEC.optionalFieldOf("back").forGetter(d -> Optional.ofNullable(d.back)),
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(d -> Optional.ofNullable(d.owner))
    ).apply(i, TrainData::new));

    public double cruise;
    public int state;
    public double fuel;
    @Nullable public UUID front;
    @Nullable public UUID back;
    @Nullable public UUID owner; // 连接者=车主（M5 加载配额用）

    public TrainData() {}

    public TrainData(double cruise, int state, double fuel,
                     Optional<UUID> front, Optional<UUID> back, Optional<UUID> owner) {
        this.cruise = cruise;
        this.state = state;
        this.fuel = fuel;
        this.front = front.orElse(null);
        this.back = back.orElse(null);
        this.owner = owner.orElse(null);
    }

    public int linkCount() {
        return (front != null ? 1 : 0) + (back != null ? 1 : 0);
    }

    public boolean hasFreeSlot() {
        return front == null || back == null;
    }

    public void addNeighbor(UUID id) {
        if (front == null) front = id;
        else if (back == null) back = id;
    }

    public void removeNeighbor(UUID id) {
        if (id.equals(front)) front = null;
        if (id.equals(back)) back = null;
    }
}
