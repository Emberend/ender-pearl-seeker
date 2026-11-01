package com.eyelocator.client;

import com.eyelocator.core.ChannelManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * 简单 JSON 配置，位于 {@code config/eyelocator.json}。
 * 启动时读取；可用 {@code /eyelocator reload} 重载，或用命令直接修改后自动保存。
 */
public final class EyeLocatorConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("eyelocator.json");

    private static EyeLocatorConfig instance = new EyeLocatorConfig();

    public boolean enabled = true;

    /** 路标样式：diamond / circle / cross */
    public String markerStyle = "diamond";
    /** 路标整体缩放。 */
    public double markerScale = 1.0;
    /** 描边粗细（像素，未缩放）。 */
    public double markerThickness = 3.0;

    public String colorStronghold = "#55FF55";
    public String colorAdvice = "#4FD8FF";
    public String colorArchived = "#B0B0B0";

    public boolean showDistance = true;
    public boolean showLabels = true;
    public boolean showPanel = true;

    /** 锁定结果时把坐标发到聊天栏（方便回看/分享）。 */
    public boolean notifyChat = true;

    // ---- 解析后的颜色缓存：避免 HUD 每帧重新解析字符串 ----
    private transient String cachedStrongholdSource;
    private transient int cachedStronghold;
    private transient String cachedAdviceSource;
    private transient int cachedAdvice;
    private transient String cachedArchivedSource;
    private transient int cachedArchived;

    /** 下一最佳投掷点的横向基准距离（格）。 */
    public double baseline = 400.0;

    /** 残差超过该值的射线会被当作异常样本剔除（样本次数 >=3 时生效）。 */
    public double outlierResidual = 80.0;
    /** 新射线明显指向另一个要塞时（离当前估计超过该距离），自动归档并重新开始。 */
    public boolean detectStrongholdChange = true;
    public double changeDistance = 512.0;

    /** 是否显示已归档的其它要塞。 */
    public boolean showArchived = true;
    /** 正在寻找新要塞（尚未形成当前交点）时，隐藏已归档要塞的标记。 */
    public boolean hideArchivedWhileSearching = true;
    /** 去重时：距离小于该值（同维度）的归档会被视为同一个要塞并合并。 */
    public double dedupeDistance = 48.0;

    // ---- 兼容其它模组的定位道具 ----

    /** 是否自动识别 id 里带关键词的实体（关掉后只有白名单与原版眼睛生效）。 */
    public boolean autoDetect = true;

    /** 自动识别的关键词，匹配实体 id 的路径部分。 */
    public List<String> autoDetectKeywords = new ArrayList<>(Arrays.asList(
            "eye", "seeker", "locator", "finder", "stronghold"));

    /** 额外追踪的实体 id，支持 {@code *} 通配，例如 {@code someMod:*eye*}。 */
    public List<String> trackedEntities = new ArrayList<>();

    /** 覆盖通道显示名：实体 id -> 名字，例如 {@code "someMod:ancient_eye": "远古城市"}。 */
    public Map<String, String> channelNames = new LinkedHashMap<>();

    private EyeLocatorConfig() {
    }

    public static EyeLocatorConfig get() {
        return instance;
    }

    public static void load() {
        try {
            if (Files.exists(PATH)) {
                String json = Files.readString(PATH, StandardCharsets.UTF_8);
                EyeLocatorConfig loaded = GSON.fromJson(json, EyeLocatorConfig.class);
                if (loaded != null) {
                    instance = loaded;
                }
            } else {
                save();
            }
        } catch (Exception e) {
            EyeLocatorClient.LOGGER.warn("读取配置失败，使用默认值: {}", e.getMessage());
        }
        instance.normalize();
        applyToCommon(instance);
    }

    /** 把配置推给与加载器无关的共享层。 */
    private static void applyToCommon(EyeLocatorConfig cfg) {
        ChannelManager.setNameOverrides(cfg.channelNames);
        ChannelManager.setOutlierResidual(cfg.outlierResidual);
    }

    /** 补齐旧配置里缺失的字段，避免 Gson 反序列化出 null。 */
    private void normalize() {
        if (autoDetectKeywords == null || autoDetectKeywords.isEmpty()) {
            autoDetectKeywords = new ArrayList<>(Arrays.asList(
                    "eye", "seeker", "locator", "finder", "stronghold"));
        }
        if (trackedEntities == null) {
            trackedEntities = new ArrayList<>();
        }
        if (channelNames == null) {
            channelNames = new LinkedHashMap<>();
        }
    }

    public static void save() {
        instance.normalize();
        applyToCommon(instance);
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(instance), StandardCharsets.UTF_8);
        } catch (Exception e) {
            EyeLocatorClient.LOGGER.warn("保存配置失败: {}", e.getMessage());
        }
    }

    public int strongholdColor() {
        if (colorStronghold == null || !colorStronghold.equals(cachedStrongholdSource)) {
            cachedStronghold = parseColor(colorStronghold, 0xFF55FF55);
            cachedStrongholdSource = colorStronghold;
        }
        return cachedStronghold;
    }

    public int adviceColor() {
        if (colorAdvice == null || !colorAdvice.equals(cachedAdviceSource)) {
            cachedAdvice = parseColor(colorAdvice, 0xFF4FD8FF);
            cachedAdviceSource = colorAdvice;
        }
        return cachedAdvice;
    }

    public int archivedColor() {
        if (colorArchived == null || !colorArchived.equals(cachedArchivedSource)) {
            cachedArchived = parseColor(colorArchived, 0xFFB0B0B0);
            cachedArchivedSource = colorArchived;
        }
        return cachedArchived;
    }

    public double scaledRadius() {
        return 11.0 * clamp(markerScale, 0.4, 3.0);
    }

    public double scaledThickness() {
        return Math.max(1.0, markerThickness * clamp(markerScale, 0.4, 3.0));
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** 支持 "#RRGGBB"、"RRGGBB"、"0xRRGGBB"；带 alpha 时保留。 */
    public static int parseColor(String input, int fallback) {
        if (input == null) {
            return fallback;
        }
        try {
            String s = input.trim();
            if (s.startsWith("#")) {
                s = s.substring(1);
            } else if (s.startsWith("0x") || s.startsWith("0X")) {
                s = s.substring(2);
            }
            if (s.length() == 6) {
                return 0xFF000000 | Integer.parseInt(s, 16);
            }
            if (s.length() == 8) {
                return (int) Long.parseLong(s, 16);
            }
            return fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
