package com.eyelocator.client;

import com.eyelocator.core.ChannelManager;
import com.eyelocator.core.Fmt;
import com.eyelocator.core.LocatorChannel;
import com.eyelocator.core.StrongholdLocator;
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
 * 末影寻珠 管理界面。设置页只保留少量直观选项；
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
        super(Text.translatable("eyelocator.screen.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.translatable("eyelocator.screen.tab.settings"), b -> switchTab(0))
                .dimensions(this.width / 2 - 105, 30, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("eyelocator.screen.tab.archive"), b -> switchTab(1))
                .dimensions(this.width / 2 + 5, 30, 100, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("eyelocator.screen.done"), b -> close())
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
        int colW = 146;
        int gapX = 8;
        int left = (this.width - (colW * 2 + gapX)) / 2;
        int right = left + colW + gapX;
        int top = 60;
        int rowGap = 26;
        int h = 20;

        ButtonWidget style = cycle("eyelocator.screen.style", () -> styleName(cfg.markerStyle),
                () -> cfg.markerStyle = nextStyle(cfg.markerStyle), left, top, colW, h);
        style.setTooltip(Tooltip.of(Text.translatable("eyelocator.screen.style.tooltip")));
        addDrawableChild(style);

        ButtonWidget scale = cycle("eyelocator.screen.scale", () -> Text.literal(String.format("%.1f", cfg.markerScale)),
                () -> cfg.markerScale = nextDouble(SCALES, cfg.markerScale), right, top, colW, h);
        scale.setTooltip(Tooltip.of(Text.translatable("eyelocator.screen.scale.tooltip")));
        addDrawableChild(scale);

        addDrawableChild(toggle("eyelocator.screen.show_distance", "eyelocator.screen.show_distance.tooltip",
                () -> cfg.showDistance, v -> cfg.showDistance = v, left, top + rowGap, colW, h));

        addDrawableChild(toggle("eyelocator.screen.show_panel", "eyelocator.screen.show_panel.tooltip",
                () -> cfg.showPanel, v -> cfg.showPanel = v, right, top + rowGap, colW, h));

        addDrawableChild(toggle("eyelocator.screen.show_archived", "eyelocator.screen.show_archived.tooltip",
                () -> cfg.showArchived, v -> cfg.showArchived = v, left, top + rowGap * 2, colW, h));

        addDrawableChild(toggle("eyelocator.screen.hide_archived", "eyelocator.screen.hide_archived.tooltip",
                () -> cfg.hideArchivedWhileSearching, v -> cfg.hideArchivedWhileSearching = v,
                right, top + rowGap * 2, colW, h));

        addDrawableChild(toggle("eyelocator.screen.notify_chat", "eyelocator.screen.notify_chat.tooltip",
                () -> cfg.notifyChat, v -> cfg.notifyChat = v, left, top + rowGap * 3, colW, h));

        ButtonWidget reset = ButtonWidget.builder(Text.translatable("eyelocator.screen.reset"), b -> {
            cfg.markerStyle = "diamond";
            cfg.markerScale = 1.0;
            cfg.showDistance = true;
            cfg.showPanel = true;
            cfg.showArchived = true;
            cfg.hideArchivedWhileSearching = true;
            cfg.notifyChat = true;
            EyeLocatorConfig.save();
            this.clearAndInit();
        }).dimensions(right, top + rowGap * 3, colW, h).build();
        reset.setTooltip(Tooltip.of(Text.translatable("eyelocator.screen.reset.tooltip")));
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

        ButtonWidget prev = ButtonWidget.builder(Text.translatable("eyelocator.screen.archive.prev"), b -> {
            if (page > 0) {
                page--;
            }
        }).dimensions(x, y, navW, h).build();
        prev.active = page > 0;
        addDrawableChild(prev);
        x += navW + gap;

        addDrawableChild(ButtonWidget.builder(Text.translatable("eyelocator.screen.archive.next"), b -> page++)
                .dimensions(x, y, navW, h).build());
        x += navW + gap;

        ButtonWidget addCurrent = ButtonWidget.builder(Text.translatable("eyelocator.screen.archive.add"), b -> archiveCurrent())
                .dimensions(x, y, addW, h).build();
        addCurrent.active = EyeLocatorClient.locator().hasEstimate();
        addCurrent.setTooltip(Tooltip.of(Text.translatable("eyelocator.screen.archive.add.tooltip")));
        addDrawableChild(addCurrent);
        x += addW + gap;

        addDrawableChild(ButtonWidget.builder(Text.translatable("eyelocator.screen.archive.dedupe"), b -> {
            int merged = StrongholdRegistry.dedupe(EyeLocatorConfig.get().dedupeDistance);
            page = 0;
            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(Text.translatable("eyelocator.chat.deduped", merged), true);
            }
        }).dimensions(x, y, dedupeW, h)
                .tooltip(Tooltip.of(Text.translatable("eyelocator.screen.archive.dedupe.tooltip")))
                .build());
        x += dedupeW + gap;

        addDrawableChild(ButtonWidget.builder(Text.translatable("eyelocator.screen.archive.clear"), b -> {
            StrongholdRegistry.clear();
            page = 0;
        }).dimensions(x, y, clearW, h).build());
    }

    private void archiveCurrent() {
        StrongholdLocator locator = EyeLocatorClient.locator();
        if (!locator.hasEstimate() || this.client == null || this.client.world == null) {
            return;
        }
        String dimension = this.client.world.getRegistryKey().getValue().toString();
        String channelId = EyeLocatorClient.activeChannelId();
        double x = locator.estX();
        double z = locator.estZ();
        this.client.setScreen(new TextPromptScreen(this,
                Text.translatable("eyelocator.prompt.archive_title"), "",
                name -> StrongholdRegistry.add(channelId, name, x, z, dimension)));
    }

    private void renameEntry(StrongholdRegistry.Entry entry) {
        if (this.client == null) {
            return;
        }
        String initial = entry.name == null ? "" : entry.name;
        this.client.setScreen(new TextPromptScreen(this,
                Text.translatable("eyelocator.prompt.rename_title"), initial,
                name -> StrongholdRegistry.rename(entry, name)));
    }

    // ---- 通用控件 ----

    private ButtonWidget cycle(String labelKey, Supplier<Text> value, Runnable action,
                               int x, int y, int w, int h) {
        return ButtonWidget.builder(optionText(labelKey, value.get()), b -> {
            action.run();
            EyeLocatorConfig.save();
            b.setMessage(optionText(labelKey, value.get()));
        }).dimensions(x, y, w, h).build();
    }

    private ButtonWidget toggle(String labelKey, String tooltipKey, BooleanSupplier getter,
                                Consumer<Boolean> setter, int x, int y, int w, int h) {
        ButtonWidget button = ButtonWidget.builder(stateText(labelKey, getter.getAsBoolean()), b -> {
            setter.accept(!getter.getAsBoolean());
            EyeLocatorConfig.save();
            b.setMessage(stateText(labelKey, getter.getAsBoolean()));
        }).dimensions(x, y, w, h).build();
        button.setTooltip(Tooltip.of(Text.translatable(tooltipKey)));
        return button;
    }

    private static Text optionText(String labelKey, Text value) {
        return Text.translatable("eyelocator.screen.option", Text.translatable(labelKey), value);
    }

    private static Text stateText(String labelKey, boolean on) {
        return Text.translatable("eyelocator.screen.option", Text.translatable(labelKey),
                Text.translatable(on ? "eyelocator.screen.on" : "eyelocator.screen.off"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFFFF);

        if (tab == 0) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.translatable("eyelocator.screen.advanced"),
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
                Text.translatable("eyelocator.screen.archive.header", dimName(dimension)), left, 44, 0xFFAAAAAA);
        context.drawTextWithShadow(this.textRenderer,
                Text.translatable("eyelocator.screen.archive.rename_hint"), right - 90, 44, 0xFF777777);

        List<StrongholdRegistry.Entry> all = StrongholdRegistry.entries();
        deleteBoxes.clear();
        rowBoxes.clear();
        if (all.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer,
                    Text.translatable("eyelocator.screen.archive.empty"), left, rowY, 0xFF888888);
            return;
        }

        int pages = Math.max(1, (all.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));

        int start = page * ROWS_PER_PAGE;
        int end = Math.min(all.size(), start + ROWS_PER_PAGE);
        for (int i = start; i < end; i++) {
            StrongholdRegistry.Entry entry = all.get(i);
            int y = rowY + (i - start) * ROW_HEIGHT;

            Text name = entry.name == null || entry.name.isEmpty()
                    ? Text.translatable("eyelocator.screen.unnamed")
                    : Text.literal(entry.name);
            LocatorChannel owner = ChannelManager.byId(entry.channel);
            Text channelName = owner == null ? Text.literal("-") : EyeLocatorClient.channelText(owner.id);
            Text line = Text.translatable("eyelocator.screen.entry", name,
                    Fmt.number(entry.x), Fmt.number(entry.z), dimName(entry.dimension), channelName,
                    Math.round(entry.confidence * 100));
            context.drawTextWithShadow(this.textRenderer, line, left, y + 4,
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
                Text.translatable("eyelocator.screen.archive.page", page + 1, pages, all.size()),
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

    private static Text styleName(String style) {
        return switch (style) {
            case "circle" -> Text.translatable("eyelocator.screen.style.circle");
            case "cross" -> Text.translatable("eyelocator.screen.style.cross");
            default -> Text.translatable("eyelocator.screen.style.diamond");
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

    private static Text dimName(String dimension) {
        if (dimension == null) {
            return Text.literal("?");
        }
        return switch (dimension) {
            case "minecraft:overworld" -> Text.translatable("eyelocator.screen.dim.overworld");
            case "minecraft:the_nether" -> Text.translatable("eyelocator.screen.dim.nether");
            case "minecraft:the_end" -> Text.translatable("eyelocator.screen.dim.end");
            default -> Text.literal(dimension);
        };
    }
}