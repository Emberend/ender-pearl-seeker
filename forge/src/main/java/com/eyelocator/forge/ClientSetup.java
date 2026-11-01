package com.eyelocator.forge;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.eyelocator.core.ChannelManager;
import com.eyelocator.core.Fmt;
import com.eyelocator.core.LocatorChannel;
import com.eyelocator.core.LocatorState;
import com.eyelocator.core.StrongholdLocator;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

import org.lwjgl.glfw.GLFW;

/**
 * 客户端专用入口。
 *
 * <p>这个类引用了一堆客户端类型（{@code Minecraft}、{@code KeyMapping}、{@code GuiGraphics} 等），
 * 所以**绝不能**在 {@link EyeLocatorForge} 里直接引用——Forge 的 DistCleaner 会在专用服务端
 * 加载主类时因为常量池里出现客户端类而直接拒绝。只能通过 {@code DistExecutor} 间接加载。</p>
 */
public final class ClientSetup {

    private static final ClientSetup INSTANCE = new ClientSetup();

    private static KeyMapping clearKey;
    private static KeyMapping menuKey;
    private static String currentDimension;
    /** 当前存档/服务器标识，和维度一起决定残留射线是否还适用。 */
    private static String currentWorld;
    /** 上一 tick 存在的实体 id，用于发现新加载的实体。 */
    private static Set<Integer> seenEntities = new HashSet<>();

    private static final Path STATE_PATH =
            FMLPaths.CONFIGDIR.get().resolve("eyelocator_state.json");
    /** 从磁盘恢复的快照，第一次进入世界时用真实的世界/维度校验一次。 */
    private static LocatorState restoredState;
    private static boolean stateReconciled;
    /** 上一次提示"没记录到方位"的时间，避免连续投掷时刷屏。 */
    private static long lastRayWarnMillis;

    /** 单人/多人信息都还拿不到时的占位符；此时不做世界校验，避免误删残留射线。 */
    private static final String UNKNOWN_WORLD = "unknown";
    /** 每个通道上次播报过的坐标，避免同一条方位重复刷屏。 */
    private static final Map<String, double[]> LAST_NOTIFIED = new HashMap<>();

    private ClientSetup() {
    }

    /** 只在客户端调用（由 {@link EyeLocatorForge} 通过 DistExecutor 触发）。 */
    public static void init() {
        ForgeConfig.load();
        ForgeRegistry.load();
        loadState();

        FMLJavaModLoadingContext.get().getModEventBus().addListener(ClientSetup::registerKeys);
        MinecraftForge.EVENT_BUS.register(INSTANCE);

        EyeLocatorForge.LOGGER.info(
                "末影寻珠 (Forge) 已加载：扔末影之眼或其它模组的同类道具即可自动记录方位。");
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        clearKey = new KeyMapping("key.eyelocator.clear", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K, "category.eyelocator");
        event.register(clearKey);

        menuKey = new KeyMapping("key.eyelocator.menu", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_I, "category.eyelocator");
        event.register(menuKey);
    }

    // ---- 事件 ----

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return;
        }

        String dimension = level.dimension().location().toString();
        String worldKey = worldKey(mc);
        boolean sameDimension = currentDimension == null || currentDimension.equals(dimension);
        boolean sameWorld = currentWorld == null || currentWorld.equals(worldKey)
                || UNKNOWN_WORLD.equals(worldKey) || UNKNOWN_WORLD.equals(currentWorld);
        if (!sameDimension || !sameWorld) {
            // 此时 currentDimension 还是旧维度，归档要写到旧维度
            archiveAllChannels(currentDimension);
            ChannelManager.resetAllRays();
            ForgeTracker.clear();
            seenEntities.clear();
            LAST_NOTIFIED.clear();
            saveState();
        }
        currentDimension = dimension;
        currentWorld = worldKey;

        if (!stateReconciled && !UNKNOWN_WORLD.equals(worldKey)) {
            stateReconciled = true;
            if (restoredState != null) {
                String oldWorld = restoredState.world;
                String oldDimension = restoredState.dimension;
                boolean kept = restoredState.keepOnly(dimension, worldKey);
                restoredState = null;
                if (!kept) {
                    ChannelManager.resetAllRays();
                    saveState();
                    EyeLocatorForge.LOGGER.info("丢弃其它世界/维度的未完成定位（{} / {}）",
                            oldWorld, oldDimension);
                }
            }
        }

        scanEntities(mc, level);
        ForgeTracker.tick(level);

        while (clearKey != null && clearKey.consumeClick()) {
            endCurrent();
        }

        if (menuKey != null && menuKey.consumeClick() && mc.screen == null) {
            mc.setScreen(new ForgeEyeLocatorScreen(null));
        }
    }

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        ForgeHud.render(event.getGuiGraphics());
    }

    @SubscribeEvent
    public void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        ForgeCommands.build(event.getDispatcher());
    }

    /** 客户端实体没有可靠的加入事件，这里用"上一 tick 快照"的方式发现新实体。 */
    private static void scanEntities(Minecraft mc, ClientLevel level) {
        Set<Integer> current = new HashSet<>();
        for (Entity entity : level.entitiesForRendering()) {
            current.add(entity.getId());
            if (seenEntities.contains(entity.getId())) {
                continue;
            }
            handleNewEntity(entity, mc.player);
        }
        seenEntities = current;
    }

    private static void handleNewEntity(Entity entity, Entity player) {
        // 手动学习模式优先：用户刚输入过 /eyelocator track next
        if (ForgeClassifier.isLearnCandidate(entity, player)) {
            ForgeClassifier.stopLearning();
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            ForgeConfig config = ForgeConfig.get();
            if (!config.trackedEntities.contains(id)) {
                config.trackedEntities.add(id);
                ForgeConfig.save();
            }
            ChannelManager.getOrCreate(id);
            ForgeTracker.begin(entity, id);
            notifyPlayer(Component.translatable("eyelocator.chat.learned", id));
            return;
        }

        String channelId = ForgeClassifier.classify(entity);
        if (channelId != null) {
            ForgeTracker.begin(entity, channelId);
        }
    }

    // ---- 定位流程 ----

    public static StrongholdLocator locator() {
        return ChannelManager.active().locator;
    }

    public static String activeChannelId() {
        return ChannelManager.active().id;
    }

    /**
     * 通道显示名（纯文本）：用户自定义名称 > 内置名称的翻译 > 实体 id 的显示名。
     * 用于把名字塞进 {@code %s} 占位符。
     */
    public static String channelName(String id) {
        LocatorChannel channel = ChannelManager.byId(id);
        if (channel == null) {
            return id == null ? "-" : id;
        }
        String key = ChannelManager.displayKey(id);
        return key == null ? channel.displayName : Component.translatable(key).getString();
    }

    /** 通道显示名（Component 形式），用于列表/标签。 */
    public static Component channelText(String id) {
        LocatorChannel channel = ChannelManager.byId(id);
        if (channel == null) {
            return Component.literal(id == null ? "-" : id);
        }
        String key = ChannelManager.displayKey(id);
        return key == null ? Component.literal(channel.displayName) : Component.translatable(key);
    }

    /**
     * 结束当前的定位并收起 HUD：先把各通道结果归档，再清空当前射线。
     * 不会删除任何已归档的目标。
     */
    public static void endCurrent() {
        archiveAllChannels(currentDimension);
        ChannelManager.resetAllRays();
        ForgeTracker.clear();
        seenEntities.clear();
        LAST_NOTIFIED.clear();
        saveState();
    }

    /** 彻底清空：当前定位 + 全部归档。 */
    public static void clearEverything() {
        ChannelManager.resetAllRays();
        ForgeTracker.clear();
        seenEntities.clear();
        ForgeRegistry.clear();
        LAST_NOTIFIED.clear();
        saveState();
    }

    /** 一条方位射线完成（道具飞出足够距离）时调用。 */
    static void onRayCompleted(String channelId, StrongholdLocator.Ray ray) {
        ForgeConfig config = ForgeConfig.get();
        String dimension = currentDimension;
        LocatorChannel channel = ChannelManager.getOrCreate(channelId);
        ChannelManager.setActive(channel);

        if (dimension != null && channel.locator.hasEstimate() && config.detectStrongholdChange
                && channel.locator.perpDistanceToEstimate(ray) > config.changeDistance) {
            ForgeRegistry.update(channel.id, channel.locator.estX(), channel.locator.estZ(),
                    channel.locator.confidence(), dimension);
            channel.locator.reset();
            notifyPlayer(Component.translatable("eyelocator.chat.changed"));
        }

        channel.locator.addRay(ray);

        if (dimension != null && channel.locator.hasEstimate()) {
            ForgeRegistry.update(channel.id, channel.locator.estX(), channel.locator.estZ(),
                    channel.locator.confidence(), dimension);
            notifyLocked(channel);
        }
        saveState();
    }

    /** 锁定坐标时发一条聊天消息（可在配置里关掉）。 */
    private static void notifyLocked(LocatorChannel channel) {
        if (!ForgeConfig.get().notifyChat) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        double x = channel.locator.estX();
        double z = channel.locator.estZ();
        double[] last = LAST_NOTIFIED.get(channel.id);
        if (last != null && Math.hypot(last[0] - x, last[1] - z) < 8.0) {
            return;
        }
        LAST_NOTIFIED.put(channel.id, new double[]{x, z});

        double distance = Math.hypot(x - mc.player.getX(), z - mc.player.getZ());
        mc.player.displayClientMessage(Component.translatable("eyelocator.chat.locked",
                channelName(channel.id), Fmt.number(x), Fmt.number(z),
                Math.round(distance), Math.round(channel.locator.confidence() * 100)), false);
    }

    private static void archiveAllChannels(String dimension) {
        if (dimension == null) {
            return;
        }
        for (LocatorChannel channel : ChannelManager.all()) {
            if (channel.locator.hasEstimate()) {
                ForgeRegistry.update(channel.id, channel.locator.estX(), channel.locator.estZ(),
                        channel.locator.confidence(), dimension);
            }
        }
    }

    public static void notifyPlayer(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(message, false);
        }
    }

    // ---- 未完成定位的磁盘持久化 ----

    /**
     * 当前存档/服务器的稳定标识：单人用存档名，多人用服务器地址。
     * 用于把未完成定位限制在同一个世界里，避免同维度换世界后沿用旧射线。
     */
    private static String worldKey(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) {
            String name = server.getWorldData().getLevelName();
            if (name != null && !name.isEmpty()) {
                return "sp:" + name;
            }
        }
        if (mc.getConnection() != null && mc.getConnection().getConnection() != null) {
            String text = String.valueOf(mc.getConnection().getConnection().getRemoteAddress());
            if (!text.isEmpty()) {
                return "mp:" + text;
            }
        }
        return UNKNOWN_WORLD;
    }

    /** 投掷物没飞出足够距离就消失了：提示这次没有记录到方位（节流，避免刷屏）。 */
    public static void onRayFailed(String channelId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastRayWarnMillis < 4000L) {
            return;
        }
        lastRayWarnMillis = now;
        mc.player.displayClientMessage(
                Component.translatable("eyelocator.chat.ray_failed", channelName(channelId)), true);
    }

    private static void loadState() {
        try {
            LocatorState state = LocatorState.read(STATE_PATH);
            if (state != null) {
                state.apply();
                restoredState = state;
            }
        } catch (IOException e) {
            EyeLocatorForge.LOGGER.warn("读取未完成定位失败: {}", e.getMessage());
        }
    }

    /** 把当前所有射线写盘；没有射线时会删除文件。 */
    public static void saveState() {
        try {
            LocatorState.capture(currentDimension, currentWorld).write(STATE_PATH);
        } catch (IOException e) {
            EyeLocatorForge.LOGGER.warn("保存未完成定位失败: {}", e.getMessage());
        }
    }

    /** 当前维度 id（可能为 null，比如还没进世界）。 */
    public static String currentDimension() {
        return currentDimension;
    }
}