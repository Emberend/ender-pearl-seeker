package com.eyelocator.forge;

import com.eyelocator.core.ChannelManager;
import com.eyelocator.core.LocatorChannel;
import com.eyelocator.core.StrongholdLocator;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/** 客户端命令 {@code /eyelocator ...}：在游戏内查看与修改配置。 */
public final class ForgeCommands {

    private ForgeCommands() {
    }

    public static void build(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(net.minecraft.commands.Commands.literal("eyelocator")
                .executes(ctx -> {
                    printStatus(ctx.getSource());
                    return 1;
                })
                .then(net.minecraft.commands.Commands.literal("gui")
                        .executes(ctx -> {
                            net.minecraft.client.Minecraft.getInstance()
                                    .setScreen(new ForgeEyeLocatorScreen(null));
                            return 1;
                        }))
                .then(net.minecraft.commands.Commands.literal("reload")
                        .executes(ctx -> {
                            ForgeConfig.load();
                            feedback(ctx.getSource(), "配置已重新加载");
                            return 1;
                        }))
                .then(net.minecraft.commands.Commands.literal("reset")
                        .executes(ctx -> {
                            ClientSetup.endCurrent();
                            feedback(ctx.getSource(), "已结束当前定位（归档保留）");
                            return 1;
                        }))
                .then(net.minecraft.commands.Commands.literal("clearall")
                        .executes(ctx -> {
                            ClientSetup.clearEverything();
                            feedback(ctx.getSource(), "已清空当前定位与全部归档");
                            return 1;
                        }))
                .then(net.minecraft.commands.Commands.literal("style")
                        .then(net.minecraft.commands.Commands.argument("style", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("diamond").suggest("circle").suggest("cross");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String style = StringArgumentType.getString(ctx, "style");
                                    if (!style.equals("diamond") && !style.equals("circle") && !style.equals("cross")) {
                                        feedback(ctx.getSource(), "样式只能是 diamond / circle / cross");
                                        return 0;
                                    }
                                    ForgeConfig.get().markerStyle = style;
                                    ForgeConfig.save();
                                    feedback(ctx.getSource(), "路标样式: " + style);
                                    return 1;
                                })))
                .then(net.minecraft.commands.Commands.literal("scale")
                        .then(net.minecraft.commands.Commands.argument("scale", DoubleArgumentType.doubleArg(0.4, 3.0))
                                .executes(ctx -> {
                                    double scale = DoubleArgumentType.getDouble(ctx, "scale");
                                    ForgeConfig.get().markerScale = scale;
                                    ForgeConfig.save();
                                    feedback(ctx.getSource(), String.format("路标缩放: %.2f", scale));
                                    return 1;
                                })))
                .then(net.minecraft.commands.Commands.literal("baseline")
                        .then(net.minecraft.commands.Commands.argument("distance", DoubleArgumentType.doubleArg(50.0, 2000.0))
                                .executes(ctx -> {
                                    double d = DoubleArgumentType.getDouble(ctx, "distance");
                                    ForgeConfig.get().baseline = d;
                                    ForgeConfig.save();
                                    feedback(ctx.getSource(), String.format("最佳投掷点基准距离: %.0f", d));
                                    return 1;
                                })))
                .then(net.minecraft.commands.Commands.literal("color")
                        .then(net.minecraft.commands.Commands.argument("which", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("stronghold").suggest("advice").suggest("archived");
                                    return builder.buildFuture();
                                })
                                .then(net.minecraft.commands.Commands.argument("hex", StringArgumentType.word())
                                        .executes(ctx -> setColor(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "which"),
                                                StringArgumentType.getString(ctx, "hex"))))))
                .then(net.minecraft.commands.Commands.literal("archived")
                        .then(net.minecraft.commands.Commands.argument("on", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean on = BoolArgumentType.getBool(ctx, "on");
                                    ForgeConfig.get().showArchived = on;
                                    ForgeConfig.save();
                                    feedback(ctx.getSource(), "显示已归档目标: " + (on ? "开" : "关"));
                                    return 1;
                                })))
                .then(net.minecraft.commands.Commands.literal("track")
                        .then(net.minecraft.commands.Commands.literal("next")
                                .executes(ctx -> {
                                    ForgeClassifier.requestLearning();
                                    feedback(ctx.getSource(),
                                            "已进入识别模式：15 秒内使用那个道具，第一个飞行物会被记住");
                                    return 1;
                                }))
                        .then(net.minecraft.commands.Commands.literal("stop")
                                .executes(ctx -> {
                                    ForgeClassifier.stopLearning();
                                    feedback(ctx.getSource(), "已退出识别模式");
                                    return 1;
                                }))
                        .then(net.minecraft.commands.Commands.argument("entity", StringArgumentType.string())
                                .executes(ctx -> addTracked(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "entity")))))
                .then(net.minecraft.commands.Commands.literal("channels")
                        .executes(ctx -> {
                            printChannels(ctx.getSource());
                            return 1;
                        }))
                .then(net.minecraft.commands.Commands.literal("channel")
                        .then(net.minecraft.commands.Commands.argument("entity", StringArgumentType.string())
                                .executes(ctx -> switchChannel(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "entity")))))
        );
    }

    private static void printStatus(CommandSourceStack source) {
        ForgeConfig cfg = ForgeConfig.get();
        LocatorChannel channel = ChannelManager.active();
        StrongholdLocator locator = channel.locator;
        feedback(source, "=== 末影寻珠 (Forge) ===");
        feedback(source, String.format("样式 %s · 缩放 %.2f · 粗细 %.1f",
                cfg.markerStyle, cfg.markerScale, cfg.markerThickness));
        feedback(source, String.format("基线距离 %.0f · 异常阈值 %.0f", cfg.baseline, cfg.outlierResidual));
        feedback(source, String.format("[%s] 样本 %d · 已归档 %d · %s",
                channel.displayName, locator.sampleCount(), ForgeRegistry.size(channel.id),
                locator.hasEstimate() ? "已定位" : "未定位"));
        if (ChannelManager.all().size() > 1) {
            feedback(source, "共 " + ChannelManager.all().size() + " 个通道，用 /eyelocator channels 查看");
        }
        feedback(source, "用法: /eyelocator gui|reload|reset|clearall|style|scale|baseline|color|archived|track|channels|channel");
    }

    private static int setColor(CommandSourceStack source, String which, String hex) {
        ForgeConfig cfg = ForgeConfig.get();
        if ("stronghold".equalsIgnoreCase(which)) {
            cfg.colorStronghold = hex;
        } else if ("advice".equalsIgnoreCase(which)) {
            cfg.colorAdvice = hex;
        } else if ("archived".equalsIgnoreCase(which)) {
            cfg.colorArchived = hex;
        } else {
            feedback(source, "可选: stronghold / advice / archived");
            return 0;
        }
        ForgeConfig.save();
        feedback(source, "颜色已更新: " + which + " = " + hex);
        return 1;
    }

    private static int addTracked(CommandSourceStack source, String entityId) {
        String id = entityId.trim();
        if (id.isEmpty()) {
            feedback(source, "用法: /eyelocator track <实体id>，例如 goety:cryptic_eye");
            return 0;
        }
        if (!id.contains(":")) {
            id = "minecraft:" + id;
        }
        ForgeConfig cfg = ForgeConfig.get();
        if (!cfg.trackedEntities.contains(id)) {
            cfg.trackedEntities.add(id);
            ForgeConfig.save();
        }
        LocatorChannel channel = ChannelManager.getOrCreate(id);
        ChannelManager.setActive(channel);
        feedback(source, "已加入追踪: " + id + "（" + channel.displayName + "）");
        return 1;
    }

    private static int switchChannel(CommandSourceStack source, String entityId) {
        String id = entityId.trim();
        if (!id.contains(":")) {
            id = "minecraft:" + id;
        }
        LocatorChannel channel = ChannelManager.getOrCreate(id);
        ChannelManager.setActive(channel);
        feedback(source, "当前通道: " + channel.displayName + "（" + channel.id + "）");
        return 1;
    }

    private static void printChannels(CommandSourceStack source) {
        if (ChannelManager.all().isEmpty()) {
            feedback(source, "还没有任何定位通道，扔一次道具即可创建");
            return;
        }
        feedback(source, "=== 定位通道 ===");
        for (LocatorChannel channel : ChannelManager.all()) {
            feedback(source, String.format("%s %-24s 样本 %d · 归档 %d%s",
                    channel == ChannelManager.active() ? "*" : " ",
                    channel.displayName,
                    channel.locator.sampleCount(),
                    ForgeRegistry.size(channel.id),
                    channel.locator.hasEstimate() ? " · 已定位" : ""));
        }
        feedback(source, "用 /eyelocator channel <实体id> 切换 HUD 显示的通道");
    }

    private static void feedback(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }
}