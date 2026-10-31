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

/** 2D HUD：投影路标（支持样式/颜色/大小配置）+ 信息面板 + 已归档要塞标记。 */
public final class HudRenderer implements HudRenderCallback {

    private static final int PANEL_BG = 0x90101010;
    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_DIM = 0xFF9A9A9A;
    private static final int BASE_RADIUS = 11;
    private static final int MARGIN = 22;

    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        EyeLocatorConfig config = EyeLocatorConfig.get();
        if (!config.enabled) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null || mc.options.hudHidden) {
            return;
        }

        Vec3d playerPos = player.getLerpedPos(tickCounter.getTickDelta(false));
        StrongholdLocator locator = EyeLocatorClient.LOCATOR;

        if (locator.hasEstimate()) {
            renderEstimate(context, mc, playerPos, locator, config);
        } else {
            NextThrowAdvisor.Advice advice = NextThrowAdvisor.advise(playerPos.x, playerPos.z, locator);
            if (advice != null) {
                renderAdvice(context, mc, advice, config);
            } else if (locator.sampleCount() > 0) {
                context.drawTextWithShadow(mc.textRenderer,
                        Text.literal("末影之眼方位已记录，再扔一次即可三角定位"), 6, 6, COLOR_DIM);
            }
        }

        boolean searchingNew = config.hideArchivedWhileSearching && !locator.hasEstimate();
        if (config.showArchived && !searchingNew) {
            drawArchivedMarkers(context, mc, config);
        }
    }

    private void renderEstimate(DrawContext context, MinecraftClient mc, Vec3d playerPos,
                                StrongholdLocator locator, EyeLocatorConfig config) {
        double distance = Math.hypot(locator.estX() - playerPos.x, locator.estZ() - playerPos.z);

        if (config.showPanel) {
            String sampleInfo = locator.excluded() > 0
                    ? String.format("样本 %d（剔除 %d）", locator.sampleCount(), locator.excluded())
                    : String.format("样本 %d", locator.sampleCount());
            drawPanel(context, mc, new String[]{
                    String.format("末地要塞 ≈  X:%.0f  Z:%.0f", locator.estX(), locator.estZ()),
                    String.format("距离 %d 格 · %s · 置信度 %d%%",
                            Math.round(distance), sampleInfo, Math.round(locator.confidence() * 100)),
                    String.format("归档 %d 个要塞 · [K] 清除", StrongholdRegistry.size())
            }, new int[]{config.strongholdColor(), COLOR_TEXT, COLOR_DIM});
        }

        drawWaypoint(context, mc, locator.estX(), locator.estZ(), distance,
                config.strongholdColor(), null, config);
    }

    private void renderAdvice(DrawContext context, MinecraftClient mc,
                              NextThrowAdvisor.Advice advice, EyeLocatorConfig config) {
        if (advice.alreadyOptimal) {
            if (config.showPanel) {
                drawPanel(context, mc,
                        new String[]{"当前站位已与首次方位错开足够远", "直接原地再扔一次即可锁定要塞", "[K] 清除记录"},
                        new int[]{config.adviceColor(), COLOR_TEXT, COLOR_DIM});
            }
            return;
        }

        if (config.showPanel) {
            drawPanel(context, mc, new String[]{
                    String.format("下一最佳投掷点 ≈  X:%.0f  Z:%.0f", advice.x, advice.z),
                    String.format("距你 %d 格 · 走到后扔一次即可锁定", Math.round(advice.travel)),
                    "[K] 清除记录"
            }, new int[]{config.adviceColor(), COLOR_TEXT, COLOR_DIM});
        }

        drawWaypoint(context, mc, advice.x, advice.z, advice.travel,
                config.adviceColor(), "投掷点", config);
    }

    private void drawWaypoint(DrawContext context, MinecraftClient mc,
                              double targetX, double targetZ, double distance,
                              int color, String label, EyeLocatorConfig config) {
        int radius = Math.max(4, (int) Math.round(BASE_RADIUS * clamp(config.markerScale, 0.4, 3.0)));
        int[] pos = project(mc, context, targetX, targetZ, radius + 6);
        int cx = pos[0];
        int cy = pos[1];

        drawMarker(context, cx, cy, radius, (float) config.scaledThickness(), color, config.markerStyle);

        if (config.showDistance) {
            String distText = Math.round(distance) + "m";
            context.drawTextWithShadow(mc.textRenderer, Text.literal(distText),
                    cx - mc.textRenderer.getWidth(distText) / 2, cy + radius + 5, 0xFFFFFFFF);
        }
        if (config.showLabels && label != null) {
            context.drawTextWithShadow(mc.textRenderer, Text.literal(label),
                    cx - mc.textRenderer.getWidth(label) / 2, cy - radius - 13, color);
        }
    }

    /** 把已归档的其它要塞画成较小、较暗的路标。 */
    private void drawArchivedMarkers(DrawContext context, MinecraftClient mc, EyeLocatorConfig config) {
        String dimension = mc.world.getRegistryKey().getValue().toString();
        StrongholdLocator locator = EyeLocatorClient.LOCATOR;

        int radius = Math.max(3, (int) Math.round(BASE_RADIUS * 0.6 * clamp(config.markerScale, 0.4, 3.0)));
        float thickness = Math.max(1.0f, (float) (config.scaledThickness() * 0.7));

        for (StrongholdRegistry.Entry entry : StrongholdRegistry.entries()) {
            if (!entry.dimension.equals(dimension)) {
                continue;
            }
            if (locator.hasEstimate()
                    && Math.hypot(entry.x - locator.estX(), entry.z - locator.estZ()) < 128) {
                continue; // 与当前定位重叠
            }
            int[] pos = project(mc, context, entry.x, entry.z, radius + 6);
            drawMarker(context, pos[0], pos[1], radius, thickness, config.archivedColor(), config.markerStyle);
        }
    }

    /** 世界坐标 -> 屏幕坐标（水平方向），超出屏幕贴边。 */
    private static int[] project(MinecraftClient mc, DrawContext context,
                                 double targetX, double targetZ, int margin) {
        Vec3d camPos = mc.gameRenderer.getCamera().getPos();
        Quaternionf rotation = mc.gameRenderer.getCamera().getRotation();

        Vector3f forward = rotation.transform(new Vector3f(0, 0, -1));
        Vector3f right = rotation.transform(new Vector3f(1, 0, 0));
        Vector3f up = rotation.transform(new Vector3f(0, 1, 0));

        // 目标高度锚定相机高度，垂直移动时路标不跳动
        Vector3f rel = new Vector3f(
                (float) (targetX - camPos.x), 0.0f, (float) (targetZ - camPos.z));

        float rx = rel.dot(right);
        float ry = rel.dot(up);
        float rz = rel.dot(forward);

        int screenW = context.getScaledWindowWidth();
        int screenH = context.getScaledWindowHeight();

        float tanY = (float) Math.tan(Math.toRadians((double) mc.options.getFov().getValue() * 0.5));
        float tanX = tanY * ((float) screenW / (float) screenH);

        int m = Math.max(MARGIN, margin);
        float sx;
        float sy;
        if (rz > 0.5f) {
            float ndcX = (rx / rz) / tanX;
            float ndcY = (ry / rz) / tanY;
            sx = (ndcX * 0.5f + 0.5f) * screenW;
            sy = (0.5f - ndcY * 0.5f) * screenH;
        } else {
            sx = rx >= 0 ? screenW - m : m;
            sy = screenH * 0.5f;
        }
        int cx = Math.round(MathHelper.clamp(sx, m, screenW - m));
        int cy = Math.round(MathHelper.clamp(sy, m, screenH - m));
        return new int[]{cx, cy};
    }

    // ---- 抗锯齿自绘图形 ----

    private static void drawMarker(DrawContext context, int cx, int cy, int r,
                                   float thickness, int color, String style) {
        boolean circle = "circle".equalsIgnoreCase(style);
        boolean cross = "cross".equalsIgnoreCase(style);
        boolean diamond = !circle && !cross;

        double half = thickness * 0.5;
        int bound = (int) Math.ceil(r + half + 2.0);
        int cr = (color >> 16) & 0xFF;
        int cg = (color >> 8) & 0xFF;
        int cb = color & 0xFF;
        int baseA = (color >>> 24) & 0xFF;

        int ax = cx, ay = cy - r;
        int bx = cx + r, by = cy;
        int dx = cx, dy = cy + r;
        int ex = cx - r, ey = cy;

        for (int py = cy - bound; py <= cy + bound; py++) {
            for (int px = cx - bound; px <= cx + bound; px++) {
                double sx = px + 0.5;
                double sy = py + 0.5;
                double dist;
                if (circle) {
                    dist = Math.abs(Math.hypot(sx - cx, sy - cy) - r);
                } else if (cross) {
                    dist = Math.min(
                            distToSegment(sx, sy, cx - r, cy, cx + r, cy),
                            distToSegment(sx, sy, cx, cy - r, cx, cy + r));
                } else {
                    dist = Math.min(
                            Math.min(distToSegment(sx, sy, ax, ay, bx, by), distToSegment(sx, sy, bx, by, dx, dy)),
                            Math.min(distToSegment(sx, sy, dx, dy, ex, ey), distToSegment(sx, sy, ex, ey, ax, ay)));
                }

                double cover = clamp01(half + 0.5 - dist);
                if (cover > 0.01) {
                    int a = (int) Math.round(cover * baseA);
                    if (a > 0) {
                        context.fill(px, py, px + 1, py + 1, (a << 24) | (cr << 16) | (cg << 8) | cb);
                    }
                } else {
                    double halo = clamp01(half + 2.0 + 0.5 - dist) * 0.32;
                    int a = (int) Math.round(halo * 70);
                    if (a > 0) {
                        context.fill(px, py, px + 1, py + 1, a << 24);
                    }
                }
            }
        }

        if (diamond || circle) {
            drawSmoothDot(context, cx, cy, 1.8, color);
        }
    }

    private static void drawSmoothDot(DrawContext context, int cx, int cy, double radius, int color) {
        int bound = (int) Math.ceil(radius + 2.0);
        int cr = (color >> 16) & 0xFF;
        int cg = (color >> 8) & 0xFF;
        int cb = color & 0xFF;
        int baseA = (color >>> 24) & 0xFF;
        for (int py = cy - bound; py <= cy + bound; py++) {
            for (int px = cx - bound; px <= cx + bound; px++) {
                double dist = Math.hypot(px + 0.5 - cx, py + 0.5 - cy);
                double cover = clamp01(radius + 0.5 - dist);
                if (cover > 0.01) {
                    int a = (int) Math.round(cover * baseA);
                    if (a > 0) {
                        context.fill(px, py, px + 1, py + 1, (a << 24) | (cr << 16) | (cg << 8) | cb);
                    }
                }
            }
        }
    }

    private static double distToSegment(double px, double py,
                                        double ax, double ay, double bx, double by) {
        double abx = bx - ax;
        double aby = by - ay;
        double apx = px - ax;
        double apy = py - ay;
        double len2 = abx * abx + aby * aby;
        double t = len2 == 0.0 ? 0.0 : (apx * abx + apy * aby) / len2;
        t = clamp01(t);
        double dx = ax + t * abx - px;
        double dy = ay + t * aby - py;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
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
