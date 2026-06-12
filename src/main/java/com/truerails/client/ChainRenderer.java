package com.truerails.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.truerails.TrueRails;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** 相邻车间渲染下垂链条（原版链条贴图，§2.6）。 */
@EventBusSubscriber(modid = TrueRails.MODID, value = Dist.CLIENT)
public final class ChainRenderer {
    private static final ResourceLocation CHAIN_TEX =
            ResourceLocation.withDefaultNamespace("textures/block/chain.png");
    private static final int SEGMENTS = 8;
    private static final float HALF_W = 0.05f;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ClientLinkState.LINKS.isEmpty()) return;

        ClientLinkState.purgeExpired();

        // 若 getPartialTick() 返回 DeltaTracker 而非 float，
        // 改为 event.getPartialTick().getGameTimeDeltaPartialTick(false)
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(CHAIN_TEX));

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = pose.last().pose();

        for (var entry : ClientLinkState.LINKS.entrySet()) {
            Entity a = mc.level.getEntity(entry.getKey());
            Entity b = mc.level.getEntity(entry.getValue().partnerId());
            if (a == null || b == null) continue;

            Vec3 pa = a.getPosition(pt).add(0, 0.45, 0);
            Vec3 pb = b.getPosition(pt).add(0, 0.45, 0);
            double dist = pa.distanceTo(pb);
            if (dist < 0.1 || dist > 12.0) continue;

            // 下垂量：近时松弛、绷紧时变直
            float sag = (float) Math.min(0.35, Math.max(0.02, (2.2 - dist) * 0.25 + 0.1));

            int light = LevelRenderer.getLightColor(mc.level,
                    BlockPos.containing(pa.add(pb).scale(0.5)));

            Vec3 dir = pb.subtract(pa);
            Vec3 perp = new Vec3(-dir.z, 0, dir.x).normalize().scale(HALF_W);
            if (perp.lengthSqr() < 1.0e-8) perp = new Vec3(HALF_W, 0, 0);

            Vec3 prev = pa;
            for (int i = 1; i <= SEGMENTS; i++) {
                double t = (double) i / SEGMENTS;
                double tp = (double) (i - 1) / SEGMENTS;
                Vec3 p = pa.lerp(pb, t).subtract(0, sag * 4 * t * (1 - t), 0);
                // 垂直带（链条正面）
                quad(vc, mat, light,
                        prev.add(0, HALF_W, 0), p.add(0, HALF_W, 0),
                        p.subtract(0, HALF_W, 0), prev.subtract(0, HALF_W, 0));
                // 水平带（交叉面）
                quad(vc, mat, light,
                        prev.add(perp), p.add(perp), p.subtract(perp), prev.subtract(perp));
                prev = p;
                // tp 未用于 UV（每段 v 取 0..1），保留变量名以示意
            }
        }
        pose.popPose();
        buffers.endBatch(RenderType.entityCutoutNoCull(CHAIN_TEX));
    }

    private static void quad(VertexConsumer vc, Matrix4f mat, int light,
                             Vec3 v1, Vec3 v2, Vec3 v3, Vec3 v4) {
        // 链条贴图左侧链环条带 u: 0..3/16
        vertex(vc, mat, v1, 0.0f, 0.0f, light);
        vertex(vc, mat, v2, 0.0f, 1.0f, light);
        vertex(vc, mat, v3, 0.1875f, 1.0f, light);
        vertex(vc, mat, v4, 0.1875f, 0.0f, light);
        // 反面（NoCull 下可省，但部分驱动背面剔除行为不一致，补一份保险）
        vertex(vc, mat, v4, 0.1875f, 0.0f, light);
        vertex(vc, mat, v3, 0.1875f, 1.0f, light);
        vertex(vc, mat, v2, 0.0f, 1.0f, light);
        vertex(vc, mat, v1, 0.0f, 0.0f, light);
    }

    private static void vertex(VertexConsumer vc, Matrix4f mat, Vec3 p, float u, float v, int light) {
        vc.addVertex(mat, (float) p.x, (float) p.y, (float) p.z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(0.0f, 1.0f, 0.0f);
    }
}
