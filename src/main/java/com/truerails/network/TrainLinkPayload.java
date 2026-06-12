package com.truerails.network;

import com.truerails.TrueRails;
import com.truerails.client.ClientLinkState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TrainLinkPayload(int cartA, int cartB) implements CustomPacketPayload {

    public static final Type<TrainLinkPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TrueRails.MODID, "train_link"));

    public static final StreamCodec<ByteBuf, TrainLinkPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TrainLinkPayload::cartA,
            ByteBufCodecs.VAR_INT, TrainLinkPayload::cartB,
            TrainLinkPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TrainLinkPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
                ClientLinkState.put(p.cartA(), p.cartB(), System.currentTimeMillis() + 3000L));
    }
}
