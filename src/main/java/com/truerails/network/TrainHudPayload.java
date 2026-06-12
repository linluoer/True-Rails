package com.truerails.network;

import com.truerails.TrueRails;
import com.truerails.client.ClientHudState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** S→C HUD 同步小包，每 5 刻发给乘客。 */
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
        // ClientHudState 为纯数据类（无 Minecraft 客户端类引用），专用服务器上类加载安全
        ctx.enqueueWork(() -> {
            ClientHudState.cruise = p.cruise();
            ClientHudState.fuelPct = p.fuelPct();
            ClientHudState.hasFurnace = p.hasFurnace();
            ClientHudState.state = p.state();
        });
    }
}
