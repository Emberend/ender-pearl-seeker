package com.eyelocator.forge;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.eyelocator.core.ChannelManager;
import com.eyelocator.core.Fmt;
import com.eyelocator.core.LocatorChannel;
import com.eyelocator.core.StrongholdLocator;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

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
    /** 归档页那一排窄按钮：比常规按钮矮一点，好在 320×240 下留出行距。 */
    private static final int ARCHIVE_BTN_H = 18;
    /** 小于这个逻辑宽度（320×240 缩放时的常见值）就省略较长的文字。 */
    private static final int NARROW_WIDTH = 420;

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
        super(Component.translatable("eyelocator.screen.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int tabsW = 100;
        int gap = 4;
        int tabsTotal = tabsW * 2 + gap;
        int tabsX = (this.width - tabsTotal) / 2;

        this.addRenderableWidget(Button.builder(Component.translatable("eyelocator.screen.tab.settings"),
                        b -> switchTab(0))
                .bounds(tabsX, 24, tabsW, BTN_H).build());
        this.addRenderableWidget(Button.builder(Component.translatable("eyelocator.screen.tab.archive"),
                        b -> switchTab(1))
                .bounds(tabsX + tabsW + gap, 24, tabsW, BTN_H).build());

        this.addRenderableWidget(Button.builder(Component.translatable("eyelocator.screen.done"),
                        b -> this.onClose())
                .bounds(this.width / 2 - 50, this.height - 24, 100, BTN_H).build());

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
        int top = 60;
        int row = 21;

        this.addRenderableWidget(cycle("eyelocator.screen.style", () -> styleName(cfg.markerStyle),
                () -> cfg.markerStyle = next(STYLES, cfg.markerStyle), left, top, colW,
                "eyelocator.screen.style.tooltip"));

        this.addRenderableWidget(cycle("eyelocator.screen.scale", () -> Component.literal(String.format("%.1f", cfg.markerScale)),
                () -> cfg.markerScale = next(SCALES, cfg.markerScale), left, top + row, colW,
                "eyelocator.screen.scale.tooltip"));

        this.addRenderableWidget(cycle("eyelocator.screen.thickness", () -> Component.literal(String.format("%.1f", cfg.markerThickness)),
                () -> cfg.markerThickness = next(THICKNESSES, cfg.markerThickness), left, top + row * 2, colW,
                "eyelocator.screen.thickness.tooltip"));

        this.addRenderableWidget(toggle("eyelocator.screen.show_distance", () -> cfg.showDistance,
                v -> cfg.showDistance = v, left, top + row * 3, colW,
                "eyelocator.screen.show_distance.tooltip"));

        this.addRenderableWidget(toggle("eyelocator.screen.show_labels", () -> cfg.showLabels,
                v -> cfg.showLabels = v, left, top + row * 4, colW,
                "eyelocator.screen.show_labels.tooltip"));

        this.addRenderableWidget(toggle("eyelocator.screen.auto_detect", () -> cfg.autoDetect,
                v -> cfg.autoDetect = v, left, top + row * 5, colW,
                "eyelocator.screen.auto_detect.tooltip"));

        this.addRenderableWidget(toggle("eyelocator.screen.show_panel", () -> cfg.showPanel,
                v -> cfg.showPanel = v, right, top, colW,
                "eyelocator.screen.show_panel.tooltip"));

        this.addRenderableWidget(toggle("eyelocator.screen.show_archived", () -> cfg.showArchived,
                v -> cfg.showArchived = v, right, top + row, colW,
                "eyelocator.screen.show_archived.tooltip"));

        this.addRenderableWidget(toggle("eyelocator.screen.hide_archived", () -> cfg.hideArchivedWhileSearching,
                v -> cfg.hideArchivedWhileSearching = v, right, top + row * 2, colW,
                "eyelocator.screen.hide_archived.tooltip"));

        this.addRenderableWidget(toggle("eyelocator.screen.notify_chat", () -> cfg.notifyChat,
                v -> cfg.notifyChat = v, right, top + row * 3, colW,
                "eyelocator.screen.notify_chat.tooltip"));

        this.addRenderableWidget(Button.builder(Component.translatable("eyelocator.screen.reset"), b -> {
            cfg.markerStyle = "diamond";
            cfg.markerScale = 1.0;
            cfg.markerThickness = 3.0;
            cfg.showDistance = true;
            cfg.showLabels = true;
            cfg.showPanel = true;
            cfg.showArchived = true;
            cfg.hideArchivedWhileSearching = true;
            cfg.notifyChat = true;
            cfg.autoDetect = true;
            ForgeConfig.save();
            this.rebuildWidgets();
        }).bounds(right, top + row * 4, colW, BTN_H)
                .tooltip(Tooltip.create(Component.translatable("eyelocator.screen.reset.tooltip")))
                .build());
    }

    // ---- 归档页 ----

    private void initArchive() {
        int gap = 4;
        int navW = 42;
        int addW = 92;
        int dedupeW = 50;
        int clearW = 60;
        // 42 + 42 + 92 + 50 + 60 + 4*4 = 302，320 宽的界面上也能居中放下
        int total = navW * 2 + addW + dedupeW + clearW + gap * 4;
        int x = Math.max(4, (this.width - total) / 2);
        int y = this.height - 46;

        this.prevButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("eyelocator.screen.archive.prev"), b -> {
                            if (this.page > 0) {
                                this.page--;
                            }
                        }).bounds(x, y, navW, ARCHIVE_BTN_H).build());
        x += navW + gap;

        this.nextButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("eyelocator.screen.archive.next"), b -> this.page++)
                .bounds(x, y, navW, ARCHIVE_BTN_H).build());
        x += navW + gap;

        this.archiveCurrentButton = this.addRenderableWidget(
                Button.builder(Component.translatable("eyelocator.screen.archive.add"), b -> archiveCurrent())
                        .bounds(x, y, addW, ARCHIVE_BTN_H)
                        .tooltip(Tooltip.create(Component.translatable("eyelocator.screen.archive.add.tooltip")))
                        .build());
        x += addW + gap;

        this.addRenderableWidget(Button.builder(Component.translatable("eyelocator.screen.archive.dedupe"), b -> {
            int merged = ForgeRegistry.dedupe(ForgeConfig.get().dedupeDistance);
            this.page = 0;
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(
                        Component.translatable("eyelocator.chat.deduped", merged), true);
            }
        }).bounds(x, y, dedupeW, ARCHIVE_BTN_H)
                .tooltip(Tooltip.create(Component.translatable("eyelocator.screen.archive.dedupe.tooltip")))
                .build());
        x += dedupeW + gap;

        this.addRenderableWidget(Button.builder(Component.translatable("eyelocator.screen.archive.clear"), b -> {
            ForgeRegistry.clear();
            this.page = 0;
        }).bounds(x, y, clearW, ARCHIVE_BTN_H)
                .tooltip(Tooltip.create(Component.translatable("eyelocator.screen.archive.clear.tooltip")))
                .build());
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
        this.minecraft.setScreen(new ForgeTextPromptScreen(this,
                Component.translatable("eyelocator.prompt.archive_title"), "",
                name -> ForgeRegistry.add(channelId, name, x, z, dimension)));
    }

    private void renameEntry(ForgeRegistry.Entry entry) {
        if (this.minecraft == null) {
            return;
        }
        String initial = entry.name == null ? "" : entry.name;
        this.minecraft.setScreen(new ForgeTextPromptScreen(this,
                Component.translatable("eyelocator.prompt.rename_title"), initial,
                name -> ForgeRegistry.rename(entry, name)));
    }

    // ---- 通用控件 ----

    private Button cycle(String labelKey, Supplier<Component> value, Runnable action, int x, int y, int w,
                         String tooltipKey) {
        return Button.builder(optionText(labelKey, value.get()), b -> {
            action.run();
            ForgeConfig.save();
            b.setMessage(optionText(labelKey, value.get()));
        }).bounds(x, y, w, BTN_H)
                .tooltip(Tooltip.create(Component.translatable(tooltipKey))).build();
    }

    private Button toggle(String labelKey, BooleanSupplier getter, Consumer<Boolean> setter, int x, int y, int w,
                          String tooltipKey) {
        return Button.builder(stateText(labelKey, getter.getAsBoolean()), b -> {
            setter.accept(!getter.getAsBoolean());
            ForgeConfig.save();
            b.setMessage(stateText(labelKey, getter.getAsBoolean()));
        }).bounds(x, y, w, BTN_H)
                .tooltip(Tooltip.create(Component.translatable(tooltipKey))).build();
    }

    private static MutableComponent optionText(String labelKey, Component value) {
        return Component.translatable("eyelocator.screen.option", Component.translatable(labelKey), value);
    }

    private static MutableComponent stateText(String labelKey, boolean on) {
        return Component.translatable("eyelocator.screen.option", Component.translatable(labelKey),
                Component.translatable(on ? "eyelocator.screen.on" : "eyelocator.screen.off"));
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
        boolean narrow = this.width < NARROW_WIDTH;
        Component status;
        if (!locator.hasEstimate()) {
            status = Component.translatable("eyelocator.screen.status.unlocated",
                    ClientSetup.channelName(channel.id), locator.sampleCount());
        } else {
            String coords = "X:" + Fmt.number(locator.estX()) + " Z:" + Fmt.number(locator.estZ());
            status = narrow
                    ? Component.translatable("eyelocator.screen.status.narrow",
                            ClientSetup.channelName(channel.id), coords, locator.sampleCount())
                    : Component.translatable("eyelocator.screen.status.full",
                            ClientSetup.channelName(channel.id), coords, locator.sampleCount(),
                            Math.round(locator.confidence() * 100));
        }
        graphics.drawCenteredString(this.font, status, this.width / 2, 46, COLOR_DIM);

        int bottom = 60 + 21 * 5 + BTN_H + 10;
        graphics.drawCenteredString(this.font, Component.translatable(narrow
                        ? "eyelocator.screen.more.narrow" : "eyelocator.screen.more.full"),
                this.width / 2, bottom, COLOR_DIM);
    }

    private void renderArchive(GuiGraphics graphics, int mouseX, int mouseY) {
        String dimension = this.minecraft == null || this.minecraft.level == null
                ? "" : this.minecraft.level.dimension().location().toString();
        boolean narrow = this.width < NARROW_WIDTH;

        int left = 14;
        int right = this.width - 14;
        int rowTop = 64;

        graphics.drawString(this.font, Component.translatable(narrow
                        ? "eyelocator.screen.archive.header.narrow"
                        : "eyelocator.screen.archive.header",
                        dimName(dimension)),
                left, 48, COLOR_DIM);
        if (!narrow) {
            graphics.drawString(this.font, Component.translatable("eyelocator.screen.archive.rename_hint"),
                    right - 90, 48, COLOR_DIM);
        }

        this.rowsPerPage = Math.max(3, (this.height - 46 - 8 - rowTop) / ROW_HEIGHT);

        List<ForgeRegistry.Entry> all = ForgeRegistry.entries();
        this.rowBoxes.clear();
        this.deleteBoxes.clear();

        if (this.archiveCurrentButton != null) {
            this.archiveCurrentButton.active = ClientSetup.locator().hasEstimate();
        }

        if (all.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("eyelocator.screen.archive.empty"),
                    left, rowTop, COLOR_DIM);
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

            Component name = entry.name == null || entry.name.isEmpty()
                    ? Component.translatable("eyelocator.screen.unnamed")
                    : Component.literal(entry.name);
            LocatorChannel owner = ChannelManager.byId(entry.channel);
            Component channelName = owner == null ? Component.literal("-") : ClientSetup.channelText(owner.id);
            int confidence = (int) Math.round(entry.confidence * 100);
            // 窄屏（320×240）放不下通道名，先省掉它，坐标和维度优先
            Component line = narrow
                    ? Component.translatable("eyelocator.screen.archive.entry.narrow",
                            name, Fmt.number(entry.x), Fmt.number(entry.z), dimName(entry.dimension), confidence)
                    : Component.translatable("eyelocator.screen.entry",
                            name, Fmt.number(entry.x), Fmt.number(entry.z), dimName(entry.dimension),
                            channelName, confidence);
            graphics.drawString(this.font, line, left, y + 4,
                    entry.dimension.equals(dimension) ? COLOR_TEXT : COLOR_DIM);
            this.rowBoxes.add(new int[]{left, y - 2, right - left - 22, 16, i});

            int boxX = right - 18;
            boolean hovered = mouseX >= boxX && mouseX <= boxX + 14 && mouseY >= y && mouseY <= y + 14;
            graphics.fill(boxX, y, boxX + 14, y + 14, hovered ? 0xFF883333 : 0xFF552222);
            graphics.drawCenteredString(this.font, Component.literal("×"), boxX + 7, y + 3, COLOR_TITLE);
            this.deleteBoxes.add(new int[]{boxX, y, 14, 14, i});
        }

        graphics.drawString(this.font,
                Component.translatable("eyelocator.screen.archive.page", this.page + 1, pages, all.size()),
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

    private static Component styleName(String style) {
        if ("circle".equalsIgnoreCase(style)) {
            return Component.translatable("eyelocator.screen.style.circle");
        }
        if ("cross".equalsIgnoreCase(style)) {
            return Component.translatable("eyelocator.screen.style.cross");
        }
        return Component.translatable("eyelocator.screen.style.diamond");
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

    private static Component dimName(String dimension) {
        if (dimension == null) {
            return Component.literal("?");
        }
        return switch (dimension) {
            case "minecraft:overworld" -> Component.translatable("eyelocator.screen.dim.overworld");
            case "minecraft:the_nether" -> Component.translatable("eyelocator.screen.dim.nether");
            case "minecraft:the_end" -> Component.translatable("eyelocator.screen.dim.end");
            default -> Component.literal(dimension);
        };
    }
}