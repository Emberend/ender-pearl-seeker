package com.eyelocator.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import net.minecraft.text.Text;

/** 客户端命令 {@code /eyelocator ...}：在游戏内查看与修改配置。 */
public final class EyeLocatorCommands {

    private EyeLocatorCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> build(dispatcher));
    }

    private static void build(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("eyelocator")
                .executes(ctx -> {
                    printStatus(ctx.getSource());
                    return 1;
                })
                .then(ClientCommandManager.literal("reload")
                        .executes(ctx -> {
                            EyeLocatorConfig.load();
                            feedback(ctx.getSource(), "配置已重载");
                            return 1;
                        }))
                .then(ClientCommandManager.literal("reset")
                        .executes(ctx -> {
                            EyeLocatorClient.endCurrent();
                            feedback(ctx.getSource(), "已结束当前定位（归档保留）");
                            return 1;
                        }))
                .then(ClientCommandManager.literal("clearall")
                        .executes(ctx -> {
                            EyeLocatorClient.clearEverything();
                            feedback(ctx.getSource(), "已清空当前定位与全部归档");
                            return 1;
                        }))
                .then(ClientCommandManager.literal("gui")
                        .executes(ctx -> {
                            net.minecraft.client.MinecraftClient client =
                                    net.minecraft.client.MinecraftClient.getInstance();
                            client.execute(() -> client.setScreen(new EyeLocatorScreen(client.currentScreen)));
                            return 1;
                        }))
                .then(ClientCommandManager.literal("style")
                        .then(ClientCommandManager.argument("style", StringArgumentType.word())
                                .executes(ctx -> {
                                    String style = StringArgumentType.getString(ctx, "style");
                                    if (!style.equals("diamond") && !style.equals("circle") && !style.equals("cross")) {
                                        feedback(ctx.getSource(), "样式只能是 diamond / circle / cross");
                                        return 0;
                                    }
                                    EyeLocatorConfig.get().markerStyle = style;
                                    EyeLocatorConfig.save();
                                    feedback(ctx.getSource(), "路标样式: " + style);
                                    return 1;
                                })))
                .then(ClientCommandManager.literal("scale")
                        .then(ClientCommandManager.argument("scale", DoubleArgumentType.doubleArg(0.4, 3.0))
                                .executes(ctx -> {
                                    double scale = DoubleArgumentType.getDouble(ctx, "scale");
                                    EyeLocatorConfig.get().markerScale = scale;
                                    EyeLocatorConfig.save();
                                    feedback(ctx.getSource(), String.format("路标缩放: %.2f", scale));
                                    return 1;
                                })))
                .then(ClientCommandManager.literal("baseline")
                        .then(ClientCommandManager.argument("blocks", DoubleArgumentType.doubleArg(50.0, 2000.0))
                                .executes(ctx -> {
                                    double blocks = DoubleArgumentType.getDouble(ctx, "blocks");
                                    EyeLocatorConfig.get().baseline = blocks;
                                    EyeLocatorConfig.save();
                                    feedback(ctx.getSource(), String.format("最佳投掷点横向距离: %.0f 格", blocks));
                                    return 1;
                                })))
                .then(ClientCommandManager.literal("color")
                        .then(ClientCommandManager.literal("stronghold")
                                .then(ClientCommandManager.argument("hex", StringArgumentType.word())
                                        .executes(ctx -> setColor(ctx.getSource(), true,
                                                StringArgumentType.getString(ctx, "hex")))))
                        .then(ClientCommandManager.literal("advice")
                                .then(ClientCommandManager.argument("hex", StringArgumentType.word())
                                        .executes(ctx -> setColor(ctx.getSource(), false,
                                                StringArgumentType.getString(ctx, "hex"))))))
                .then(ClientCommandManager.literal("archived")
                        .then(ClientCommandManager.argument("on", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean on = BoolArgumentType.getBool(ctx, "on");
                                    EyeLocatorConfig.get().showArchived = on;
                                    EyeLocatorConfig.save();
                                    feedback(ctx.getSource(), "显示已归档目标: " + (on ? "开" : "关"));
                                    return 1;
                                })))
                .then(ClientCommandManager.literal("track")
                        .then(ClientCommandManager.literal("next")
                                .executes(ctx -> {
                                    EntityClassifier.requestLearning();
                                    feedback(ctx.getSource(),
                                            "已进入识别模式：15 秒内扔出道具，第一个飞行物会被记住");
                                    return 1;
                                }))
                        .then(ClientCommandManager.literal("stop")
                                .executes(ctx -> {
                                    EntityClassifier.stopLearning();
                                    feedback(ctx.getSource(), "已退出识别模式");
                                    return 1;
                                }))
                        .then(ClientCommandManager.argument("entity", StringArgumentType.string())
                                .executes(ctx -> addTracked(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "entity")))))
                .then(ClientCommandManager.literal("channels")
                        .executes(ctx -> {
                            printChannels(ctx.getSource());
                            return 1;
                        }))
                .then(ClientCommandManager.literal("channel")
                        .then(ClientCommandManager.argument("entity", StringArgumentType.string())
                                .executes(ctx -> switchChannel(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "entity")))))
        );
    }

    private static int setColor(FabricClientCommandSource source, boolean stronghold, String hex) {
        if (EyeLocatorConfig.parseColor(hex, 0) == 0) {
            feedback(source, "颜色格式: #RRGGBB");
            return 0;
        }
        EyeLocatorConfig cfg = EyeLocatorConfig.get();
        if (stronghold) {
            cfg.colorStronghold = hex;
        } else {
            cfg.colorAdvice = hex;
        }
        EyeLocatorConfig.save();
        feedback(source, (stronghold ? "要塞" : "投掷点") + "颜色: " + hex);
        return 1;
    }

    private static void printStatus(FabricClientCommandSource source) {
        EyeLocatorConfig cfg = EyeLocatorConfig.get();
        LocatorChannel channel = ChannelManager.active();
        StrongholdLocator locator = channel.locator;
        feedback(source, "=== 末影寻珠 ===");
        feedback(source, String.format("样式 %s · 缩放 %.2f · 粗细 %.1f", cfg.markerStyle, cfg.markerScale, cfg.markerThickness));
        feedback(source, String.format("基线距离 %.0f · 异常阈值 %.0f", cfg.baseline, cfg.outlierResidual));
        feedback(source, String.format("[%s] 样本 %d · 已归档 %d · %s",
                channel.displayName, locator.sampleCount(), StrongholdRegistry.size(channel.id),
                locator.hasEstimate() ? "已定位" : "未定位"));
        if (ChannelManager.all().size() > 1) {
            feedback(source, "共 " + ChannelManager.all().size() + " 个通道，用 /eyelocator channels 查看");
        }
        feedback(source, "用法: /eyelocator reload|reset|clearall|gui|style|scale|baseline|color|archived|track|channels|channel");
    }

    private static int addTracked(FabricClientCommandSource source, String entityId) {
        String id = entityId.trim();
        if (id.isEmpty()) {
            feedback(source, "用法: /eyelocator track <实体id>，例如 someMod:ancient_eye");
            return 0;
        }
        if (!id.contains(":")) {
            id = "minecraft:" + id;
        }
        EyeLocatorConfig cfg = EyeLocatorConfig.get();
        if (!cfg.trackedEntities.contains(id)) {
            cfg.trackedEntities.add(id);
            EyeLocatorConfig.save();
        }
        LocatorChannel channel = ChannelManager.getOrCreate(id);
        ChannelManager.setActive(channel);
        feedback(source, "已加入追踪: " + id + "（" + channel.displayName + "）");
        return 1;
    }

    private static int switchChannel(FabricClientCommandSource source, String entityId) {
        String id = entityId.trim();
        if (!id.contains(":")) {
            id = "minecraft:" + id;
        }
        LocatorChannel channel = ChannelManager.getOrCreate(id);
        ChannelManager.setActive(channel);
        feedback(source, "当前通道: " + channel.displayName + "（" + channel.id + "）");
        return 1;
    }

    private static void printChannels(FabricClientCommandSource source) {
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
                    StrongholdRegistry.size(channel.id),
                    channel.locator.hasEstimate() ? " · 已定位" : ""));
        }
        feedback(source, "用 /eyelocator channel <实体id> 切换 HUD 显示的通道");
    }

    private static void feedback(FabricClientCommandSource source, String message) {
        source.sendFeedback(Text.literal(message));
    }
}
