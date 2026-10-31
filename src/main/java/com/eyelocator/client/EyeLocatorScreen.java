package com.eyelocator.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Eye Locator 管理界面。设置页只保留少量直观选项；
 * 归档页支持：归档当前坐标并命名、点击条目重命名、删除、去重、清空。
 */
public final class EyeLocatorScreen extends Screen {

    private static final String[] STYLES = {"diamond", "circle", "cross"};
    private static final double[] SCALES = {0.6, 0.8, 1.0, 1.2, 1.5, 2.0};
    private static final int ROWS_PER_PAGE = 7;
    private static final int ROW_HEIGHT = 18;

    private final Screen parent;
    private int tab = 0; // 0=设置 1=归档
    private int page = 0;

    private final List<int[]> deleteBoxes = new ArrayList<>();
    private final List<int[]> rowBoxes = new ArrayList<>();

    public EyeLocatorScreen(Screen parent) {
        super(Text.literal("Eye Locator 管理"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("设置"), b -> switchTab(0))
                .dimensions(this.width / 2 - 105, 30, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("归档"), b -> switchTab(1))
                .dimensions(this.width / 2 + 5, 30, 100, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("完成"), b -> close())
                .dimensions(this.width / 2 - 50, this.height - 28, 100, 20).build());

        if (tab == 0) {
            initSettings();
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

    // ---- 设置页 ----

    private void initSettings() {
        EyeLocatorConfig cfg = EyeLocatorConfig.get();
        int w = 220;
        int x = (this.width - w) / 2;
        int top = 62;
        int h = 20;
        int gap = 24;

        ButtonWidget style = cycle("路标样式", () -> styleName(cfg.markerStyle),
                () -> cfg.markerStyle = nextStyle(cfg.markerStyle), x, top, w, h);
        style.setTooltip(Tooltip.of(Text.literal("路标的形状：菱形 / 圆环 / 十字")));
        addDrawableChild(style);

        ButtonWidget scale = cycle("路标大小", () -> String.format("%.1f", cfg.markerScale),
                () -> cfg.markerScale = nextDouble(SCALES, cfg.markerScale), x, top + gap, w, h);
        scale.setTooltip(Tooltip.of(Text.literal("路标显示大小，0.6 到 2.0")));
        addDrawableChild(scale);

        addDrawableChild(toggle("显示距离", () -> cfg.showDistance, v -> cfg.showDistance = v,
                x, top + gap * 2, w, h, "在路标下方显示到目标的距离（米）"));

        addDrawableChild(toggle("显示旧要塞标记", () -> cfg.showArchived, v -> cfg.showArchived = v,
                x, top + gap * 3, w, h, "把以前找到的其它要塞显示为暗色小标记"));

        addDrawableChild(toggle("找新要塞时隐藏旧标记", () -> cfg.hideArchivedWhileSearching,
                v -> cfg.hideArchivedWhileSearching = v, x, top + gap * 4, w, h,
                "还没算出当前要塞时，自动隐藏旧要塞标记，避免干扰"));

        ButtonWidget reset = ButtonWidget.builder(Text.literal("恢复默认设置"), b -> {
            cfg.markerStyle = "diamond";
            cfg.markerScale = 1.0;
            cfg.showDistance = true;
            cfg.showArchived = true;
            cfg.hideArchivedWhileSearching = true;
            EyeLocatorConfig.save();
            this.clearAndInit();
        }).dimensions(x, top + gap * 5, w, h).build();
        reset.setTooltip(Tooltip.of(Text.literal("只把上面这些选项恢复默认")));
        addDrawableChild(reset);
    }

    // ---- 归档页 ----

    private void initArchive() {
        int y = this.height - 52;
        int h = 20;
        int gap = 6;
        int navW = 55;
        int addW = 110;
        int dedupeW = 70;
        int clearW = 70;

        int total = navW * 2 + addW + dedupeW + clearW + gap * 4;
        int x = Math.max(8, (this.width - total) / 2);

        ButtonWidget prev = ButtonWidget.builder(Text.literal("上一页"), b -> {
            if (page > 0) {
                page--;
            }
        }).dimensions(x, y, navW, h).build();
        prev.active = page > 0;
        addDrawableChild(prev);
        x += navW + gap;

        addDrawableChild(ButtonWidget.builder(Text.literal("下一页"), b -> page++)
                .dimensions(x, y, navW, h).build());
        x += navW + gap;

        ButtonWidget addCurrent = ButtonWidget.builder(Text.literal("归档当前坐标"), b -> archiveCurrent())
                .dimensions(x, y, addW, h).build();
        addCurrent.active = EyeLocatorClient.LOCATOR.hasEstimate();
        addCurrent.setTooltip(Tooltip.of(Text.literal("把当前算出的要塞坐标保存到归档，并可命名")));
        addDrawableChild(addCurrent);
        x += addW + gap;

        addDrawableChild(ButtonWidget.builder(Text.literal("去重"), b -> {
            int merged = StrongholdRegistry.dedupe(EyeLocatorConfig.get().dedupeDistance);
            page = 0;
            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(Text.literal("已合并 " + merged + " 个相近要塞"), true);
            }
        }).dimensions(x, y, dedupeW, h).build());
        x += dedupeW + gap;

        addDrawableChild(ButtonWidget.builder(Text.literal("清空归档"), b -> {
            StrongholdRegistry.clear();
            page = 0;
        }).dimensions(x, y, clearW, h).build());
    }

    private void archiveCurrent() {
        StrongholdLocator locator = EyeLocatorClient.LOCATOR;
        if (!locator.hasEstimate() || this.client == null || this.client.world == null) {
            return;
        }
        String dimension = this.client.world.getRegistryKey().getValue().toString();
        double x = locator.estX();
        double z = locator.estZ();
        this.client.setScreen(new TextPromptScreen(this, "归档当前坐标", "",
                name -> StrongholdRegistry.add(name, x, z, dimension)));
    }

    private void renameEntry(StrongholdRegistry.Entry entry) {
        if (this.client == null) {
            return;
        }
        String initial = entry.name == null ? "" : entry.name;
        this.client.setScreen(new TextPromptScreen(this, "重命名要塞", initial,
                name -> StrongholdRegistry.rename(entry, name)));
    }

    // ---- 通用控件 ----

    private ButtonWidget cycle(String name, Supplier<String> value, Runnable action,
                               int x, int y, int w, int h) {
        return ButtonWidget.builder(Text.literal(name + ": " + value.get()), b -> {
            action.run();
            EyeLocatorConfig.save();
            b.setMessage(Text.literal(name + ": " + value.get()));
        }).dimensions(x, y, w, h).build();
    }

    private ButtonWidget toggle(String name, BooleanSupplier getter, Consumer<Boolean> setter,
                                int x, int y, int w, int h, String tooltip) {
        ButtonWidget button = ButtonWidget.builder(stateText(name, getter.getAsBoolean()), b -> {
            setter.accept(!getter.getAsBoolean());
            EyeLocatorConfig.save();
            b.setMessage(stateText(name, getter.getAsBoolean()));
        }).dimensions(x, y, w, h).build();
        button.setTooltip(Tooltip.of(Text.literal(tooltip)));
        return button;
    }

    private static Text stateText(String name, boolean on) {
        return Text.literal(name + ": " + (on ? "开" : "关"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFFFF);

        if (tab == 0) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("更多高级选项请编辑 config/eyelocator.json"),
                    this.width / 2, this.height - 44, 0xFF777777);
        } else {
            renderArchive(context, mouseX, mouseY);
        }
    }

    private void renderArchive(DrawContext context, int mouseX, int mouseY) {
        String dimension = this.client == null || this.client.world == null
                ? "" : this.client.world.getRegistryKey().getValue().toString();

        int left = 20;
        int right = this.width - 20;
        int rowY = 70;

        context.drawTextWithShadow(this.textRenderer,
                Text.literal("已归档要塞（当前维度： " + dimName(dimension) + "）"), left, 44, 0xFFAAAAAA);
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("点击条目可重命名"), right - 90, 44, 0xFF777777);

        List<StrongholdRegistry.Entry> all = StrongholdRegistry.entries();
        deleteBoxes.clear();
        rowBoxes.clear();
        if (all.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, Text.literal("暂无归档"), left, rowY, 0xFF888888);
            return;
        }

        int pages = Math.max(1, (all.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));

        int start = page * ROWS_PER_PAGE;
        int end = Math.min(all.size(), start + ROWS_PER_PAGE);
        for (int i = start; i < end; i++) {
            StrongholdRegistry.Entry entry = all.get(i);
            int y = rowY + (i - start) * ROW_HEIGHT;

            String name = entry.name == null || entry.name.isEmpty() ? "未命名" : entry.name;
            String text = String.format("%s   X:%.0f Z:%.0f   %s   置信%d%%",
                    name, entry.x, entry.z, dimName(entry.dimension),
                    Math.round(entry.confidence * 100));
            context.drawTextWithShadow(this.textRenderer, Text.literal(text), left, y + 4,
                    entry.dimension.equals(dimension) ? 0xFFE0E0E0 : 0xFF909090);
            rowBoxes.add(new int[]{left, y - 2, right - left - 22, 16, i});

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
                if (inside(mouseX, mouseY, box)) {
                    List<StrongholdRegistry.Entry> all = StrongholdRegistry.entries();
                    int index = box[4];
                    if (index >= 0 && index < all.size()) {
                        StrongholdRegistry.remove(all.get(index));
                    }
                    return true;
                }
            }
            for (int[] box : rowBoxes) {
                if (inside(mouseX, mouseY, box)) {
                    List<StrongholdRegistry.Entry> all = StrongholdRegistry.entries();
                    int index = box[4];
                    if (index >= 0 && index < all.size()) {
                        renameEntry(all.get(index));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static boolean inside(double mouseX, double mouseY, int[] box) {
        return mouseX >= box[0] && mouseX <= box[0] + box[2]
                && mouseY >= box[1] && mouseY <= box[1] + box[3];
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
