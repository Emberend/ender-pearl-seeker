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

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mod 客户端入口：挂接实体加载/每 tick/HUD/世界渲染事件。
 */
public final class EyeLocatorClient implements ClientModInitializer {

    public static final String MOD_ID = "eyelocator";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** 全局唯一的定位状态。 */
    public static final StrongholdLocator LOCATOR = new StrongholdLocator();

    private static KeyBinding clearKey;
    private static Object currentDimension;

    @Override
    public void onInitializeClient() {
        clearKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.eyelocator.clear",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "category.eyelocator"));

        ClientEntityEvents.ENTITY_LOAD.register(EyeLocatorClient::onEntityLoad);
        ClientTickEvents.END_CLIENT_TICK.register(EyeLocatorClient::onClientTick);

        HudRenderCallback.EVENT.register(new HudRenderer());
        WorldGuide.register();

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

        Object dimension = client.world.getRegistryKey();
        if (currentDimension != null && !currentDimension.equals(dimension)) {
            LOCATOR.reset();
            EyeTracker.clear();
        }
        currentDimension = dimension;

        EyeTracker.tick(client.world, LOCATOR);

        while (clearKey != null && clearKey.wasPressed()) {
            LOCATOR.reset();
            EyeTracker.clear();
        }
    }

    /** 让 HUD 能读取到按键绑定状态（用于显示提示）。 */
    static KeyBinding clearKey() {
        return clearKey;
    }
}
