package com.truerails.registry;

import com.truerails.TrueRails;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class TRSounds {
    public static final DeferredRegister<SoundEvent> REGISTER =
            DeferredRegister.create(Registries.SOUND_EVENT, TrueRails.MODID);

    public static final Supplier<SoundEvent> WHISTLE_SHORT = register("whistle_short");
    public static final Supplier<SoundEvent> WHISTLE_LONG = register("whistle_long");
    public static final Supplier<SoundEvent> RAIL_CLACK = register("rail_clack");
    public static final Supplier<SoundEvent> BRAKE_SQUEAL = register("brake_squeal");
    public static final Supplier<SoundEvent> LOW_FUEL = register("low_fuel");

    private static Supplier<SoundEvent> register(String name) {
        return REGISTER.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(TrueRails.MODID, name)));
    }

    private TRSounds() {}
}
