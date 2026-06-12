package com.truerails.registry;

import com.truerails.TrueRails;
import com.truerails.train.TrainData;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class TRAttachments {
    public static final DeferredRegister<AttachmentType<?>> REGISTER =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, TrueRails.MODID);

    public static final Supplier<AttachmentType<TrainData>> TRAIN_DATA = REGISTER.register(
            "train_data",
            () -> AttachmentType.builder(TrainData::new).serialize(TrainData.CODEC).build());

    private TRAttachments() {}
}
