package com.eyelocator.client;

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
import net.minecraft.entity.EyeOfEnderEntity;
import net.minecraft.text.Text;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Mod 客户端入口：事件注册、配置加载、命令、归档/变更检测。 */
public final class EyeLocatorClient implements ClientModInitializer {

    public static final String MOD_ID = "eyelocator";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final StrongholdLocator LOCATOR = new StrongholdLocator();

    private static KeyBinding clearKey;
    private static String currentDimension;

    @Override
    public void onInitializeClient() {
        EyeLocatorConfig.load();

        clearKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.eyelocator.clear",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "category.eyelocator"));

        ClientEntityEvents.ENTITY_LOAD.register(EyeLocatorClient::onEntityLoad);
        ClientTickEvents.END_CLIENT_TICK.register(EyeLocatorClient::onClientTick);

        HudRenderCallback.EVENT.register(new HudRenderer());
        EyeLocatorCommands.register();

        LOGGER.info("Eye Locator 已加载：扔末影之眼即可自动记录方位。");
    }

    private static void onEntityLoad(Entity entity, ClientWorld world) {
        if (entity instanceof EyeOfEnderEntity) {
            EyeTracker.begin(entity);
        }
    }

    private static void onClientTick(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            return;
        }

        String dimension = client.world.getRegistryKey().getValue().toString();
        if (currentDimension != null && !currentDimension.equals(dimension)) {
            archiveCurrent();
            LOCATOR.reset();
            EyeTracker.clear();
        }
        currentDimension = dimension;

        EyeTracker.tick(client.world);

        while (clearKey != null && clearKey.wasPressed()) {
            LOCATOR.reset();
            EyeTracker.clear();
            StrongholdRegistry.clear();
        }
    }

    /** 一条方位射线完成（眼睛飞出足够距离）时调用。 */
    static void onRayCompleted(StrongholdLocator.Ray ray) {
        EyeLocatorConfig config = EyeLocatorConfig.get();
        String dimension = currentDimension;

        // 新射线明显不指向当前估计的要塞：归档当前结果并重新开始
        if (dimension != null && LOCATOR.hasEstimate() && config.detectStrongholdChange
                && LOCATOR.perpDistanceToEstimate(ray) > config.changeDistance) {
            archiveCurrent();
            LOCATOR.reset();
            notifyPlayer("检测到不同的要塞：已归档上一个，重新定位中");
        }

        LOCATOR.addRay(ray);

        if (dimension != null && LOCATOR.hasEstimate()) {
            StrongholdRegistry.update(LOCATOR.estX(), LOCATOR.estZ(), LOCATOR.confidence(), dimension);
        }
    }

    private static void archiveCurrent() {
        if (LOCATOR.hasEstimate() && currentDimension != null) {
            StrongholdRegistry.update(LOCATOR.estX(), LOCATOR.estZ(), LOCATOR.confidence(), currentDimension);
        }
    }

    private static void notifyPlayer(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal(message), false);
        }
    }
}
