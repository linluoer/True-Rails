package com.truerails.client;

import com.truerails.TRConfig;
import com.truerails.TrueRails;
import com.truerails.registry.TRSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = TrueRails.MODID, value = Dist.CLIENT)
public final class ClientSounds {
    private static double clackTimer;
    private static int squealCooldown;
    private static float lastSpeed;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.isPaused()) return;
        if (!(mc.player.getVehicle() instanceof AbstractMinecart cart)) {
            clackTimer = 0;
            lastSpeed = 0;
            return;
        }

        float speed = ClientHudState.actualSpeed;
        float decel = Math.max(0.0f, lastSpeed - speed) * 20.0f;
        lastSpeed = speed;

        if (speed > 3.0f) {
            clackTimer += 0.05;
            double interval = 4.0 / speed;
            if (clackTimer >= interval) {
                clackTimer = 0;
                float pitch = 0.85f + speed / 64.0f * 0.5f;
                mc.level.playLocalSound(cart.getX(), cart.getY(), cart.getZ(),
                        TRSounds.RAIL_CLACK.get(), SoundSource.NEUTRAL,
                        0.5f, Mth.clamp(pitch, 0.5f, 2.0f), false);
            }
        } else {
            clackTimer = 0;
        }

        boolean braking = mc.options.keyJump.isDown();
        if (braking && speed > 2.0f) {
            if (squealCooldown <= 0) {
                float vol = Mth.clamp(decel / (float) (double) TRConfig.EMERGENCY_BRAKE.get(),
                        0.25f, 1.0f);
                mc.level.playLocalSound(cart.getX(), cart.getY(), cart.getZ(),
                        TRSounds.BRAKE_SQUEAL.get(), SoundSource.NEUTRAL, vol, 1.0f, false);
                squealCooldown = 7;
            }

            if (cart.tickCount % 2 == 0) {
                mc.level.addParticle(ParticleTypes.LAVA,
                        cart.getX(), cart.getY() + 0.1, cart.getZ(), 0.0, 0.0, 0.0);
            }
        }
        if (squealCooldown > 0) squealCooldown--;

        if (speed > TRConfig.MAX_SPEED.get() + 0.5 && cart.tickCount % 3 == 0) {
            mc.level.addParticle(ParticleTypes.CRIT,
                    cart.getX(), cart.getY() + 0.1, cart.getZ(), 0.0, 0.05, 0.0);
        }
    }

    private ClientSounds() {}
}
