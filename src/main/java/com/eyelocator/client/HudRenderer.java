package com.eyelocator.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 2D HUD：
 * <ul>
 *   <li>目标位置投影到屏幕上，用手绘的极简菱形路标标记（跟随视角、贴边、带距离）。</li>
 *   <li>左上角信息面板：坐标 / 距离 / 样本数 / 置信度。</li>
 * </ul>
 */
public final class HudRenderer implements HudRenderCallback {

    private static final int PANEL_BG = 0x90101010;
    private static final int COLOR_ACCENT = 0xFF55FF55;
    private static final int COLOR_ADVICE = 0xFF4FD8FF;
    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_DIM = 0xFF9A9A9A;

    private static final int MARKER_RADIUS = 10;
    private static final int MARGIN = 22;

    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null || mc.options.hudHidden) {
            return;
        }

        StrongholdLocator locator = EyeLocatorClient.LOCATOR;

        if (locator.hasEstimate()) {
            renderEstimate(context, mc, player, locator);
            return;
        }

        NextThrowAdvisor.Advice advice = NextThrowAdvisor.advise(player, locator);
        if (advice != null) {
            renderAdvice(context, mc, player, advice);
        } else if (locator.sampleCount() > 0) {
            context.drawTextWithShadow(mc.textRenderer,
                    Text.literal("末影之眼方位已记录，再扔一次即可三角定位"), 6, 6, COLOR_DIM);
        }
    }

    private void renderEstimate(DrawContext context, MinecraftClient mc, ClientPlayerEntity player,
                                StrongholdLocator locator) {
        double dx = locator.estX() - player.getX();
        double dz = locator.estZ() - player.getZ();
        double distance = Math.hypot(dx, dz);

        drawPanel(context, mc,
                new String[]{
                        String.format("末地要塞 ≈  X:%.0f  Z:%.0f", locator.estX(), locator.estZ()),
                        String.format("距离 %d 格   样本 %d 条   置信度 %d%%",
                                Math.round(distance), locator.sampleCount(),
                                Math.round(locator.confidence() * 100)),
                        "[K] 清除记录"
                },
                new int[]{COLOR_ACCENT, COLOR_TEXT, COLOR_DIM});

        drawWaypoint(context, mc, player, locator.estX(), locator.estZ(),
                distance, COLOR_ACCENT, null);
    }

    private void renderAdvice(DrawContext context, MinecraftClient mc, ClientPlayerEntity player,
                              NextThrowAdvisor.Advice advice) {
        if (advice.alreadyOptimal) {
            drawPanel(context, mc,
                    new String[]{"当前站位已与首次方位错开足够远", "直接原地再扔一次即可锁定要塞", "[K] 清除记录"},
                    new int[]{COLOR_ADVICE, COLOR_TEXT, COLOR_DIM});
            return;
        }

        drawPanel(context, mc,
                new String[]{
                        String.format("下一最佳投掷点 ≈  X:%.0f  Z:%.0f", advice.x, advice.z),
                        String.format("距你 %d 格 · 走到后扔一次即可锁定", Math.round(advice.travel)),
                        "[K] 清除记录"
                },
                new int[]{COLOR_ADVICE, COLOR_TEXT, COLOR_DIM});

        drawWaypoint(context, mc, player, advice.x, advice.z,
                advice.travel, COLOR_ADVICE, "投掷点");
    }

    /** 把世界坐标目标投影到屏幕，画极简菱形路标。 */
    private void drawWaypoint(DrawContext context, MinecraftClient mc, ClientPlayerEntity player,
                              double targetX, double targetZ, double distance,
                              int color, String label) {
        Vec3d camPos = mc.gameRenderer.getCamera().getPos();
        Quaternionf rotation = mc.gameRenderer.getCamera().getRotation();

        Vector3f forward = rotation.transform(new Vector3f(0, 0, -1));
        Vector3f right = rotation.transform(new Vector3f(1, 0, 0));
        Vector3f up = rotation.transform(new Vector3f(0, 1, 0));

        double targetY = player.getY() + 1.2;
        Vector3f rel = new Vector3f(
                (float) (targetX - camPos.x),
                (float) (targetY - camPos.y),
                (float) (targetZ - camPos.z));

        float rx = rel.dot(right);
        float ry = rel.dot(up);
        float rz = rel.dot(forward);

        int screenW = context.getScaledWindowWidth();
        int screenH = context.getScaledWindowHeight();

        float tanY = (float) Math.tan(Math.toRadians((double) mc.options.getFov().getValue() * 0.5));
        float aspect = (float) screenW / (float) screenH;
        float tanX = tanY * aspect;

        float sx;
        float sy;
        if (rz > 0.5f) {
            float ndcX = (rx / rz) / tanX;
            float ndcY = (ry / rz) / tanY;
            sx = (ndcX * 0.5f + 0.5f) * screenW;
            sy = (0.5f - ndcY * 0.5f) * screenH;
        } else {
            sx = rx >= 0 ? screenW - MARGIN : MARGIN;
            sy = screenH * 0.5f;
        }

        int cx = Math.round(MathHelper.clamp(sx, MARGIN, screenW - MARGIN));
        int cy = Math.round(MathHelper.clamp(sy, MARGIN, screenH - MARGIN));

        // 深色描边提升可读性，再画彩色菱形
        drawDiamondOutlined(context, cx, cy, MARKER_RADIUS, 0x90000000, color);
        // 中心点
        context.fill(cx - 1, cy - 1, cx + 1, cy + 1, color);

        // 距离（下方）
        String distText = Math.round(distance) + "m";
        context.drawTextWithShadow(mc.textRenderer, Text.literal(distText),
                cx - mc.textRenderer.getWidth(distText) / 2, cy + MARKER_RADIUS + 4, 0xFFFFFFFF);

        // 标签（上方，可选）
        if (label != null) {
            context.drawTextWithShadow(mc.textRenderer, Text.literal(label),
                    cx - mc.textRenderer.getWidth(label) / 2, cy - MARKER_RADIUS - 12, color);
        }
    }

    private static void drawDiamondOutlined(DrawContext context, int cx, int cy, int r,
                                            int outlineColor, int color) {
        drawDiamond(context, cx, cy, r + 1, outlineColor);
        drawDiamond(context, cx, cy, r, color);
    }

    private static void drawDiamond(DrawContext context, int cx, int cy, int r, int color) {
        drawLine(context, cx, cy - r, cx + r, cy, color);
        drawLine(context, cx + r, cy, cx, cy + r, color);
        drawLine(context, cx, cy + r, cx - r, cy, color);
        drawLine(context, cx - r, cy, cx, cy - r, color);
    }

    private static void drawLine(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1);
        int dy = -Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            context.fill(x1, y1, x1 + 1, y1 + 1, color);
            if (x1 == x2 && y1 == y2) {
                return;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x1 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y1 += sy;
            }
        }
    }

    private void drawPanel(DrawContext context, MinecraftClient mc, String[] lines, int[] colors) {
        int x = 6;
        int y = 6;
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, mc.textRenderer.getWidth(line));
        }
        context.fill(x - 3, y - 3, x + width + 5, y + 11 * lines.length + 1, PANEL_BG);
        for (int i = 0; i < lines.length; i++) {
            context.drawTextWithShadow(mc.textRenderer, Text.literal(lines[i]), x, y + i * 11, colors[i]);
        }
    }
}
