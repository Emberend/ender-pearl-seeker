package com.eyelocator.forge;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.eyelocator.core.ChannelManager;
import com.eyelocator.core.LocatorChannel;
import com.eyelocator.core.StrongholdLocator;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Forge 版管理界面。默认按 {@code I} 打开。
 *
 * <p>设置页只放最常用的几项，其余高级项仍然写 {@code config/eyelocator.json}
 * 或用 {@code /eyelocator} 命令修改。归档页支持命名、重命名、删除、去重、清空。</p>
 */
public final class ForgeEyeLocatorScreen extends Screen {

    private static final String[] STYLES = {"diamond", "circle", "cross"};
    private static final double[] SCALES = {0.6, 0.8, 1.0, 1.2, 1.5, 2.0};
    private static final double[] THICKNESSES = {1.5, 2.0, 2.5, 3.0, 4.0};

    private static final int ROW_HEIGHT = 18;
    private static final int BTN_H = 20;

    private static final int COLOR_TITLE = 0xFFFFFFFF;
    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_DIM = 0xFF8A8A8A;
    private static final int COLOR_WARN = 0xFFFFA0A0;
    private static final int PANEL_BG = 0xB0000000;

    private final Screen parent;

    /** 0 = 设置, 1 = 归档 */
    private int tab = 0;
    private int page = 0;
    private int rowsPerPage = 6;

    private final List<int[]> rowBoxes = new ArrayList<>();
    private final List<int[]> deleteBoxes = new ArrayList<>();

    private Button prevButton;
    private Button nextButton;
    private Button archiveCurrentButton;

    public ForgeEyeLocatorScreen(Screen parent) {
        super(Component.literal("末影寻珠 管理"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int tabsW = 100;
        int gap = 4;
        int tabsTotal = tabsW * 2 + gap;
        int tabsX = (this.width - tabsTotal) / 2;

        this.addRenderableWidget(Button.builder(Component.literal("设置"), b -> switchTab(0))
                .bounds(tabsX, 24, tabsW, BTN_H).build());
        this.addRenderableWidget(Button.builder(Component.literal("归档"), b -> switchTab(1))
                .bounds(tabsX + tabsW + gap, 24, tabsW, BTN_H).build());

        this.addRenderableWidget(Button.builder(Component.literal("完成"), b -> this.onClose())
                .bounds(this.width / 2 - 50, this.height - 27, 100, BTN_H).build());

        if (this.tab == 0) {
            initSettings();
        } else {
            initArchive();
        }
    }

    private void switchTab(int newTab) {
        if (this.tab == newTab) {
            return;
        }
        this.tab = newTab;
        this.page = 0;
        this.rebuildWidgets();
    }

    // ---- 设置页 ----

    private void initSettings() {
        ForgeConfig cfg = ForgeConfig.get();

        int colGap = 4;
        int colW = Math.min(170, (this.width - 24 - colGap) / 2);
        int totalW = colW * 2 + colGap;
        int left = (this.width - totalW) / 2;
        int right = left + colW + colGap;
        int top = 66;
        int row = 24;

        this.addRenderableWidget(cycle("路标样式", () -> styleName(cfg.markerStyle),
                () -> cfg.markerStyle = next(STYLES, cfg.markerStyle), left, top, colW,
                "标记形状：菱形 / 圆环 / 十字"));

        this.addRenderableWidget(cycle("路标大小", () -> String.format("%.1f", cfg.markerScale),
                () -> cfg.markerScale = next(SCALES, cfg.markerScale), left, top + row, colW,
                "标记显示大小，0.6 到 2.0"));

        this.addRenderableWidget(cycle("描边粗细", () -> String.format("%.1f", cfg.markerThickness),
                () -> cfg.markerThickness = next(THICKNESSES, cfg.markerThickness), left, top + row * 2, colW,
                "标记线条粗细，1.5 到 4.0"));

        this.addRenderableWidget(toggle("显示距离", () -> cfg.showDistance, v -> cfg.showDistance = v,
                left, top + row * 3, colW, "在标记下方显示到目标的距离"));

        this.addRenderableWidget(toggle("显示名称", () -> cfg.showLabels, v -> cfg.showLabels = v,
                right, top, colW, "在标记上方显示目标/投掷点的名称"));

        this.addRenderableWidget(toggle("显示信息面板", () -> cfg.showPanel, v -> cfg.showPanel = v,
                right, top + row, colW, "左上角的坐标、距离、置信度面板"));

        this.addRenderableWidget(toggle("显示旧目标标记", () -> cfg.showArchived, v -> cfg.showArchived = v,
                right, top + row * 2, colW, "把以前找到的其它目标显示为暗色小标记"));

        this.addRenderableWidget(toggle("找新目标时隐藏旧标记", () -> cfg.hideArchivedWhileSearching,
                v -> cfg.hideArchivedWhileSearching = v, right, top + row * 3, colW,
                "还没算出当前目标时，自动隐藏旧标记，避免干扰"));

        this.addRenderableWidget(toggle("自动识别新道具", () -> cfg.autoDetect, v -> cfg.autoDetect = v,
                left, top + row * 4, colW, "实体 id 里含 eye / seeker 等关键词时自动追踪"));

        this.addRenderableWidget(Button.builder(Component.literal("恢复默认设置"), b -> {
            cfg.markerStyle = "diamond";
            cfg.markerScale = 1.0;
            cfg.markerThickness = 3.0;
            cfg.showDistance = true;
            cfg.showLabels = true;
            cfg.showPanel = true;
            cfg.showArchived = true;
            cfg.hideArchivedWhileSearching = true;
            cfg.autoDetect = true;
            ForgeConfig.save();
            this.rebuildWidgets();
        }).bounds(right, top + row * 4, colW, BTN_H)
                .tooltip(Tooltip.create(Component.literal("只恢复上面这些开关，不动通道与归档")))
                .build());
    }

    // ---- 归档页 ----

    private void initArchive() {
        int gap = 4;
        int navW = 44;
        int addW = 96;
        int dedupeW = 54;
        int clearW = 64;
        int total = navW * 2 + addW + dedupeW + clearW + gap * 4;
        int x = Math.max(4, (this.width - total) / 2);
        int y = this.height - 46;

        this.prevButton = this.addRenderableWidget(Button.builder(Component.literal("上一页"), b -> {
            if (this.page > 0) {
                this.page--;
            }
        }).bounds(x, y, navW, BTN_H).build());
        x += navW + gap;

        this.nextButton = this.addRenderableWidget(Button.builder(Component.literal("下一页"), b -> this.page++)
                .bounds(x, y, navW, BTN_H).build());
        x += navW + gap;

        this.archiveCurrentButton = this.addRenderableWidget(
                Button.builder(Component.literal("归档当前坐标"), b -> archiveCurrent())
                        .bounds(x, y, addW, BTN_H)
                        .tooltip(Tooltip.create(Component.literal("把当前算出的坐标保存到归档，并可命名")))
                        .build());
        x += addW + gap;

        this.addRenderableWidget(Button.builder(Component.literal("去重"), b -> {
            int merged = ForgeRegistry.dedupe(ForgeConfig.get().dedupeDistance,
                    ClientSetup.activeChannelId());
            this.page = 0;
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(
                        Component.literal("已合并 " + merged + " 个相近目标"), true);
            }
        }).bounds(x, y, dedupeW, BTN_H).build());
        x += dedupeW + gap;

        this.addRenderableWidget(Button.builder(Component.literal("清空归档"), b -> {
            ForgeRegistry.clear();
            this.page = 0;
        }).bounds(x, y, clearW, BTN_H).build());
    }

    private void archiveCurrent() {
        StrongholdLocator locator = ClientSetup.locator();
        if (!locator.hasEstimate() || this.minecraft == null || this.minecraft.level == null) {
            return;
        }
        String dimension = this.minecraft.level.dimension().location().toString();
        String channelId = ClientSetup.activeChannelId();
        double x = locator.estX();
        double z = locator.estZ();
        this.minecraft.setScreen(new ForgeTextPromptScreen(this, "归档当前坐标", "",
                name -> ForgeRegistry.add(channelId, name, x, z, dimension)));
    }

    private void renameEntry(ForgeRegistry.Entry entry) {
        if (this.minecraft == null) {
            return;
        }
        String initial = entry.name == null ? "" : entry.name;
        this.minecraft.setScreen(new ForgeTextPromptScreen(this, "重命名目标", initial,
                name -> ForgeRegistry.rename(entry, name)));
    }

    // ---- 通用控件 ----

    private Button cycle(String name, Supplier<String> value, Runnable action, int x, int y, int w,
                         String tooltip) {
        return Button.builder(Component.literal(name + ": " + value.get()), b -> {
            action.run();
            ForgeConfig.save();
            b.setMessage(Component.literal(name + ": " + value.get()));
        }).bounds(x, y, w, BTN_H).tooltip(Tooltip.create(Component.literal(tooltip))).build();
    }

    private Button toggle(String name, BooleanSupplier getter, Consumer<Boolean> setter, int x, int y, int w,
                          String tooltip) {
        return Button.builder(stateText(name, getter.getAsBoolean()), b -> {
            setter.accept(!getter.getAsBoolean());
            ForgeConfig.save();
            b.setMessage(stateText(name, getter.getAsBoolean()));
        }).bounds(x, y, w, BTN_H).tooltip(Tooltip.create(Component.literal(tooltip))).build();
    }

    private static Component stateText(String name, boolean on) {
        return Component.literal(name + ": " + (on ? "开" : "关"));
    }

    // ---- 渲染 ----

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 自己画遮罩，不用原版的泥土背景：这是工具界面，深色底更好读
        graphics.fill(0, 0, this.width, this.height, PANEL_BG);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, COLOR_TITLE);

        if (this.tab == 0) {
            renderSettingsText(graphics);
        } else {
            renderArchive(graphics, mouseX, mouseY);
        }
    }

    private void renderSettingsText(GuiGraphics graphics) {
        LocatorChannel channel = ChannelManager.active();
        StrongholdLocator locator = channel.locator;
        String status = locator.hasEstimate()
                ? String.format("%s · X:%.0f Z:%.0f · 样本 %d · 置信 %d%%",
                        channel.displayName, locator.estX(), locator.estZ(),
                        locator.sampleCount(), Math.round(locator.confidence() * 100))
                : String.format("%s · 样本 %d · 尚未定位", channel.displayName, locator.sampleCount());
        graphics.drawCenteredString(this.font, Component.literal(status),
                this.width / 2, 50, COLOR_DIM);

        int bottom = 66 + 24 * 4 + BTN_H + 8;
        graphics.drawCenteredString(this.font,
                Component.literal("更多选项：编辑 config/eyelocator.json，或用 /eyelocator 命令"),
                this.width / 2, bottom, COLOR_DIM);
    }

    private void renderArchive(GuiGraphics graphics, int mouseX, int mouseY) {
        String dimension = this.minecraft == null || this.minecraft.level == null
                ? "" : this.minecraft.level.dimension().location().toString();

        int left = 14;
        int right = this.width - 14;
        int rowTop = 64;

        graphics.drawString(this.font,
                Component.literal("已归档目标（当前维度：" + dimName(dimension) + "）"),
                left, 48, COLOR_DIM);
        graphics.drawString(this.font, Component.literal("点击条目可重命名"), right - 90, 48, COLOR_DIM);

        this.rowsPerPage = Math.max(3, (this.height - 46 - 8 - rowTop) / ROW_HEIGHT);

        List<ForgeRegistry.Entry> all = ForgeRegistry.entries();
        this.rowBoxes.clear();
        this.deleteBoxes.clear();

        if (this.archiveCurrentButton != null) {
            this.archiveCurrentButton.active = ClientSetup.locator().hasEstimate();
        }

        if (all.isEmpty()) {
            graphics.drawString(this.font, Component.literal("暂无归档"), left, rowTop, COLOR_DIM);
            if (this.prevButton != null) {
                this.prevButton.active = false;
            }
            if (this.nextButton != null) {
                this.nextButton.active = false;
            }
            return;
        }

        int pages = Math.max(1, (all.size() + this.rowsPerPage - 1) / this.rowsPerPage);
        this.page = Math.max(0, Math.min(this.page, pages - 1));

        if (this.prevButton != null) {
            this.prevButton.active = this.page > 0;
        }
        if (this.nextButton != null) {
            this.nextButton.active = this.page < pages - 1;
        }

        int start = this.page * this.rowsPerPage;
        int end = Math.min(all.size(), start + this.rowsPerPage);
        for (int i = start; i < end; i++) {
            ForgeRegistry.Entry entry = all.get(i);
            int y = rowTop + (i - start) * ROW_HEIGHT;

            String name = entry.name == null || entry.name.isEmpty() ? "未命名" : entry.name;
            LocatorChannel owner = ChannelManager.byId(entry.channel);
            String channelName = owner == null ? "" : owner.displayName;
            String text = String.format("%s   X:%.0f Z:%.0f   %s   %s   置信%d%%",
                    name, entry.x, entry.z, dimName(entry.dimension), channelName,
                    Math.round(entry.confidence * 100));
            graphics.drawString(this.font, Component.literal(text), left, y + 4,
                    entry.dimension.equals(dimension) ? COLOR_TEXT : COLOR_DIM);
            this.rowBoxes.add(new int[]{left, y - 2, right - left - 22, 16, i});

            int boxX = right - 18;
            boolean hovered = mouseX >= boxX && mouseX <= boxX + 14 && mouseY >= y && mouseY <= y + 14;
            graphics.fill(boxX, y, boxX + 14, y + 14, hovered ? 0xFF883333 : 0xFF552222);
            graphics.drawCenteredString(this.font, Component.literal("×"), boxX + 7, y + 3, COLOR_TITLE);
            this.deleteBoxes.add(new int[]{boxX, y, 14, 14, i});
        }

        graphics.drawString(this.font,
                Component.literal("第 " + (this.page + 1) + " / " + pages + " 页 · 共 " + all.size() + " 个"),
                left, this.height - 62, COLOR_DIM);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.tab == 1 && button == 0) {
            for (int[] box : this.deleteBoxes) {
                if (inside(mouseX, mouseY, box)) {
                    List<ForgeRegistry.Entry> all = ForgeRegistry.entries();
                    int index = box[4];
                    if (index >= 0 && index < all.size()) {
                        ForgeRegistry.remove(all.get(index));
                    }
                    return true;
                }
            }
            for (int[] box : this.rowBoxes) {
                if (inside(mouseX, mouseY, box)) {
                    List<ForgeRegistry.Entry> all = ForgeRegistry.entries();
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
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private static String styleName(String style) {
        if ("circle".equalsIgnoreCase(style)) {
            return "圆环";
        }
        if ("cross".equalsIgnoreCase(style)) {
            return "十字";
        }
        return "菱形";
    }

    private static String next(String[] values, String current) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equalsIgnoreCase(current)) {
                return values[(i + 1) % values.length];
            }
        }
        return values[0];
    }

    private static double next(double[] values, double current) {
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
        switch (dimension) {
            case "minecraft:overworld":
                return "主世界";
            case "minecraft:the_nether":
                return "下界";
            case "minecraft:the_end":
                return "末地";
            default:
                return dimension;
        }
    }
}