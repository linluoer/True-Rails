package com.truerails.client;

import com.truerails.TrueRails;
import com.truerails.network.DriveInputPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = TrueRails.MODID, value = Dist.CLIENT)
public final class ClientInput {
    private static byte lastMask;
    private static boolean riding;
    private static Vec3 lastPos;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null && !mc.isPaused()
                && mc.player.getVehicle() instanceof AbstractMinecart cart) {
            riding = true;
            byte mask = 0;
            if (mc.options.keyUp.isDown()) mask |= DriveInputPayload.FWD;
            if (mc.options.keyDown.isDown()) mask |= DriveInputPayload.BACK;
            if (mc.options.keyJump.isDown()) mask |= DriveInputPayload.BRAKE;
            if (mask != lastMask) {
                PacketDistributor.sendToServer(new DriveInputPayload(mask));
                lastMask = mask;
            }
            // M9: 服务端权威下 motion 包不连续，HUD 测速用位置差分
            Vec3 pos = cart.position();
            if (lastPos != null) {
                ClientHudState.actualSpeed =
                        (float) (Math.hypot(pos.x - lastPos.x, pos.z - lastPos.z) * 20.0);
            }
            Vec3 look = mc.player.getLookAngle();
            ClientHudState.reverse = lastPos != null
                    && ((pos.x - lastPos.x) * look.x + (pos.z - lastPos.z) * look.z) < -1.0e-3;
            lastPos = pos;
        } else if (riding) {
            riding = false;
            lastPos = null;
            if (lastMask != 0) {
                PacketDistributor.sendToServer(new DriveInputPayload((byte) 0));
                lastMask = 0;
            }
            ClientHudState.reset();
        }
    }

    /** 乘车时取消原版经验条/等级数字。 */
    @SubscribeEvent
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getVehicle() instanceof AbstractMinecart)) return;
        if (event.getName().equals(VanillaGuiLayers.EXPERIENCE_BAR)
                || event.getName().equals(VanillaGuiLayers.EXPERIENCE_LEVEL)) {
            event.setCanceled(true);
        }
    }
}
