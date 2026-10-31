package com.eyelocator.forge;

import com.eyelocator.core.ChannelManager;
import com.eyelocator.core.LocatorChannel;
import com.eyelocator.core.NextThrowAdvisor;
import com.eyelocator.core.StrongholdLocator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * HUD：信息面板 + 屏幕空间路标。
 *
 * <p>所有图形都是逐像素自绘（配合 alpha 覆盖率做抗锯齿），因此不依赖纹理资源。</p>
 */
public final class ForgeHud {

    private static final int PANEL_BG = 0x90101010;
    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_DIM = 0xFF9A9A9A;
    private static final int COLOR_WARN = 0xFFFFA0A0;
    private static final int BASE_RADIUS = 11;
    private static final int MARGIN = 22;

    private ForgeHud() {
    }

    public static void render(GuiGraphics graphics) {
        ForgeConfig config = ForgeConfig.get();
        if (!config.enabled) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) {
            return;
        }

        Vec3 playerPos = mc.player.position();
        LocatorChannel channel = ChannelManager.active();
        StrongholdLocator locator = channel.locator;

        if (locator.hasEstimate()) {
            renderEstimate(graphics, mc, playerPos, channel, config);
        } else {
            NextThrowAdvisor.Advice advice =
                    NextThrowAdvisor.advise(playerPos.x, playerPos.z, locator, config.baseline);
            if (advice != null) {
                renderAdvice(graphics, mc, advice, channel, config);
            } else if (locator.sampleCount() > 0) {
                graphics.drawString(mc.font,
                        Component.literal("方位已记录，再扔一次即可三角定位"), 6, 6, COLOR_DIM, true);
            }
        }

        boolean searchingNew = config.hideArchivedWhileSearching && !locator.hasEstimate();
        if (config.showArchived && !searchingNew) {
            drawArchivedMarkers(graphics, mc, config);
        }
    }

    private static void renderEstimate(GuiGraphics graphics, Minecraft mc, Vec3 playerPos,
                                       LocatorChannel channel, ForgeConfig config) {
        StrongholdLocator locator = channel.locator;
        double distance = Math.hypot(locator.estX() - playerPos.x, locator.estZ() - playerPos.z);

        if (config.showPanel) {
            String sampleInfo = locator.excluded() > 0
                    ? String.format("样本 %d（剔除 %d）", locator.sampleCount(), locator.excluded())
                    : String.format("样本 %d", locator.sampleCount());
            drawPanel(graphics, mc, new String[]{
                    String.format("%s ≈  X:%.0f  Z:%.0f", channel.displayName,
                            locator.estX(), locator.estZ()),
                    String.format("距离 %d 格 · %s · 置信度 %d%%",
                            Math.round(distance), sampleInfo, Math.round(locator.confidence() * 100)),
                    String.format("归档 %d 个 · [K] 结束 · [I] 管理界面", ForgeRegistry.size(channel.id))
            }, new int[]{config.strongholdColor(), COLOR_TEXT, COLOR_DIM});
        }

        drawWaypoint(graphics, mc, locator.estX(), locator.estZ(), distance,
                config.strongholdColor(), null, config);
    }

    private static void renderAdvice(GuiGraphics graphics, Minecraft mc,
                                     NextThrowAdvisor.Advice advice, LocatorChannel channel,
                                     ForgeConfig config) {
        if (advice.kind == NextThrowAdvisor.Kind.THROW_HERE) {
            if (config.showPanel) {
                drawPanel(graphics, mc,
                        new String[]{"当前站位已与首次方位错开足够远", "直接原地再扔一次即可锁定", "[K] 结束当前定位"},
                        new int[]{config.adviceColor(), COLOR_TEXT, COLOR_DIM});
            }
            return;
        }

        String title;
        String detail;
        if (advice.kind == NextThrowAdvisor.Kind.RETHROW) {
            // 两次方位大概指向了不同要塞，继续横移只会更偏
            title = "两次方位对不上，可能指向不同要塞";
            detail = advice.travel < 1.0
                    ? "就在原地再扔一次，会自动剔除矛盾的那条方位"
                    : String.format("往标记处侧移 %d 格再扔一次，会自动剔除矛盾方位",
                            Math.round(advice.travel));
        } else if (advice.kind == NextThrowAdvisor.Kind.MOVE_WIDER) {
            title = "两条方位夹角太小，交点不可靠";
            detail = String.format("往标记处横向多走 %d 格再扔一次", Math.round(advice.travel));
        } else {
            title = String.format("下一最佳投掷点 ≈  X:%.0f  Z:%.0f", advice.x, advice.z);
            detail = String.format("距你 %d 格 · 走到后扔一次即可锁定", Math.round(advice.travel));
        }

        if (config.showPanel) {
            drawPanel(graphics, mc, new String[]{title, detail, "[K] 结束当前定位"},
                    new int[]{advice.kind == NextThrowAdvisor.Kind.MOVE ? config.adviceColor() : COLOR_WARN,
                            COLOR_TEXT, COLOR_DIM});
        }

        drawWaypoint(graphics, mc, advice.x, advice.z, advice.travel,
                advice.kind == NextThrowAdvisor.Kind.MOVE ? config.adviceColor() : COLOR_WARN,
                advice.kind == NextThrowAdvisor.Kind.RETHROW ? "重投点" : "投掷点", config);
    }

    private static void drawWaypoint(GuiGraphics graphics, Minecraft mc,
                                     double targetX, double targetZ, double distance,
                                     int color, String label, ForgeConfig config) {
        int radius = Math.max(4, (int) Math.round(BASE_RADIUS * clamp(config.markerScale, 0.4, 3.0)));
        int[] pos = project(graphics, mc, targetX, targetZ, radius + 6);
        int cx = pos[0];
        int cy = pos[1];

        drawMarker(graphics, cx, cy, radius, (float) config.scaledThickness(), color, config.markerStyle);

        if (config.showDistance) {
            String distText = Math.round(distance) + "m";
            graphics.drawString(mc.font, Component.literal(distText),
                    cx - mc.font.width(distText) / 2, cy + radius + 5, 0xFFFFFFFF, true);
        }
        if (config.showLabels && label != null) {
            graphics.drawString(mc.font, Component.literal(label),
                    cx - mc.font.width(label) / 2, cy - radius - 13, color, true);
        }
    }

    private static void drawArchivedMarkers(GuiGraphics graphics, Minecraft mc, ForgeConfig config) {
        String dimension = mc.level.dimension().location().toString();
        StrongholdLocator locator = ChannelManager.active().locator;

        int radius = Math.max(3, (int) Math.round(BASE_RADIUS * 0.6 * clamp(config.markerScale, 0.4, 3.0)));
        float thickness = Math.max(1.0f, (float) (config.scaledThickness() * 0.7));

        for (ForgeRegistry.Entry entry : ForgeRegistry.entries()) {
            if (!entry.dimension.equals(dimension)) {
                continue;
            }
            if (locator.hasEstimate()
                    && Math.hypot(entry.x - locator.estX(), entry.z - locator.estZ()) < 128) {
                continue; // 与当前定位重叠
            }
            int[] pos = project(graphics, mc, entry.x, entry.z, radius + 6);
            drawMarker(graphics, pos[0], pos[1], radius, thickness, config.archivedColor(), config.markerStyle);

            if (config.showLabels) {
                String text = entry.name;
                if (text == null || text.isEmpty()) {
                    LocatorChannel owner = ChannelManager.byId(entry.channel);
                    text = owner == null ? null : owner.displayName;
                }
                if (text != null && !text.isEmpty()) {
                    graphics.drawString(mc.font, Component.literal(text),
                            pos[0] - mc.font.width(text) / 2,
                            pos[1] - radius - 11, config.archivedColor(), true);
                }
            }
        }
    }

    /**
     * 世界坐标 -> 屏幕坐标（水平方向），超出屏幕贴边。
     *
     * <p>这里刻意不用 {@code Camera#rotation()} 的四元数：原版在 1.21 改过这个
     * 四元数的基准约定（1.20.x 是 {@code forwards = rot * (0,0,1)}、{@code left = rot * (1,0,0)}，
     * 1.21+ 变成了 {@code rot * (0,0,-1)} / {@code rot * (1,0,0)}），同一份代码不可能两边都对。
     * 改用相机的 yaw/pitch 自己算基向量，语义跨版本一致。</p>
     */
    private static int[] project(GuiGraphics graphics, Minecraft mc,
                                 double targetX, double targetZ, int margin) {
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        float yaw = (float) Math.toRadians(mc.gameRenderer.getMainCamera().getYRot());
        float pitch = (float) Math.toRadians(mc.gameRenderer.getMainCamera().getXRot());

        float sinYaw = (float) Math.sin(yaw);
        float cosYaw = (float) Math.cos(yaw);
        float sinPitch = (float) Math.sin(pitch);
        float cosPitch = (float) Math.cos(pitch);

        // 与 Vec3.directionFromRotation 一致的相机朝向
        float fx = -sinYaw * cosPitch;
        float fz = cosYaw * cosPitch;
        // 水平右手方向（yaw=0 朝南 +Z 时指向西 -X）
        float rxAxis = -cosYaw;
        float rzAxis = -sinYaw;
        // 相机上方向 = 右手方向 × 前方向
        float ux = -sinYaw * sinPitch;
        float uz = cosYaw * sinPitch;

        // 目标高度锚定相机高度，垂直移动时路标不跳动
        float relX = (float) (targetX - camPos.x);
        float relZ = (float) (targetZ - camPos.z);

        float rx = relX * rxAxis + relZ * rzAxis;
        float ry = relX * ux + relZ * uz;
        float rz = relX * fx + relZ * fz;

        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();

        float tanY = (float) Math.tan(Math.toRadians((double) mc.options.fov().get() * 0.5));
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
        int cx = Math.round(Mth.clamp(sx, m, screenW - m));
        int cy = Math.round(Mth.clamp(sy, m, screenH - m));
        return new int[]{cx, cy};
    }

    // ---- 抗锯齿自绘图形 ----

    private static void drawMarker(GuiGraphics graphics, int cx, int cy, int r,
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
                        graphics.fill(px, py, px + 1, py + 1, (a << 24) | (cr << 16) | (cg << 8) | cb);
                    }
                } else {
                    double halo = clamp01(half + 2.0 + 0.5 - dist) * 0.32;
                    int a = (int) Math.round(halo * 70);
                    if (a > 0) {
                        graphics.fill(px, py, px + 1, py + 1, a << 24);
                    }
                }
            }
        }

        if (diamond || circle) {
            drawSmoothDot(graphics, cx, cy, 1.8, color);
        }
    }

    private static void drawSmoothDot(GuiGraphics graphics, int cx, int cy, double radius, int color) {
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
                        graphics.fill(px, py, px + 1, py + 1, (a << 24) | (cr << 16) | (cg << 8) | cb);
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

    private static void drawPanel(GuiGraphics graphics, Minecraft mc, String[] lines, int[] colors) {
        int x = 6;
        int y = 6;
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, mc.font.width(line));
        }
        graphics.fill(x - 3, y - 3, x + width + 5, y + 11 * lines.length + 1, PANEL_BG);
        for (int i = 0; i < lines.length; i++) {
            graphics.drawString(mc.font, Component.literal(lines[i]), x, y + i * 11, colors[i], true);
        }
    }
}