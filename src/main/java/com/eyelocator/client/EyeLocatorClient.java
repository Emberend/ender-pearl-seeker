package com.eyelocator.client;

import com.eyelocator.core.ChannelManager;
import com.eyelocator.core.Fmt;
import com.eyelocator.core.LocatorChannel;
import com.eyelocator.core.LocatorState;
import com.eyelocator.core.StrongholdLocator;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import net.fabricmc.loader.api.FabricLoader;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Mod 客户端入口：事件注册、配置加载、命令、归档/变更检测。 */
public final class EyeLocatorClient implements ClientModInitializer {

    public static final String MOD_ID = "eyelocator";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** 进行中的射线快照，放在 config 目录里与其它数据一致。 */
    private static final Path STATE_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("eyelocator_state.json");

    private static KeyBinding clearKey;
    private static KeyBinding menuKey;
    private static String currentDimension;

    /** 从磁盘恢复的射线所属维度，用于第一次进入世界时校验。 */
    private static String restoredDimension;
    private static boolean stateReconciled;

    /** 每个通道上次播报过的坐标，避免同一条方位重复刷屏。 */
    private static final Map<String, double[]> LAST_NOTIFIED = new HashMap<>();

    @Override
    public void onInitializeClient() {
        EyeLocatorConfig.load();
        StrongholdRegistry.load();
        loadState();

        clearKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.eyelocator.clear",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "category.eyelocator"));

        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.eyelocator.menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_I,
                "category.eyelocator"));

        ClientEntityEvents.ENTITY_LOAD.register(EyeLocatorClient::onEntityLoad);
        ClientTickEvents.END_CLIENT_TICK.register(EyeLocatorClient::onClientTick);

        HudRenderCallback.EVENT.register(new HudRenderer());
        EyeLocatorCommands.register();

        LOGGER.info("末影寻珠 已加载：扔末影之眼（或其它模组的同类道具）即可自动记录方位。");
    }

    /** 当前 HUD 展示的通道的定位器。 */
    public static StrongholdLocator locator() {
        return ChannelManager.active().locator;
    }

    /** 当前 HUD 展示的通道 id。 */
    public static String activeChannelId() {
        return ChannelManager.active().id;
    }

    /** 当前维度 id（可能为 null，比如还没进世界）。 */
    public static String currentDimension() {
        return currentDimension;
    }

    private static void onEntityLoad(Entity entity, ClientWorld world) {
        // 手动学习模式优先：用户刚输入过 /eyelocator track next
        if (EntityClassifier.isLearnCandidate(entity, MinecraftClient.getInstance().player)) {
            EntityClassifier.stopLearning();
            String id = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
            EyeLocatorConfig config = EyeLocatorConfig.get();
            if (!config.trackedEntities.contains(id)) {
                config.trackedEntities.add(id);
                EyeLocatorConfig.save();
            }
            ChannelManager.getOrCreate(id);
            EyeTracker.begin(entity, id);
            notifyPlayer(Text.translatable("eyelocator.chat.learned", id));
            return;
        }

        String channelId = EntityClassifier.classify(entity);
        if (channelId != null) {
            EyeTracker.begin(entity, channelId);
        }
    }

    private static void onClientTick(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            return;
        }

        String dimension = client.world.getRegistryKey().getValue().toString();
        if (currentDimension != null && !currentDimension.equals(dimension)) {
            // 注意：此时 currentDimension 还是旧维度，归档要写到旧维度
            archiveAllChannels(currentDimension);
            ChannelManager.resetAllRays();
            EyeTracker.clear();
            LAST_NOTIFIED.clear();
            saveState();
        }
        currentDimension = dimension;

        // 第一次拿到维度时校验磁盘里的残留射线是不是同一个世界的
        if (!stateReconciled) {
            stateReconciled = true;
            if (restoredDimension != null && !restoredDimension.equals(dimension)) {
                ChannelManager.resetAllRays();
                saveState();
                LOGGER.info("丢弃上一个维度的未完成定位（{}）", restoredDimension);
            }
        }

        EyeTracker.tick(client.world);

        while (clearKey != null && clearKey.wasPressed()) {
            endCurrent();
        }

        if (menuKey != null && menuKey.wasPressed() && client.currentScreen == null) {
            client.setScreen(new EyeLocatorScreen(null));
        }
    }

    /**
     * 结束当前的定位并收起 HUD：先把各通道结果归档，再清空当前射线。
     * 不会删除任何已归档的目标。
     */
    public static void endCurrent() {
        archiveAllChannels(currentDimension);
        ChannelManager.resetAllRays();
        EyeTracker.clear();
        LAST_NOTIFIED.clear();
        saveState();
    }

    /** 彻底清空：当前定位 + 全部归档。 */
    public static void clearEverything() {
        ChannelManager.resetAllRays();
        EyeTracker.clear();
        StrongholdRegistry.clear();
        LAST_NOTIFIED.clear();
        saveState();
    }

    /** 一条方位射线完成（道具飞出足够距离）时调用。 */
    static void onRayCompleted(String channelId, StrongholdLocator.Ray ray) {
        EyeLocatorConfig config = EyeLocatorConfig.get();
        String dimension = currentDimension;
        LocatorChannel channel = ChannelManager.getOrCreate(channelId);
        ChannelManager.setActive(channel);

        // 新射线明显不指向当前估计的目标：归档当前结果并重新开始
        if (dimension != null && channel.locator.hasEstimate() && config.detectStrongholdChange
                && channel.locator.perpDistanceToEstimate(ray) > config.changeDistance) {
            StrongholdRegistry.update(channel.id, channel.locator.estX(), channel.locator.estZ(),
                    channel.locator.confidence(), dimension);
            channel.locator.reset();
            notifyPlayer(Text.translatable("eyelocator.chat.changed"));
        }

        channel.locator.addRay(ray);

        if (dimension != null && channel.locator.hasEstimate()) {
            StrongholdRegistry.update(channel.id, channel.locator.estX(), channel.locator.estZ(),
                    channel.locator.confidence(), dimension);
            notifyLocked(channel);
        }
        saveState();
    }

    /** 锁定坐标时发一条聊天消息（可在配置里关掉）。 */
    private static void notifyLocked(LocatorChannel channel) {
        if (!EyeLocatorConfig.get().notifyChat) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        double x = channel.locator.estX();
        double z = channel.locator.estZ();
        double[] last = LAST_NOTIFIED.get(channel.id);
        if (last != null && Math.hypot(last[0] - x, last[1] - z) < 8.0) {
            return;
        }
        LAST_NOTIFIED.put(channel.id, new double[]{x, z});

        double distance = Math.hypot(x - client.player.getX(), z - client.player.getZ());
        client.player.sendMessage(Text.translatable("eyelocator.chat.locked",
                channel.displayName, Fmt.number(x), Fmt.number(z),
                Math.round(distance), Math.round(channel.locator.confidence() * 100)), false);
    }

    /** 把所有通道当前的估计写入归档。 */
    private static void archiveAllChannels(String dimension) {
        if (dimension == null) {
            return;
        }
        for (LocatorChannel channel : ChannelManager.all()) {
            if (channel.locator.hasEstimate()) {
                StrongholdRegistry.update(channel.id, channel.locator.estX(), channel.locator.estZ(),
                        channel.locator.confidence(), dimension);
            }
        }
    }

    // ---- 未完成定位的磁盘持久化 ----

    private static void loadState() {
        try {
            LocatorState state = LocatorState.read(STATE_PATH);
            if (state != null) {
                state.apply();
                restoredDimension = state.dimension;
            }
        } catch (IOException e) {
            LOGGER.warn("读取未完成定位失败: {}", e.getMessage());
        }
    }

    /** 把当前所有射线写盘；没有射线时会删除文件。 */
    public static void saveState() {
        try {
            LocatorState.capture(currentDimension).write(STATE_PATH);
        } catch (IOException e) {
            LOGGER.warn("保存未完成定位失败: {}", e.getMessage());
        }
    }

    private static void notifyPlayer(Text message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(message, false);
        }
    }
}