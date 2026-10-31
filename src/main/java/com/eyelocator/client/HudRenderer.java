package com.eyelocator.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 2D HUD：
 * <ul>
 *   <li>目标位置投影到屏幕上，用一个"末影之眼"图标作为路标（跟随视角、贴边、带距离）。</li>
 *   <li>左上角信息面板：坐标 / 距离 / 样本数 / 置信度。</li>
 * </ul>
 */
public final class HudRenderer implements HudRenderCallback {

    private static final Identifier ENDER_EYE =
            Identifier.of("minecraft", "textures/item/ender_eye.png");

    private static final int PANEL_BG = 0x90101010;
    private static final int COLOR_ACCENT = 0xFF55FF55;
    private static final int COLOR_ADVICE = 0xFF4FD8FF;
    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_DIM = 0xFF9A9A9A;
    private static final int RING_ACCENT = 0xE055FF55;
    private static final int RING_ADVICE = 0xE04FD8FF;

    private static final int MARKER_RADIUS = 18;
    private static final int MARGIN = 26;

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

        String coordLine = String.format("末地要塞 ≈  X:%.0f  Z:%.0f", locator.estX(), locator.estZ());
        String infoLine = String.format("距离 %d 格   样本 %d 条   置信度 %d%%",
                Math.round(distance), locator.sampleCount(), Math.round(locator.confidence() * 100));
        String hintLine = "[K] 清除记录";

        drawPanel(context, mc, new String[]{coordLine, infoLine, hintLine},
                new int[]{COLOR_ACCENT, COLOR_TEXT, COLOR_DIM});

        drawWaypoint(context, mc, player, locator.estX(), locator.estZ(),
                distance, RING_ACCENT, "末地要塞", COLOR_ACCENT);
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
                advice.travel, RING_ADVICE, "下一投掷点", COLOR_ADVICE);
    }

    /** 把一个世界坐标目标投影到屏幕，并画出末影之眼图标路标。 */
    private void drawWaypoint(DrawContext context, MinecraftClient mc, ClientPlayerEntity player,
                              double targetX, double targetZ, double distance,
                              int ringColor, String label, int labelColor) {
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
            // 目标在身后：贴到左右边缘
            sx = rx >= 0 ? screenW - MARGIN : MARGIN;
            sy = screenH * 0.5f;
        }

        int cx = Math.round(MathHelper.clamp(sx, MARGIN, screenW - MARGIN));
        int cy = Math.round(MathHelper.clamp(sy, MARGIN, screenH - MARGIN));

        // 圆形底 + 彩色圆环
        fillCircle(context, cx, cy, MARKER_RADIUS, ringColor);
        fillCircle(context, cx, cy, MARKER_RADIUS - 3, 0xE0181818);

        // 末影之眼图标
        int iconSize = 22;
        context.drawTexture(ENDER_EYE, cx - iconSize / 2, cy - iconSize / 2,
                iconSize, iconSize, 0.0f, 0.0f, 16, 16, 16, 16);

        // 距离（下方）与标签（上方）
        String distText = Math.round(distance) + "m";
        context.drawTextWithShadow(mc.textRenderer, Text.literal(distText),
                cx - mc.textRenderer.getWidth(distText) / 2, cy + MARKER_RADIUS + 3, 0xFFFFFFFF);
        context.drawTextWithShadow(mc.textRenderer, Text.literal(label),
                cx - mc.textRenderer.getWidth(label) / 2, cy - MARKER_RADIUS - 12, labelColor);
    }

    private static void fillCircle(DrawContext context, int cx, int cy, int radius, int color) {
        int r2 = radius * radius;
        for (int dy = -radius; dy <= radius; dy++) {
            int w = (int) Math.floor(Math.sqrt((double) (r2 - dy * dy)));
            context.fill(cx - w, cy + dy, cx + w, cy + dy + 1, color);
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
