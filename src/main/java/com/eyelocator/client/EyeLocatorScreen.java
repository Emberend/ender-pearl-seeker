package com.eyelocator.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Eye Locator 管理界面：设置 + 已归档要塞管理。 */
public final class EyeLocatorScreen extends Screen {

    private static final String[] STYLES = {"diamond", "circle", "cross"};
    private static final double[] SCALES = {0.6, 0.8, 1.0, 1.2, 1.5, 2.0};
    private static final double[] THICKNESS = {1.5, 2.0, 2.5, 3.0, 4.0, 5.0};
    private static final double[] BASELINES = {150, 250, 400, 600, 800, 1000};
    private static final double[] OUTLIERS = {40, 60, 80, 120, 200};
    private static final double[] CHANGES = {256, 384, 512, 768, 1024};
    private static final int ROWS_PER_PAGE = 7;
    private static final int ROW_HEIGHT = 18;

    private final Screen parent;
    private int tab = 0; // 0=设置 1=归档
    private int page = 0;

    private final List<StrongholdRegistry.Entry> displayed = new ArrayList<>();
    private final List<int[]> deleteBoxes = new ArrayList<>();

    public EyeLocatorScreen(Screen parent) {
        super(Text.literal("Eye Locator 管理"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int top = 56;
        int h = 20;
        int gap = 24;

        addDrawableChild(ButtonWidget.builder(Text.literal("设置"), b -> switchTab(0))
                .dimensions(this.width / 2 - 105, 30, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("归档"), b -> switchTab(1))
                .dimensions(this.width / 2 + 5, 30, 100, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("完成"), b -> close())
                .dimensions(this.width / 2 - 50, this.height - 28, 100, 20).build());

        if (tab == 0) {
            initSettings(top, h, gap);
        } else {
            initArchive();
        }
    }

    private void switchTab(int newTab) {
        if (tab == newTab) {
            return;
        }
        tab = newTab;
        page = 0;
        this.clearAndInit();
    }

    private void initSettings(int top, int h, int gap) {
        EyeLocatorConfig cfg = EyeLocatorConfig.get();
        int col1 = 20;
        int col2 = 20 + 170;
        int w = 160;

        addDrawableChild(cycle("样式", () -> styleName(cfg.markerStyle), () -> cfg.markerStyle = nextStyle(cfg.markerStyle), col1, top, w, h));
        addDrawableChild(cycle("缩放", () -> String.format("%.1f", cfg.markerScale), () -> cfg.markerScale = nextDouble(SCALES, cfg.markerScale), col1, top + gap, w, h));
        addDrawableChild(cycle("粗细", () -> String.format("%.1f", cfg.markerThickness), () -> cfg.markerThickness = nextDouble(THICKNESS, cfg.markerThickness), col1, top + gap * 2, w, h));
        addDrawableChild(cycle("投掷基线", () -> String.format("%.0f 格", cfg.baseline), () -> cfg.baseline = nextDouble(BASELINES, cfg.baseline), col1, top + gap * 3, w, h));
        addDrawableChild(cycle("异常阈值", () -> String.format("%.0f 格", cfg.outlierResidual), () -> cfg.outlierResidual = nextDouble(OUTLIERS, cfg.outlierResidual), col1, top + gap * 4, w, h));
        addDrawableChild(cycle("换要塞判定", () -> String.format("%.0f 格", cfg.changeDistance), () -> cfg.changeDistance = nextDouble(CHANGES, cfg.changeDistance), col1, top + gap * 5, w, h));

        addDrawableChild(toggle("显示距离", () -> cfg.showDistance, v -> cfg.showDistance = v, col2, top, w, h));
        addDrawableChild(toggle("显示标签", () -> cfg.showLabels, v -> cfg.showLabels = v, col2, top + gap, w, h));
        addDrawableChild(toggle("显示信息面板", () -> cfg.showPanel, v -> cfg.showPanel = v, col2, top + gap * 2, w, h));
        addDrawableChild(toggle("显示已归档标记", () -> cfg.showArchived, v -> cfg.showArchived = v, col2, top + gap * 3, w, h));
        addDrawableChild(toggle("找新要塞时隐藏旧标记", () -> cfg.hideArchivedWhileSearching,
                v -> cfg.hideArchivedWhileSearching = v, col2, top + gap * 4, w, h));
        addDrawableChild(toggle("自动检测要塞变更", () -> cfg.detectStrongholdChange,
                v -> cfg.detectStrongholdChange = v, col2, top + gap * 5, w, h));

        addDrawableChild(ButtonWidget.builder(Text.literal("清除当前定位"), b -> {
            EyeLocatorClient.LOCATOR.reset();
            EyeTracker.clear();
        }).dimensions(col1, top + gap * 6, w, h).build());
    }

    private void initArchive() {
        addDrawableChild(ButtonWidget.builder(Text.literal("上一页"), b -> {
            if (page > 0) {
                page--;
            }
        }).dimensions(this.width / 2 - 155, this.height - 52, 70, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("下一页"), b -> {
            page++;
        }).dimensions(this.width / 2 - 80, this.height - 52, 70, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("清空归档"), b -> {
            StrongholdRegistry.clear();
            page = 0;
        }).dimensions(this.width / 2 + 5, this.height - 52, 150, 20).build());
    }

    private ButtonWidget cycle(String name, Supplier<String> value, Runnable action, int x, int y, int w, int h) {
        ButtonWidget[] ref = new ButtonWidget[1];
        ref[0] = ButtonWidget.builder(Text.literal(name + ": " + value.get()), b -> {
            action.run();
            EyeLocatorConfig.save();
            b.setMessage(Text.literal(name + ": " + value.get()));
        }).dimensions(x, y, w, h).build();
        return ref[0];
    }

    private ButtonWidget toggle(String name, BooleanSupplier getter, Consumer<Boolean> setter,
                                int x, int y, int w, int h) {
        return ButtonWidget.builder(stateText(name, getter.getAsBoolean()), b -> {
            setter.accept(!getter.getAsBoolean());
            EyeLocatorConfig.save();
            b.setMessage(stateText(name, getter.getAsBoolean()));
        }).dimensions(x, y, w, h).build();
    }

    private static Text stateText(String name, boolean on) {
        return Text.literal(name + ": " + (on ? "开" : "关"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFFFF);

        if (tab == 0) {
            context.drawTextWithShadow(this.textRenderer, Text.literal("设置"), 20, 42, 0xFFAAAAAA);
        } else {
            renderArchive(context, mouseX, mouseY);
        }
    }

    private void renderArchive(DrawContext context, int mouseX, int mouseY) {
        String dimension = this.client == null || this.client.world == null
                ? "" : this.client.world.getRegistryKey().getValue().toString();

        displayed.clear();
        int left = 20;
        int right = this.width - 20;
        int rowY = 72;

        context.drawTextWithShadow(this.textRenderer,
                Text.literal("已归档要塞（当前维度： " + dimName(dimension) + "）"), left, 44, 0xFFAAAAAA);

        List<StrongholdRegistry.Entry> all = StrongholdRegistry.entries();
        if (all.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, Text.literal("暂无归档"), left, rowY, 0xFF888888);
            deleteBoxes.clear();
            return;
        }

        int pages = Math.max(1, (all.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        if (page >= pages) {
            page = pages - 1;
        }
        if (page < 0) {
            page = 0;
        }

        deleteBoxes.clear();
        int start = page * ROWS_PER_PAGE;
        int end = Math.min(all.size(), start + ROWS_PER_PAGE);
        for (int i = start; i < end; i++) {
            StrongholdRegistry.Entry entry = all.get(i);
            displayed.add(entry);
            int y = rowY + (i - start) * ROW_HEIGHT;

            String text = String.format("X: %.0f   Z: %.0f   %s   置信 %d%%",
                    entry.x, entry.z, dimName(entry.dimension), Math.round(entry.confidence * 100));
            context.drawTextWithShadow(this.textRenderer, Text.literal(text), left, y + 4,
                    entry.dimension.equals(dimension) ? 0xFFE0E0E0 : 0xFF909090);

            int boxX = right - 18;
            boolean hovered = mouseX >= boxX && mouseX <= boxX + 14 && mouseY >= y && mouseY <= y + 14;
            context.fill(boxX, y, boxX + 14, y + 14, hovered ? 0xFF883333 : 0xFF552222);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("×"),
                    boxX + 7, y + 3, 0xFFFFFFFF);
            deleteBoxes.add(new int[]{boxX, y, 14, 14, i});
        }

        context.drawTextWithShadow(this.textRenderer,
                Text.literal("第 " + (page + 1) + " / " + pages + " 页 · 共 " + all.size() + " 个"),
                left, this.height - 68, 0xFFAAAAAA);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tab == 1 && button == 0) {
            for (int[] box : deleteBoxes) {
                if (mouseX >= box[0] && mouseX <= box[0] + box[2]
                        && mouseY >= box[1] && mouseY <= box[1] + box[3]) {
                    int index = box[4];
                    List<StrongholdRegistry.Entry> all = StrongholdRegistry.entries();
                    if (index >= 0 && index < all.size()) {
                        StrongholdRegistry.remove(all.get(index));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    private static String styleName(String style) {
        return switch (style) {
            case "circle" -> "圆环";
            case "cross" -> "十字";
            default -> "菱形";
        };
    }

    private static String nextStyle(String current) {
        for (int i = 0; i < STYLES.length; i++) {
            if (STYLES[i].equals(current)) {
                return STYLES[(i + 1) % STYLES.length];
            }
        }
        return STYLES[0];
    }

    private static double nextDouble(double[] values, double current) {
        for (int i = 0; i < values.length; i++) {
            if (Math.abs(values[i] - current) < 1e-6) {
                return values[(i + 1) % values.length];
            }
        }
        return values[0];
    }

    private static String dimName(String dimension) {
        if (dimension == null) {
            return "?";
        }
        return switch (dimension) {
            case "minecraft:overworld" -> "主世界";
            case "minecraft:the_nether" -> "下界";
            case "minecraft:the_end" -> "末地";
            default -> dimension;
        };
    }
}
