package com.truerails.network;

import com.truerails.TrueRails;
import com.truerails.client.ClientHudState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TrainHudPayload(float cruise, float fuelPct, boolean hasFurnace, int state)
        implements CustomPacketPayload {

    public static final Type<TrainHudPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TrueRails.MODID, "train_hud"));

    public static final StreamCodec<ByteBuf, TrainHudPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, TrainHudPayload::cruise,
            ByteBufCodecs.FLOAT, TrainHudPayload::fuelPct,
            ByteBufCodecs.BOOL, TrainHudPayload::hasFurnace,
            ByteBufCodecs.VAR_INT, TrainHudPayload::state,
            TrainHudPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TrainHudPayload p, IPayloadContext ctx) {

        ctx.enqueueWork(() -> {
            ClientHudState.cruise = p.cruise();
            ClientHudState.fuelPct = p.fuelPct();
            ClientHudState.hasFurnace = p.hasFurnace();
            ClientHudState.state = p.state();
        });
    }
}
