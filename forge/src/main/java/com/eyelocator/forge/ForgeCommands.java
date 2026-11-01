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
import net.minecraft.network.chat.MutableComponent;

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
                            feedback(ctx.getSource(), Component.translatable("eyelocator.cmd.reloaded"));
                            return 1;
                        }))
                .then(net.minecraft.commands.Commands.literal("reset")
                        .executes(ctx -> {
                            ClientSetup.endCurrent();
                            feedback(ctx.getSource(), Component.translatable("eyelocator.cmd.reset_done"));
                            return 1;
                        }))
                .then(net.minecraft.commands.Commands.literal("clearall")
                        .executes(ctx -> {
                            ClientSetup.clearEverything();
                            feedback(ctx.getSource(), Component.translatable("eyelocator.cmd.cleared"));
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
                                        feedback(ctx.getSource(), Component.translatable("eyelocator.cmd.style_only"));
                                        return 0;
                                    }
                                    ForgeConfig.get().markerStyle = style;
                                    ForgeConfig.save();
                                    feedback(ctx.getSource(), Component.translatable("eyelocator.cmd.style_set", style));
                                    return 1;
                                })))
                .then(net.minecraft.commands.Commands.literal("scale")
                        .then(net.minecraft.commands.Commands.argument("scale", DoubleArgumentType.doubleArg(0.4, 3.0))
                                .executes(ctx -> {
                                    double scale = DoubleArgumentType.getDouble(ctx, "scale");
                                    ForgeConfig.get().markerScale = scale;
                                    ForgeConfig.save();
                                    feedback(ctx.getSource(), Component.translatable("eyelocator.cmd.scale_set", scale));
                                    return 1;
                                })))
                .then(net.minecraft.commands.Commands.literal("baseline")
                        .then(net.minecraft.commands.Commands.argument("distance", DoubleArgumentType.doubleArg(50.0, 2000.0))
                                .executes(ctx -> {
                                    double d = DoubleArgumentType.getDouble(ctx, "distance");
                                    ForgeConfig.get().baseline = d;
                                    ForgeConfig.save();
                                    feedback(ctx.getSource(), Component.translatable("eyelocator.cmd.baseline_set", d));
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
                                    feedback(ctx.getSource(), Component.translatable("eyelocator.cmd.archived_set",
                                            Component.translatable(on
                                                    ? "eyelocator.screen.on" : "eyelocator.screen.off")));
                                    return 1;
                                })))
                .then(net.minecraft.commands.Commands.literal("track")
                        .then(net.minecraft.commands.Commands.literal("next")
                                .executes(ctx -> {
                                    ForgeClassifier.requestLearning();
                                    feedback(ctx.getSource(), Component.translatable("eyelocator.cmd.learn_start_forge"));
                                    return 1;
                                }))
                        .then(net.minecraft.commands.Commands.literal("stop")
                                .executes(ctx -> {
                                    ForgeClassifier.stopLearning();
                                    feedback(ctx.getSource(), Component.translatable("eyelocator.cmd.learn_stop"));
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
        feedback(source, Component.translatable("eyelocator.cmd.status_header_forge"));
        feedback(source, Component.translatable("eyelocator.cmd.status_style",
                cfg.markerStyle, cfg.markerScale, cfg.markerThickness));
        feedback(source, Component.translatable("eyelocator.cmd.status_tuning",
                cfg.baseline, cfg.outlierResidual));
        feedback(source, Component.translatable("eyelocator.cmd.status_channel",
                ClientSetup.channelName(channel.id), locator.sampleCount(), ForgeRegistry.size(channel.id),
                Component.translatable(locator.hasEstimate()
                        ? "eyelocator.cmd.status_located" : "eyelocator.cmd.status_unlocated")));
        if (ChannelManager.all().size() > 1) {
            feedback(source, Component.translatable("eyelocator.cmd.status_multi", ChannelManager.all().size()));
        }
        feedback(source, Component.translatable("eyelocator.cmd.usage_forge"));
    }

    private static int setColor(CommandSourceStack source, String which, String hex) {
        ForgeConfig cfg = ForgeConfig.get();
        String key;
        if ("stronghold".equalsIgnoreCase(which)) {
            cfg.colorStronghold = hex;
            key = "eyelocator.cmd.color_stronghold";
        } else if ("advice".equalsIgnoreCase(which)) {
            cfg.colorAdvice = hex;
            key = "eyelocator.cmd.color_advice";
        } else if ("archived".equalsIgnoreCase(which)) {
            cfg.colorArchived = hex;
            key = "eyelocator.cmd.color_archived";
        } else {
            feedback(source, Component.translatable("eyelocator.cmd.color_choices"));
            return 0;
        }
        ForgeConfig.save();
        feedback(source, Component.translatable("eyelocator.cmd.color_updated",
                Component.translatable(key), hex));
        return 1;
    }

    private static int addTracked(CommandSourceStack source, String entityId) {
        String id = entityId.trim();
        if (id.isEmpty()) {
            feedback(source, Component.translatable("eyelocator.cmd.track_usage"));
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
        feedback(source, Component.translatable("eyelocator.cmd.track_added", id, ClientSetup.channelName(channel.id)));
        return 1;
    }

    private static int switchChannel(CommandSourceStack source, String entityId) {
        String id = entityId.trim();
        if (!id.contains(":")) {
            id = "minecraft:" + id;
        }
        LocatorChannel channel = ChannelManager.getOrCreate(id);
        ChannelManager.setActive(channel);
        ClientSetup.saveState();
        feedback(source, Component.translatable("eyelocator.cmd.channel_switched", ClientSetup.channelName(channel.id), channel.id));
        return 1;
    }

    private static void printChannels(CommandSourceStack source) {
        if (ChannelManager.all().isEmpty()) {
            feedback(source, Component.translatable("eyelocator.cmd.no_channels"));
            return;
        }
        feedback(source, Component.translatable("eyelocator.cmd.channels_header"));
        for (LocatorChannel channel : ChannelManager.all()) {
            feedback(source, Component.translatable("eyelocator.cmd.channel_line",
                    channel == ChannelManager.active() ? "*" : " ",
                    ClientSetup.channelName(channel.id),
                    channel.locator.sampleCount(),
                    ForgeRegistry.size(channel.id),
                    channel.locator.hasEstimate()
                            ? Component.translatable("eyelocator.cmd.status_located_suffix")
                            : Component.literal("")));
        }
        feedback(source, Component.translatable("eyelocator.cmd.channels_footer"));
    }

    private static void feedback(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> message, false);
    }
}