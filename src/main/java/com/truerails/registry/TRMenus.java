package com.truerails.registry;

import com.truerails.TrueRails;
import com.truerails.gui.FurnaceCartMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class TRMenus {
    public static final DeferredRegister<MenuType<?>> REGISTER =
            DeferredRegister.create(Registries.MENU, TrueRails.MODID);

    public static final Supplier<MenuType<FurnaceCartMenu>> FURNACE_CART = REGISTER.register(
            "furnace_cart",
            () -> IMenuTypeExtension.create((id, inv, buf) -> new FurnaceCartMenu(id, inv, buf.readVarInt())));

    private TRMenus() {}
}
