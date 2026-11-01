package com.eyelocator.forge;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 简单的单行文本输入弹窗，用于命名/重命名目标。 */
public final class ForgeTextPromptScreen extends Screen {

    public interface Callback {
        void accept(String text);
    }

    private final Screen parent;
    private final String initial;
    private final Callback callback;

    private EditBox field;

    public ForgeTextPromptScreen(Screen parent, Component heading, String initial, Callback callback) {
        super(heading);
        this.parent = parent;
        this.initial = initial;
        this.callback = callback;
    }

    @Override
    protected void init() {
        this.field = new EditBox(this.font,
                this.width / 2 - 100, this.height / 2 - 12, 200, 20,
                Component.translatable("eyelocator.prompt.name"));
        this.field.setMaxLength(32);
        this.field.setValue(this.initial == null ? "" : this.initial);
        this.addRenderableWidget(this.field);
        this.setInitialFocus(this.field);

        this.addRenderableWidget(Button.builder(Component.translatable("eyelocator.prompt.confirm"), b -> {
            this.callback.accept(this.field.getValue());
            this.onClose();
        }).bounds(this.width / 2 - 102, this.height / 2 + 20, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("eyelocator.prompt.cancel"), b -> this.onClose())
                .bounds(this.width / 2 + 2, this.height / 2 + 20, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xC0000000);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title,
                this.width / 2, this.height / 2 - 34, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font, Component.translatable("eyelocator.prompt.hint"),
                this.width / 2, this.height / 2 + 46, 0xFF888888);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}