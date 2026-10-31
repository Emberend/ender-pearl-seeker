package com.eyelocator.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** 简单的单行文本输入弹窗，用于命名/重命名要塞。 */
public final class TextPromptScreen extends Screen {

    public interface Callback {
        void accept(String text);
    }

    private final Screen parent;
    private final String initial;
    private final Callback callback;
    private TextFieldWidget field;

    public TextPromptScreen(Screen parent, String heading, String initial, Callback callback) {
        super(Text.literal(heading));
        this.parent = parent;
        this.initial = initial;
        this.callback = callback;
    }

    @Override
    protected void init() {
        this.field = new TextFieldWidget(this.textRenderer,
                this.width / 2 - 100, this.height / 2 - 12, 200, 20, Text.literal("名称"));
        this.field.setMaxLength(32);
        this.field.setText(this.initial == null ? "" : this.initial);
        addDrawableChild(this.field);
        this.setInitialFocus(this.field);

        addDrawableChild(ButtonWidget.builder(Text.literal("确定"), b -> {
            callback.accept(this.field.getText());
            close();
        }).dimensions(this.width / 2 - 102, this.height / 2 + 20, 100, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("取消"), b -> close())
                .dimensions(this.width / 2 + 2, this.height / 2 + 20, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title,
                this.width / 2, this.height / 2 - 34, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("留空表示不命名"), this.width / 2, this.height / 2 + 46, 0xFF888888);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }
}
