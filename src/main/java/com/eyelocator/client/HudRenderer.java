package com.eyelocator.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * 2D HUD：
 * <ul>
 *   <li>左上角显示估计的要塞坐标、水平距离、样本数与置信度；</li>
 *   <li>屏幕上方画一个罗盘圆点，圆点相对屏幕中心的方向即为要塞相对视线的方向。</li>
 * </ul>
 */
public final class HudRenderer implements HudRenderCallback {

    private static final int PANEL_BG = 0x90101010;
    private static final int COLOR_ACCENT = 0xFF55FF55;
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

        if (!locator.hasEstimate()) {
            if (locator.sampleCount() > 0) {
                String hint = "末影之眼方位已记录 " + locator.sampleCount() + " 条，再扔一次即可三角定位";
                context.drawTextWithShadow(mc.textRenderer, Text.literal(hint), 6, 6, COLOR_DIM);
            }
            return;
        }

        double dx = locator.estX() - player.getX();
        double dz = locator.estZ() - player.getZ();
        double distance = Math.hypot(dx, dz);

        String coordLine = String.format("末地要塞 ≈  X:%.0f  Z:%.0f", locator.estX(), locator.estZ());
        String infoLine = String.format("距离 %d 格   样本 %d 条   置信度 %d%%",
                Math.round(distance), locator.sampleCount(), Math.round(locator.confidence() * 100));
        String residualLine = String.format("拟合残差 %.1f 格   [K] 清除记录", locator.residual());

        int x = 6;
        int y = 6;
        int width = Math.max(mc.textRenderer.getWidth(coordLine),
                Math.max(mc.textRenderer.getWidth(infoLine), mc.textRenderer.getWidth(residualLine))) + 8;
        context.fill(x - 3, y - 3, x + width, y + 32, PANEL_BG);
        context.drawTextWithShadow(mc.textRenderer, Text.literal(coordLine), x, y, COLOR_ACCENT);
        context.drawTextWithShadow(mc.textRenderer, Text.literal(infoLine), x, y + 11, COLOR_TEXT);
        context.drawTextWithShadow(mc.textRenderer, Text.literal(residualLine), x, y + 22, COLOR_DIM);

        drawCompass(context, mc, player, dx, dz);
    }

    private void drawCompass(DrawContext context, MinecraftClient mc, ClientPlayerEntity player,
                             double dx, double dz) {
        int screenW = context.getScaledWindowWidth();
        int cx = screenW / 2;
        int cy = 34;
        int radius = 20;

        // 视线方向向量（水平）：yaw=0 面向 +Z
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
        double tdx = dx / len;
        double tdz = dz / len;

        double forwardComp = tdx * forwardX + tdz * forwardZ;
        double rightComp = tdx * rightX + tdz * rightZ;
        // 相对屏幕角度：0=正前，正=右
        double angle = Math.atan2(rightComp, forwardComp);

        double sin = Math.sin(angle);
        double cos = Math.cos(angle);

        // 中心点
        context.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFFFFF);
        // 从中心到目标点的方向线
        for (int t = 1; t <= radius; t++) {
            int px = cx + (int) Math.round(sin * t);
            int py = cy - (int) Math.round(cos * t);
            context.fill(px, py, px + 1, py + 1, 0x80FFFFFF);
        }
        // 目标点
        int tipX = cx + (int) Math.round(sin * radius);
        int tipY = cy - (int) Math.round(cos * radius);
        context.fill(tipX - 2, tipY - 2, tipX + 2, tipY + 2, COLOR_ACCENT);
    }
}
