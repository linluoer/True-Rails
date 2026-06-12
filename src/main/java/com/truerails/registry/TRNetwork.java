package com.truerails.registry;

import com.truerails.network.DriveInputPayload;
import com.truerails.network.SetCruisePayload;
import com.truerails.network.TrainHudPayload;
import com.truerails.network.TrainLinkPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class TRNetwork {
    public static void register(final RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(DriveInputPayload.TYPE, DriveInputPayload.STREAM_CODEC, DriveInputPayload::handle);
        registrar.playToServer(SetCruisePayload.TYPE, SetCruisePayload.STREAM_CODEC, SetCruisePayload::handle);
        registrar.playToClient(TrainHudPayload.TYPE, TrainHudPayload.STREAM_CODEC, TrainHudPayload::handle);
        registrar.playToClient(TrainLinkPayload.TYPE, TrainLinkPayload.STREAM_CODEC, TrainLinkPayload::handle);
    }

    private TRNetwork() {}
}
