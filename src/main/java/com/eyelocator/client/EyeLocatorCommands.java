package com.eyelocator.client;

import com.eyelocator.core.ChannelManager;
import com.eyelocator.core.LocatorChannel;
import com.eyelocator.core.StrongholdLocator;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import net.minecraft.client.MinecraftClient;
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
                            EyeLocatorClient.loadCompat();
                            EyeLocatorConfig.load();
                            feedback(ctx.getSource(), Text.translatable("eyelocator.cmd.reloaded"));
                            return 1;
                        }))
                .then(ClientCommandManager.literal("reset")
                        .executes(ctx -> {
                            EyeLocatorClient.endCurrent();
                            feedback(ctx.getSource(), Text.translatable("eyelocator.cmd.reset_done"));
                            return 1;
                        }))
                .then(ClientCommandManager.literal("clearall")
                        .executes(ctx -> {
                            EyeLocatorClient.clearEverything();
                            feedback(ctx.getSource(), Text.translatable("eyelocator.cmd.cleared"));
                            return 1;
                        }))
                .then(ClientCommandManager.literal("gui")
                        .executes(ctx -> {
                            MinecraftClient client = MinecraftClient.getInstance();
                            client.execute(() -> client.setScreen(new EyeLocatorScreen(client.currentScreen)));
                            return 1;
                        }))
                .then(ClientCommandManager.literal("style")
                        .then(ClientCommandManager.argument("style", StringArgumentType.word())
                                .executes(ctx -> {
                                    String style = StringArgumentType.getString(ctx, "style");
                                    if (!style.equals("diamond") && !style.equals("circle") && !style.equals("cross")) {
                                        feedback(ctx.getSource(), Text.translatable("eyelocator.cmd.style_only"));
                                        return 0;
                                    }
                                    EyeLocatorConfig.get().markerStyle = style;
                                    EyeLocatorConfig.save();
                                    feedback(ctx.getSource(), Text.translatable("eyelocator.cmd.style_set", style));
                                    return 1;
                                })))
                .then(ClientCommandManager.literal("scale")
                        .then(ClientCommandManager.argument("scale", DoubleArgumentType.doubleArg(0.4, 3.0))
                                .executes(ctx -> {
                                    double scale = DoubleArgumentType.getDouble(ctx, "scale");
                                    EyeLocatorConfig.get().markerScale = scale;
                                    EyeLocatorConfig.save();
                                    feedback(ctx.getSource(), Text.translatable("eyelocator.cmd.scale_set", scale));
                                    return 1;
                                })))
                .then(ClientCommandManager.literal("baseline")
                        .then(ClientCommandManager.argument("blocks", DoubleArgumentType.doubleArg(50.0, 2000.0))
                                .executes(ctx -> {
                                    double blocks = DoubleArgumentType.getDouble(ctx, "blocks");
                                    EyeLocatorConfig.get().baseline = blocks;
                                    EyeLocatorConfig.save();
                                    feedback(ctx.getSource(), Text.translatable("eyelocator.cmd.baseline_set", blocks));
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
                                    feedback(ctx.getSource(), Text.translatable("eyelocator.cmd.archived_set",
                                            Text.translatable(on ? "eyelocator.screen.on" : "eyelocator.screen.off")));
                                    return 1;
                                })))
                .then(ClientCommandManager.literal("track")
                        .then(ClientCommandManager.literal("next")
                                .executes(ctx -> {
                                    EntityClassifier.requestLearning();
                                    feedback(ctx.getSource(), Text.translatable("eyelocator.cmd.learn_start"));
                                    return 1;
                                }))
                        .then(ClientCommandManager.literal("stop")
                                .executes(ctx -> {
                                    EntityClassifier.stopLearning();
                                    feedback(ctx.getSource(), Text.translatable("eyelocator.cmd.learn_stop"));
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
            feedback(source, Text.translatable("eyelocator.cmd.color_format"));
            return 0;
        }
        EyeLocatorConfig cfg = EyeLocatorConfig.get();
        if (stronghold) {
            cfg.colorStronghold = hex;
        } else {
            cfg.colorAdvice = hex;
        }
        EyeLocatorConfig.save();
        feedback(source, Text.translatable("eyelocator.cmd.color_set",
                Text.translatable(stronghold ? "eyelocator.cmd.color_stronghold" : "eyelocator.cmd.color_advice"),
                hex));
        return 1;
    }

    private static void printStatus(FabricClientCommandSource source) {
        EyeLocatorConfig cfg = EyeLocatorConfig.get();
        LocatorChannel channel = ChannelManager.active();
        StrongholdLocator locator = channel.locator;
        feedback(source, Text.translatable("eyelocator.cmd.status_header"));
        feedback(source, Text.translatable("eyelocator.cmd.status_style",
                cfg.markerStyle, cfg.markerScale, cfg.markerThickness));
        feedback(source, Text.translatable("eyelocator.cmd.status_tuning",
                cfg.baseline, cfg.outlierResidual));
        feedback(source, Text.translatable("eyelocator.cmd.status_channel",
                EyeLocatorClient.channelName(channel.id), locator.sampleCount(), StrongholdRegistry.size(channel.id),
                Text.translatable(locator.hasEstimate()
                        ? "eyelocator.cmd.status_located" : "eyelocator.cmd.status_unlocated")));
        if (ChannelManager.all().size() > 1) {
            feedback(source, Text.translatable("eyelocator.cmd.status_multi", ChannelManager.all().size()));
        }
        feedback(source, Text.translatable("eyelocator.cmd.usage"));
    }

    private static int addTracked(FabricClientCommandSource source, String entityId) {
        String id = entityId.trim();
        if (id.isEmpty()) {
            feedback(source, Text.translatable("eyelocator.cmd.track_usage"));
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
        feedback(source, Text.translatable("eyelocator.cmd.track_added", id, EyeLocatorClient.channelName(channel.id)));
        return 1;
    }

    private static int switchChannel(FabricClientCommandSource source, String entityId) {
        String id = entityId.trim();
        if (!id.contains(":")) {
            id = "minecraft:" + id;
        }
        LocatorChannel channel = ChannelManager.getOrCreate(id);
        ChannelManager.setActive(channel);
        EyeLocatorClient.saveState();
        feedback(source, Text.translatable("eyelocator.cmd.channel_switched", EyeLocatorClient.channelName(channel.id), channel.id));
        return 1;
    }

    private static void printChannels(FabricClientCommandSource source) {
        if (ChannelManager.all().isEmpty()) {
            feedback(source, Text.translatable("eyelocator.cmd.no_channels"));
            return;
        }
        feedback(source, Text.translatable("eyelocator.cmd.channels_header"));
        for (LocatorChannel channel : ChannelManager.all()) {
            feedback(source, Text.translatable("eyelocator.cmd.channel_line",
                    channel == ChannelManager.active() ? "*" : " ",
                    EyeLocatorClient.channelName(channel.id),
                    channel.locator.sampleCount(),
                    StrongholdRegistry.size(channel.id),
                    channel.locator.hasEstimate()
                            ? Text.translatable("eyelocator.cmd.status_located_suffix")
                            : Text.literal("")));
        }
        feedback(source, Text.translatable("eyelocator.cmd.channels_footer"));
    }

    private static void feedback(FabricClientCommandSource source, Text message) {
        source.sendFeedback(message);
    }
}