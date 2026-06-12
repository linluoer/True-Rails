package com.truerails.network;

import com.truerails.TrueRails;
import com.truerails.train.TrainGraph;
import com.truerails.train.TrainRuntime;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C→S 驾驶输入，单字节位掩码。坐任意车厢都可驾驶，输入转发车头（§3）。 */
public record DriveInputPayload(byte mask) implements CustomPacketPayload {
    public static final byte FWD = 1;
    public static final byte BACK = 2;
    public static final byte BRAKE = 4;

    public static final Type<DriveInputPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TrueRails.MODID, "drive_input"));

    public static final StreamCodec<ByteBuf, DriveInputPayload> STREAM_CODEC =
            ByteBufCodecs.BYTE.map(DriveInputPayload::new, DriveInputPayload::mask);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DriveInputPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player().getVehicle() instanceof AbstractMinecart cart)) return;
            if (cart.getFirstPassenger() != ctx.player()) return;
            if (!(ctx.player().level() instanceof ServerLevel level)) return;

            AbstractMinecart head = TrainGraph.head(level, cart);
            TrainRuntime rt = TrainRuntime.get(head);
            rt.inputMask = payload.mask();
            rt.driver = ctx.player().getUUID();
        });
    }
}
