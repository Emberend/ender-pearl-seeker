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
 * 世界内指引：
 * <ul>
 *   <li>已定位：绿色 —— 玩家到要塞的一条地面直线 + 目标处路标（方框 + 光柱）。</li>
 *   <li>只有 1 条方位：青色 —— 玩家到"下一最佳投掷点"的指引线 + 该点路标。</li>
 * </ul>
 */
public final class WorldGuide {

    // 要塞（绿色）
    private static final float[] GREEN = {0.33f, 1.0f, 0.45f, 0.85f};
    // 推荐投掷点（青色）
    private static final float[] CYAN = {0.31f, 0.85f, 1.0f, 0.85f};

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

        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) {
            return;
        }

        double targetX;
        double targetZ;
        float[] color;
        String label;

        if (locator.hasEstimate()) {
            targetX = locator.estX();
            targetZ = locator.estZ();
            color = GREEN;
            label = "stronghold";
        } else {
            NextThrowAdvisor.Advice advice = NextThrowAdvisor.advise(mc.player, locator);
            if (advice == null || advice.alreadyOptimal) {
                return;
            }
            targetX = advice.x;
            targetZ = advice.z;
            color = CYAN;
            label = "next-throw";
        }

        Vec3d cam = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);

        Matrix4f mat = matrices.peek().getPositionMatrix();
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getLines());

        double y = mc.player.getY() + 0.08;

        // 1) 玩家 -> 目标的地面直线
        line(mat, vc, mc.player.getX(), y, mc.player.getZ(), targetX, y, targetZ, color);

        // 2) 目标处的地面方框（边长 4）
        double h = 2.0;
        line(mat, vc, targetX - h, y, targetZ - h, targetX + h, y, targetZ - h, color);
        line(mat, vc, targetX + h, y, targetZ - h, targetX + h, y, targetZ + h, color);
        line(mat, vc, targetX + h, y, targetZ + h, targetX - h, y, targetZ + h, color);
        line(mat, vc, targetX - h, y, targetZ + h, targetX - h, y, targetZ - h, color);

        // 3) 目标处的竖直光柱（推荐点矮一些，便于区分）
        double beam = "next-throw".equals(label) ? 4.0 : 8.0;
        line(mat, vc, targetX, y, targetZ, targetX, y + beam, targetZ, color);

        matrices.pop();
    }

    private static void line(Matrix4f mat, VertexConsumer vc,
                             double x1, double y1, double z1,
                             double x2, double y2, double z2, float[] color) {
        // RenderLayer.getLines() 的顶点格式为 position + color + normal，normal 必须提供
        vc.vertex(mat, (float) x1, (float) y1, (float) z1)
                .color(color[0], color[1], color[2], color[3]).normal(0.0f, 1.0f, 0.0f);
        vc.vertex(mat, (float) x2, (float) y2, (float) z2)
                .color(color[0], color[1], color[2], color[3]).normal(0.0f, 1.0f, 0.0f);
    }
}
