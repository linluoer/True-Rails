package com.truerails.gui;

import com.truerails.TRConfig;
import com.truerails.registry.TRAttachments;
import com.truerails.registry.TRMenus;
import com.truerails.train.TrainData;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.MinecartFurnace;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

public class FurnaceCartMenu extends AbstractContainerMenu {
    @Nullable
    private final MinecartFurnace cart;
    private final int cartId;
    private final SimpleContainer fuelInv = new SimpleContainer(1);
    private int clientFuelPermille;
    private int clientCruise;

    public FurnaceCartMenu(int id, Inventory inv, int cartId) {
        super(TRMenus.FURNACE_CART.get(), id);
        this.cartId = cartId;
        this.cart = inv.player.level().getEntity(cartId) instanceof MinecartFurnace f ? f : null;

        this.addSlot(new Slot(fuelInv, 0, 80, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {

                return stack.getBurnTime(RecipeType.SMELTING) > 0;
            }
        });
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                this.addSlot(new Slot(inv, 9 + r * 9 + c, 8 + c * 18, 84 + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            this.addSlot(new Slot(inv, c, 8 + c * 18, 142));
        }

        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                if (cart == null || cart.level().isClientSide) return clientFuelPermille;
                return (int) Math.round(cart.getData(TRAttachments.TRAIN_DATA).fuel
                        * 1000.0 / TRConfig.FUEL_CAPACITY.get());
            }

            @Override
            public void set(int v) {
                clientFuelPermille = v;
            }
        });
        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                if (cart == null || cart.level().isClientSide) return clientCruise;
                return (int) Math.round(cart.getData(TRAttachments.TRAIN_DATA).cruise);
            }

            @Override
            public void set(int v) {
                clientCruise = v;
            }
        });
    }

    @Override
    public void broadcastChanges() {
        if (cart != null && !cart.level().isClientSide) convertFuel();
        super.broadcastChanges();
    }

    private void convertFuel() {
        TrainData d = cart.getData(TRAttachments.TRAIN_DATA);
        double cap = TRConfig.FUEL_CAPACITY.get();
        ItemStack s = fuelInv.getItem(0);
        while (!s.isEmpty()) {
            int burn = s.getBurnTime(RecipeType.SMELTING);
            if (burn <= 0 || d.fuel + burn > cap) break;
            ItemStack remainder = s.getCraftingRemainingItem();
            d.fuel += burn;
            s.shrink(1);
            if (s.isEmpty() && !remainder.isEmpty()) {
                fuelInv.setItem(0, remainder);
                break;
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index == 0) {
            if (!this.moveItemStackTo(stack, 1, 37, true)) return ItemStack.EMPTY;
        } else {
            if (!this.moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return cart != null && cart.isAlive() && player.distanceToSqr(cart) <= 64.0;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) {
            this.clearContainer(player, fuelInv);
        }
    }

    public int fuelPermille() {
        return clientFuelPermille;
    }

    public int cruiseGear() {
        return clientCruise;
    }

    public int cartId() {
        return cartId;
    }
}
