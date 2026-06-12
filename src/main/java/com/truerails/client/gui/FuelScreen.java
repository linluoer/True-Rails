package com.truerails.client.gui;

import com.truerails.TrueRails;
import com.truerails.gui.FurnaceCartMenu;
import com.truerails.network.SetCruisePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** 燃料 GUI；右下角按钮展开调速面板（同屏面板，避免容器菜单生命周期问题）。 */
public class FuelScreen extends AbstractContainerScreen<FurnaceCartMenu> {
    private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath(TrueRails.MODID, "textures/gui/fuel_cart.png");
    private static final int[] GEARS = {0, 8, 16, 24, 32, 48};

    private final List<Button> gearButtons = new ArrayList<>();
    private boolean panelOpen;

    public FuelScreen(FurnaceCartMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        gearButtons.clear();
        // 右下角：展开/收起调速面板
        addRenderableWidget(Button.builder(
                        Component.translatable("truerails.gui.speed_btn"), b -> togglePanel())
                .bounds(leftPos + imageWidth + 2, topPos + imageHeight - 22, 56, 18)
                .build());
        for (int i = 0; i < GEARS.length; i++) {
            final int gear = GEARS[i];
            Button btn = Button.builder(
                            Component.literal(gear + " m/s"),
                            b -> PacketDistributor.sendToServer(
                                    new SetCruisePayload(menu.cartId(), (byte) gear)))
                    .bounds(leftPos + imageWidth + 2, topPos + 24 + i * 20, 56, 18)
                    .build();
            btn.visible = false;
            gearButtons.add(btn);
            addRenderableWidget(btn);
        }
    }

    private void togglePanel() {
        panelOpen = !panelOpen;
        for (Button b : gearButtons) b.visible = panelOpen;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (panelOpen) {
            g.drawString(font,
                    Component.translatable("truerails.gui.current", menu.cruiseGear()),
                    leftPos + imageWidth + 2, topPos + 12, 0xFFFFFF);
        }
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.blit(TEX, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
        // 燃料条填充 + 百分比
        int pm = menu.fuelPermille();
        int w = 160 * pm / 1000;
        if (w > 0) g.fill(leftPos + 8, topPos + 18, leftPos + 8 + w, topPos + 28, 0xFFE07820);
        g.drawCenteredString(font, (pm / 10) + "%", leftPos + 88, topPos + 19, 0xFFFFFF);
    }
}
