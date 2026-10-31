package com.eyelocator.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import org.joml.Matrix4f;

/**
 * 世界内指引：从玩家指向要塞估计点的一条地面直线，以及目标处的路标
 * （地面方框 + 竖直光柱），方便肉眼跟随着走。
 */
public final class WorldGuide {

    private static final float R = 0.33f;
    private static final float G = 1.0f;
    private static final float B = 0.45f;
    private static final float A = 0.85f;

    private WorldGuide() {
    }

    public static void register() {
        WorldRenderEvents.LAST.register(WorldGuide::render);
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            return;
        }
        StrongholdLocator locator = EyeLocatorClient.LOCATOR;
        if (!locator.hasEstimate()) {
            return;
        }

        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) {
            return;
        }

        Vec3d cam = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);

        Matrix4f mat = matrices.peek().getPositionMatrix();
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getLines());

        double tx = locator.estX();
        double tz = locator.estZ();
        double y = mc.player.getY() + 0.08;

        // 1) 玩家 -> 目标的地面直线
        line(mat, vc, mc.player.getX(), y, mc.player.getZ(), tx, y, tz);

        // 2) 目标处的地面方框（边长 4）
        double h = 2.0;
        line(mat, vc, tx - h, y, tz - h, tx + h, y, tz - h);
        line(mat, vc, tx + h, y, tz - h, tx + h, y, tz + h);
        line(mat, vc, tx + h, y, tz + h, tx - h, y, tz + h);
        line(mat, vc, tx - h, y, tz + h, tx - h, y, tz - h);

        // 3) 目标处的竖直光柱
        line(mat, vc, tx, y, tz, tx, y + 8.0, tz);

        matrices.pop();
    }

    private static void line(Matrix4f mat, VertexConsumer vc,
                             double x1, double y1, double z1,
                             double x2, double y2, double z2) {
        vc.vertex(mat, (float) x1, (float) y1, (float) z1).color(R, G, B, A);
        vc.vertex(mat, (float) x2, (float) y2, (float) z2).color(R, G, B, A);
    }
}
