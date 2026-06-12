package com.truerails.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.AbstractMinecart;

public final class SpeedHud implements LayeredDraw.Layer {
    private static final ResourceLocation BAR_BG =
            ResourceLocation.fromNamespaceAndPath("truerails", "hud/speed_bar_background");
    private static final ResourceLocation BAR_FILL =
            ResourceLocation.fromNamespaceAndPath("truerails", "hud/speed_bar_fill");
    private static final ResourceLocation CRUISE_MARKER =
            ResourceLocation.fromNamespaceAndPath("truerails", "hud/cruise_marker");
    private static final ResourceLocation ARROW =
            ResourceLocation.fromNamespaceAndPath("truerails", "hud/direction_arrow");
    private static final ResourceLocation FUEL_BG =
            ResourceLocation.fromNamespaceAndPath("truerails", "hud/fuel_bar_mini_bg");
    private static final ResourceLocation FUEL_FILL =
            ResourceLocation.fromNamespaceAndPath("truerails", "hud/fuel_bar_mini_fill");

    private static final float FULL_SCALE = 48.0f;

    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getVehicle() instanceof AbstractMinecart)) return;
        if (mc.options.hideGui) return;

        ClientHudState.displaySpeed =
                Mth.lerp(0.25f, ClientHudState.displaySpeed, ClientHudState.actualSpeed);
        float speed = ClientHudState.displaySpeed;

        int w = g.guiWidth();
        int h = g.guiHeight();
        int x = w / 2 - 91;
        int y = h - 32 + 3;

        g.blitSprite(BAR_BG, x, y, 182, 5);
        int fill = (int) (Mth.clamp(speed / FULL_SCALE, 0.0f, 1.0f) * 182.0f);
        if (fill > 0) {
            g.blitSprite(BAR_FILL, 182, 5, 0, 0, x, y, fill, 5);
        }

        if (ClientHudState.cruise > 0.1f) {
            int cm = (int) (Mth.clamp(ClientHudState.cruise / FULL_SCALE, 0.0f, 1.0f) * 182.0f);
            g.blitSprite(CRUISE_MARKER, x + Mth.clamp(cm - 2, 0, 177), y - 5, 5, 4);
        }

        int ax = x - 9;
        if (ClientHudState.reverse) {
            g.pose().pushPose();
            g.pose().translate(ax * 2 + 5, 0, 0);
            g.pose().scale(-1.0f, 1.0f, 1.0f);
            g.blitSprite(ARROW, ax, y, 5, 5);
            g.pose().popPose();
        } else {
            g.blitSprite(ARROW, ax, y, 5, 5);
        }

        if (ClientHudState.hasFurnace) {
            int fx = x;
            int fy = y - 11;
            g.blitSprite(FUEL_BG, fx, fy, 24, 3);
            int fw = (int) (Mth.clamp(ClientHudState.fuelPct, 0.0f, 1.0f) * 24.0f);
            if (fw > 0) {
                g.blitSprite(FUEL_FILL, 24, 3, 0, 0, fx, fy, fw, 3);
            }
            g.drawString(mc.font, Math.round(ClientHudState.fuelPct * 100) + "%",
                    fx + 27, fy - 2, 0xFFE07820, true);
        }

        String text = String.valueOf(Math.round(speed));
        Font font = mc.font;
        int tx = (w - font.width(text)) / 2;
        int ty = h - 31 - 4;
        int color = speed > FULL_SCALE + 0.5f ? 0xFFFF8800
                : (ClientHudState.reverse ? 0xFF808080 : 0xFF80FF20);
        g.drawString(font, text, tx + 1, ty, 0xFF000000, false);
        g.drawString(font, text, tx - 1, ty, 0xFF000000, false);
        g.drawString(font, text, tx, ty + 1, 0xFF000000, false);
        g.drawString(font, text, tx, ty - 1, 0xFF000000, false);
        g.drawString(font, text, tx, ty, color, false);
    }
}
