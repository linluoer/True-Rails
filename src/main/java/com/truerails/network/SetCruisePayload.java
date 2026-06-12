package com.truerails.network;

import com.truerails.TrueRails;
import com.truerails.registry.TRAttachments;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.MinecartFurnace;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetCruisePayload(int cartId, byte gear) implements CustomPacketPayload {

    public static final Type<SetCruisePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TrueRails.MODID, "set_cruise"));

    public static final StreamCodec<ByteBuf, SetCruisePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetCruisePayload::cartId,
            ByteBufCodecs.BYTE, SetCruisePayload::gear,
            SetCruisePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetCruisePayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player().level().getEntity(p.cartId()) instanceof MinecartFurnace cart)) return;
            if (ctx.player().distanceToSqr(cart) > 64.0) return;
            cart.getData(TRAttachments.TRAIN_DATA).cruise = Mth.clamp(p.gear(), 0, 48);
        });
    }
}
