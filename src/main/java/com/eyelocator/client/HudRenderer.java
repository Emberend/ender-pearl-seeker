package com.eyelocator.client;

import com.eyelocator.core.ChannelManager;
import com.eyelocator.core.Fmt;
import com.eyelocator.core.LocatorChannel;
import com.eyelocator.core.NextThrowAdvisor;
import com.eyelocator.core.StrongholdLocator;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;


/** 2D HUD：投影路标（支持样式/颜色/大小配置）+ 信息面板 + 已归档要塞标记。 */
public final class HudRenderer implements HudRenderCallback {

    private static final int PANEL_BG = 0x90101010;
    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_DIM = 0xFF9A9A9A;
    private static final int COLOR_WARN = 0xFFFFA0A0;
    private static final int BASE_RADIUS = 11;
    private static final int MARGIN = 22;

    // 翻译文本是惰性的（渲染时才解析），可以安全地在类加载时创建一次
    private static final Text TEXT_RECORDED = Text.translatable("eyelocator.hud.recorded");
    private static final Text TEXT_FOOTER_SIMPLE = Text.translatable("eyelocator.hud.footer_simple");
    private static final Text TEXT_OPTIMAL1 = Text.translatable("eyelocator.hud.advice.optimal1");
    private static final Text TEXT_OPTIMAL2 = Text.translatable("eyelocator.hud.advice.optimal2");
    private static final Text TEXT_CONFLICT = Text.translatable("eyelocator.hud.advice.conflict");
    private static final Text TEXT_CONFLICT_HERE = Text.translatable("eyelocator.hud.advice.conflict_here");
    private static final Text TEXT_PARALLEL = Text.translatable("eyelocator.hud.advice.parallel");
    private static final Text TEXT_LABEL_THROW = Text.translatable("eyelocator.hud.label.throw");
    private static final Text TEXT_LABEL_RETHROW = Text.translatable("eyelocator.hud.label.rethrow");

    /** 投影结果复用缓冲：HUD 在渲染线程上，单线程调用，不必每帧新建数组。 */
    private final int[] projBuf = new int[2];

    /** 面板文本缓存：数据没变就不重新拼接字符串。 */
    private long panelKey = Long.MIN_VALUE;
    private Text[] panelLines;
    private int[] panelColors;

    /** 建议态面板缓存（随推荐点/距离变化才重建）。 */
    private long adviceKey = Long.MIN_VALUE;
    private Text[] adviceLines;
    private int[] adviceColors;

    /** 距离文字缓存：米数不变就复用同一个 Text。 */
    private int distMeters = Integer.MIN_VALUE;
    private Text distText;

    /** 归档名称 -> 文本，避免每帧为同一个名字新建 Text。 */
    private final java.util.Map<String, Text> nameCache = new java.util.HashMap<>();

    /** 当前世界与维度 id 的缓存，避免每帧拼接字符串。 */
    private net.minecraft.client.world.ClientWorld cachedWorld;
    private String cachedDimension;

    // 1.20.1 的接口签名是 (DrawContext, float)
    public void onHudRender(DrawContext context, float tickDelta) {
        renderHud(context);
    }

    // 1.21.x 的接口签名是 (DrawContext, RenderTickCounter)
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        renderHud(context);
    }

    private void renderHud(DrawContext context) {
        EyeLocatorConfig config = EyeLocatorConfig.get();
        if (!config.enabled) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null || mc.options.hudHidden) {
            if (mc.world == null) {
                // 退出世界时释放旧世界的强引用
                cachedWorld = null;
                cachedDimension = null;
            }
            return;
        }

        // 使用实体当前位置；路标投影用相机插值，移动时依然平滑
        Vec3d playerPos = player.getPos();
        LocatorChannel channel = ChannelManager.active();
        StrongholdLocator locator = channel.locator;

        if (locator.hasEstimate()) {
            renderEstimate(context, mc, playerPos, channel, config);
        } else {
            NextThrowAdvisor.Advice advice = NextThrowAdvisor.advise(playerPos.x, playerPos.z, locator, config.baseline);
            if (advice != null) {
                renderAdvice(context, mc, advice, config);
            } else if (locator.sampleCount() > 0) {
                context.drawTextWithShadow(mc.textRenderer, TEXT_RECORDED, 6, 6, COLOR_DIM);
            }
        }

        boolean searchingNew = config.hideArchivedWhileSearching && !locator.hasEstimate();
        if (config.showArchived && !searchingNew) {
            drawArchivedMarkers(context, mc, config);
        }
    }

    private void renderEstimate(DrawContext context, MinecraftClient mc, Vec3d playerPos,
                                LocatorChannel channel, EyeLocatorConfig config) {
        StrongholdLocator locator = channel.locator;
        double distance = Math.hypot(locator.estX() - playerPos.x, locator.estZ() - playerPos.z);
        int color = config.strongholdColor();

        if (config.showPanel) {
            int confidence = (int) Math.round(locator.confidence() * 100);
            int archived = StrongholdRegistry.size();
            int meters = (int) Math.round(distance);
            long key = mix(0x51L, Math.round(locator.estX() * 10));
            key = mix(key, Math.round(locator.estZ() * 10));
            key = mix(key, locator.sampleCount());
            key = mix(key, locator.excluded());
            key = mix(key, confidence);
            key = mix(key, archived);
            key = mix(key, meters);
            // 用通道 id 做键：翻译只在重建面板时解析，避免每帧分配字符串
            key = mix(key, channel.id.hashCode());
            key = mix(key, color);

            if (key != panelKey) {
                panelKey = key;
                Text samples = locator.excluded() > 0
                        ? Text.translatable("eyelocator.hud.samples_excluded",
                                locator.sampleCount(), locator.excluded())
                        : Text.translatable("eyelocator.hud.samples", locator.sampleCount());
                panelLines = new Text[]{
                        Text.translatable("eyelocator.hud.estimate", EyeLocatorClient.channelName(channel.id),
                                Fmt.number(locator.estX()), Fmt.number(locator.estZ())),
                        Text.translatable("eyelocator.hud.distance", meters, samples, confidence),
                        Text.translatable("eyelocator.hud.footer", archived)
                };
                panelColors = new int[]{color, COLOR_TEXT, COLOR_DIM};
            }
            drawPanel(context, mc, panelLines, panelColors);
        }

        drawWaypoint(context, mc, locator.estX(), locator.estZ(), distance, color, null, config);
    }

    private void renderAdvice(DrawContext context, MinecraftClient mc,
                              NextThrowAdvisor.Advice advice, EyeLocatorConfig config) {
        if (advice.kind == NextThrowAdvisor.Kind.THROW_HERE) {
            if (config.showPanel) {
                drawPanel(context, mc, new Text[]{TEXT_OPTIMAL1, TEXT_OPTIMAL2, TEXT_FOOTER_SIMPLE},
                        new int[]{config.adviceColor(), COLOR_TEXT, COLOR_DIM});
            }
            return;
        }

        boolean warning = advice.kind != NextThrowAdvisor.Kind.MOVE;
        int color = warning ? COLOR_WARN : config.adviceColor();
        if (config.showPanel) {
            long key = mix(0xA1L, advice.kind.ordinal());
            key = mix(key, Math.round(advice.x * 10));
            key = mix(key, Math.round(advice.z * 10));
            key = mix(key, Math.round(advice.travel));
            key = mix(key, color);

            if (key != adviceKey || adviceLines == null) {
                adviceKey = key;
                Text title;
                Text detail;
                if (advice.kind == NextThrowAdvisor.Kind.RETHROW) {
                    title = TEXT_CONFLICT;
                    detail = advice.travel < 1.0
                            ? TEXT_CONFLICT_HERE
                            : Text.translatable("eyelocator.hud.advice.conflict_move", Math.round(advice.travel));
                } else if (advice.kind == NextThrowAdvisor.Kind.MOVE_WIDER) {
                    title = TEXT_PARALLEL;
                    detail = Text.translatable("eyelocator.hud.advice.parallel_detail", Math.round(advice.travel));
                } else {
                    title = Text.translatable("eyelocator.hud.advice.next",
                            Fmt.number(advice.x), Fmt.number(advice.z));
                    detail = Text.translatable("eyelocator.hud.advice.next_detail", Math.round(advice.travel));
                }
                adviceLines = new Text[]{title, detail, TEXT_FOOTER_SIMPLE};
                adviceColors = new int[]{color, COLOR_TEXT, COLOR_DIM};
            }
            drawPanel(context, mc, adviceLines, adviceColors);
        }

        Text label = advice.kind == NextThrowAdvisor.Kind.RETHROW
                ? TEXT_LABEL_RETHROW
                : TEXT_LABEL_THROW;
        drawWaypoint(context, mc, advice.x, advice.z, advice.travel, color, label, config);
    }

    private void drawWaypoint(DrawContext context, MinecraftClient mc,
                              double targetX, double targetZ, double distance,
                              int color, Text label, EyeLocatorConfig config) {
        int radius = Math.max(4, (int) Math.round(BASE_RADIUS * clamp(config.markerScale, 0.4, 3.0)));
        project(mc, context, targetX, targetZ, radius + 6);
        int cx = projBuf[0];
        int cy = projBuf[1];

        drawMarker(context, cx, cy, radius, (float) config.scaledThickness(), color, config.markerStyle);

        if (config.showDistance) {
            int meters = (int) Math.round(distance);
            if (meters != distMeters || distText == null) {
                distMeters = meters;
                distText = Text.translatable("eyelocator.hud.distance_m", meters);
            }
            context.drawTextWithShadow(mc.textRenderer, distText,
                    cx - mc.textRenderer.getWidth(distText) / 2, cy + radius + 5, 0xFFFFFFFF);
        }
        if (config.showLabels && label != null) {
            context.drawTextWithShadow(mc.textRenderer, label,
                    cx - mc.textRenderer.getWidth(label) / 2, cy - radius - 13, color);
        }
    }

    /** 把已归档的其它要塞画成较小、较暗的路标。 */
    private void drawArchivedMarkers(DrawContext context, MinecraftClient mc, EyeLocatorConfig config) {
        if (mc.world != cachedWorld) {
            cachedWorld = mc.world;
            cachedDimension = mc.world.getRegistryKey().getValue().toString();
        }
        String dimension = cachedDimension;
        StrongholdLocator locator = ChannelManager.active().locator;

        int radius = Math.max(3, (int) Math.round(BASE_RADIUS * 0.6 * clamp(config.markerScale, 0.4, 3.0)));
        float thickness = Math.max(1.0f, (float) (config.scaledThickness() * 0.7));
        int color = config.archivedColor();

        for (StrongholdRegistry.Entry entry : StrongholdRegistry.entries()) {
            if (!entry.dimension.equals(dimension)) {
                continue;
            }
            if (locator.hasEstimate()
                    && Math.hypot(entry.x - locator.estX(), entry.z - locator.estZ()) < 128) {
                continue; // 与当前定位重叠
            }
            project(mc, context, entry.x, entry.z, radius + 6);
            drawMarker(context, projBuf[0], projBuf[1], radius, thickness, color, config.markerStyle);

            if (config.showLabels && entry.name != null && !entry.name.isEmpty()) {
                Text name = nameCache.get(entry.name);
                if (name == null) {
                    name = Text.literal(entry.name);
                    nameCache.put(entry.name, name);
                }
                context.drawTextWithShadow(mc.textRenderer, name,
                        projBuf[0] - mc.textRenderer.getWidth(name) / 2,
                        projBuf[1] - radius - 11, color);
            }
        }
    }

    /**
     * 世界坐标 -> 屏幕坐标（水平方向），超出屏幕贴边。结果写入 {@link #projBuf}。
     *
     * <p>这里刻意不用 {@code Camera#getRotation()} 的四元数：原版在 1.21 改过这个
     * 四元数的基准约定（1.20.x 是 {@code forwards = rot * (0,0,1)}、{@code left = rot * (1,0,0)}，
     * 1.21+ 变成了 {@code rot * (0,0,-1)} / {@code rot * (1,0,0)}），同一份代码不可能两边都对。
     * 改用相机的 yaw/pitch 自己算基向量，语义跨版本一致。</p>
     */
    private void project(MinecraftClient mc, DrawContext context,
                         double targetX, double targetZ, int margin) {
        Vec3d camPos = mc.gameRenderer.getCamera().getPos();
        float yaw = (float) Math.toRadians(mc.gameRenderer.getCamera().getYaw());
        float pitch = (float) Math.toRadians(mc.gameRenderer.getCamera().getPitch());

        float sinYaw = (float) Math.sin(yaw);
        float cosYaw = (float) Math.cos(yaw);
        float sinPitch = (float) Math.sin(pitch);
        float cosPitch = (float) Math.cos(pitch);

        // 与 Vec3d.fromPolar / Entity#getRotationVector 一致的相机朝向
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
        projBuf[0] = Math.round(MathHelper.clamp(sx, m, screenW - m));
        projBuf[1] = Math.round(MathHelper.clamp(sy, m, screenH - m));
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

    /** 简单的数值混合，用作"面板内容是否变化"的指纹。 */
    private static long mix(long hash, long value) {
        long h = hash ^ (value + 0x9E3779B97F4A7C15L + (hash << 6) + (hash >>> 2));
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return h;
    }

    private void drawPanel(DrawContext context, MinecraftClient mc, Text[] lines, int[] colors) {
        int x = 6;
        int y = 6;
        int width = 0;
        for (Text line : lines) {
            width = Math.max(width, mc.textRenderer.getWidth(line));
        }
        context.fill(x - 3, y - 3, x + width + 5, y + 11 * lines.length + 1, PANEL_BG);
        for (int i = 0; i < lines.length; i++) {
            context.drawTextWithShadow(mc.textRenderer, lines[i], x, y + i * 11, colors[i]);
        }
    }
}