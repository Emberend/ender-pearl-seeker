package com.eyelocator.forge;

import java.util.HashSet;
import java.util.Set;

import com.eyelocator.core.ChannelManager;
import com.eyelocator.core.LocatorChannel;
import com.eyelocator.core.StrongholdLocator;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
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
    private static String currentDimension;
    /** 上一 tick 存在的实体 id，用于发现新加载的实体。 */
    private static Set<Integer> seenEntities = new HashSet<>();

    private ClientSetup() {
    }

    /** 只在客户端调用（由 {@link EyeLocatorForge} 通过 DistExecutor 触发）。 */
    public static void init() {
        ForgeConfig.load();
        ForgeRegistry.load();

        FMLJavaModLoadingContext.get().getModEventBus().addListener(ClientSetup::registerKeys);
        MinecraftForge.EVENT_BUS.register(INSTANCE);

        EyeLocatorForge.LOGGER.info(
                "末影寻珠 (Forge) 已加载：扔末影之眼或其它模组的同类道具即可自动记录方位。");
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        clearKey = new KeyMapping("key.eyelocator.clear", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K, "category.eyelocator");
        event.register(clearKey);
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
        if (currentDimension != null && !currentDimension.equals(dimension)) {
            // 此时 currentDimension 还是旧维度，归档要写到旧维度
            archiveAllChannels(currentDimension);
            ChannelManager.resetAllRays();
            ForgeTracker.clear();
            seenEntities.clear();
        }
        currentDimension = dimension;

        scanEntities(mc, level);
        ForgeTracker.tick(level);

        while (clearKey != null && clearKey.consumeClick()) {
            endCurrent();
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
            notifyPlayer("已识别定位道具：" + id + "（已写入配置，之后会自动追踪）");
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
     * 结束当前的定位并收起 HUD：先把各通道结果归档，再清空当前射线。
     * 不会删除任何已归档的目标。
     */
    public static void endCurrent() {
        archiveAllChannels(currentDimension);
        ChannelManager.resetAllRays();
        ForgeTracker.clear();
        seenEntities.clear();
    }

    /** 彻底清空：当前定位 + 全部归档。 */
    public static void clearEverything() {
        ChannelManager.resetAllRays();
        ForgeTracker.clear();
        seenEntities.clear();
        ForgeRegistry.clear();
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
            notifyPlayer("检测到不同的目标：已归档上一个，重新定位中");
        }

        channel.locator.addRay(ray);

        if (dimension != null && channel.locator.hasEstimate()) {
            ForgeRegistry.update(channel.id, channel.locator.estX(), channel.locator.estZ(),
                    channel.locator.confidence(), dimension);
        }
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

    public static void notifyPlayer(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(message), false);
        }
    }
}