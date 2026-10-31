package com.eyelocator.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * 2D HUD。
 *
 * <ul>
 *   <li>已有交点：显示要塞坐标、距离、样本数、置信度，并用罗盘指向要塞。</li>
 *   <li>只有 1 条方位：显示"下一最佳投掷点"（垂直于首次方位错开），并用罗盘指向它。</li>
 * </ul>
 */
public final class HudRenderer implements HudRenderCallback {

    private static final int PANEL_BG = 0x90101010;
    private static final int COLOR_ACCENT = 0xFF55FF55;
    private static final int COLOR_ADVICE = 0xFF4FD8FF;
    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_DIM = 0xFF9A9A9A;

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
        String residualLine = String.format("拟合残差 %.1f 格   [K] 清除记录", locator.residual());

        drawPanel(context, mc, new String[]{coordLine, infoLine, residualLine},
                new int[]{COLOR_ACCENT, COLOR_TEXT, COLOR_DIM});

        drawCompass(context, mc, player, dx, dz, COLOR_ACCENT);
    }

    private void renderAdvice(DrawContext context, MinecraftClient mc, ClientPlayerEntity player,
                              NextThrowAdvisor.Advice advice) {
        if (advice.alreadyOptimal) {
            drawPanel(context, mc,
                    new String[]{"当前站位已与首次方位错开足够远", "直接原地再扔一次即可锁定要塞", "[K] 清除记录"},
                    new int[]{COLOR_ADVICE, COLOR_TEXT, COLOR_DIM});
            return;
        }

        double dx = advice.x - player.getX();
        double dz = advice.z - player.getZ();

        String coordLine = String.format("下一最佳投掷点 ≈  X:%.0f  Z:%.0f", advice.x, advice.z);
        String infoLine = String.format("距你 %d 格（垂直于首次方位错开，保证交点稳定）",
                Math.round(advice.travel));
        String hintLine = "走到该点再扔一次，即可锁定要塞   [K] 清除";

        drawPanel(context, mc, new String[]{coordLine, infoLine, hintLine},
                new int[]{COLOR_ADVICE, COLOR_TEXT, COLOR_DIM});

        drawCompass(context, mc, player, dx, dz, COLOR_ADVICE);
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

    private void drawCompass(DrawContext context, MinecraftClient mc, ClientPlayerEntity player,
                             double dx, double dz, int color) {
        int screenW = context.getScaledWindowWidth();
        int cx = screenW / 2;
        int cy = 34;
        int radius = 20;

        // 视线方向（水平）：yaw=0 面向 +Z
        double yawRad = Math.toRadians(player.getYaw());
        double forwardX = -MathHelper.sin((float) yawRad);
        double forwardZ = MathHelper.cos((float) yawRad);
        // 视线右方向（面朝北时右手指向东 +X）
        double rightX = -forwardZ;
        double rightZ = forwardX;

        double len = Math.hypot(dx, dz);
        if (len < 1e-4) {
            len = 1e-4;
        }
        double forwardComp = (dx / len) * forwardX + (dz / len) * forwardZ;
        double rightComp = (dx / len) * rightX + (dz / len) * rightZ;
        // 相对屏幕角度：0=正前，正=右
        double angle = Math.atan2(rightComp, forwardComp);

        double sin = Math.sin(angle);
        double cos = Math.cos(angle);

        context.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFFFFF);
        for (int t = 1; t <= radius; t++) {
            int px = cx + (int) Math.round(sin * t);
            int py = cy - (int) Math.round(cos * t);
            context.fill(px, py, px + 1, py + 1, 0x80FFFFFF);
        }
        int tipX = cx + (int) Math.round(sin * radius);
        int tipY = cy - (int) Math.round(cos * radius);
        context.fill(tipX - 2, tipY - 2, tipX + 2, tipY + 2, color);
    }
}
