package com.eyelocator.client;

import com.eyelocator.core.ChannelManager;
import com.eyelocator.core.LocatorChannel;
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

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Mod 客户端入口：事件注册、配置加载、命令、归档/变更检测。 */
public final class EyeLocatorClient implements ClientModInitializer {

    public static final String MOD_ID = "eyelocator";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static KeyBinding clearKey;
    private static KeyBinding menuKey;
    private static String currentDimension;

    @Override
    public void onInitializeClient() {
        EyeLocatorConfig.load();
        StrongholdRegistry.load();

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
            notifyPlayer("已识别定位道具：" + id + "（已写入配置，之后会自动追踪）");
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
        }
        currentDimension = dimension;

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
    }

    /** 彻底清空：当前定位 + 全部归档。 */
    public static void clearEverything() {
        ChannelManager.resetAllRays();
        EyeTracker.clear();
        StrongholdRegistry.clear();
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
            notifyPlayer("检测到不同的目标：已归档上一个，重新定位中");
        }

        channel.locator.addRay(ray);

        if (dimension != null && channel.locator.hasEstimate()) {
            StrongholdRegistry.update(channel.id, channel.locator.estX(), channel.locator.estZ(),
                    channel.locator.confidence(), dimension);
        }
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

    private static void notifyPlayer(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal(message), false);
        }
    }
}