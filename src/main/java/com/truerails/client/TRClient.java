package com.truerails.client;

import com.truerails.TrueRails;
import com.truerails.client.gui.FuelScreen;
import com.truerails.registry.TRMenus;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = TrueRails.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class TRClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {

        ModList.get().getModContainerById(TrueRails.MODID).ifPresent(container ->
                container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new));
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.EXPERIENCE_BAR,
                ResourceLocation.fromNamespaceAndPath(TrueRails.MODID, "speed_hud"),
                new SpeedHud());
    }

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(TRMenus.FURNACE_CART.get(), FuelScreen::new);
    }
}
