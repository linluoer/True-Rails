package com.truerails;

import com.truerails.chunk.CorridorLoader;
import com.truerails.registry.TRAttachments;
import com.truerails.registry.TRMenus;
import com.truerails.registry.TRNetwork;
import com.truerails.registry.TRSounds;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(TrueRails.MODID)
public final class TrueRails {
    public static final String MODID = "truerails";

    public TrueRails(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, TRConfig.SPEC);
        TRAttachments.REGISTER.register(modBus);
        TRMenus.REGISTER.register(modBus);
        TRSounds.REGISTER.register(modBus);
        modBus.addListener(TRNetwork::register);
        modBus.addListener(CorridorLoader::registerController);
    }
}
