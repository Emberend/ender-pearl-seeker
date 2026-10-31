package com.eyelocator.forge;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.eyelocator.core.ChannelManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraftforge.fml.loading.FMLPaths;

/**
 * 与 Fabric 版共用同一个配置文件 {@code config/eyelocator.json}，字段完全一致，
 * 方便在两个加载器之间迁移设置。
 */
public final class ForgeConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("eyelocator.json");

    private static ForgeConfig instance = new ForgeConfig();

    public boolean enabled = true;

    /** 路标样式：diamond / circle / cross */
    public String markerStyle = "diamond";
    public double markerScale = 1.0;
    public double markerThickness = 3.0;

    public String colorStronghold = "#55FF55";
    public String colorAdvice = "#4FD8FF";
    public String colorArchived = "#B0B0B0";

    public boolean showDistance = true;
    public boolean showLabels = true;
    public boolean showPanel = true;

    public double baseline = 400.0;

    public double outlierResidual = 80.0;
    public boolean detectStrongholdChange = true;
    public double changeDistance = 512.0;

    public boolean showArchived = true;
    public boolean hideArchivedWhileSearching = true;
    public double dedupeDistance = 48.0;

    public boolean autoDetect = true;
    public List<String> autoDetectKeywords = new ArrayList<>(Arrays.asList(
            "eye", "seeker", "locator", "finder", "stronghold"));
    public List<String> trackedEntities = new ArrayList<>();
    public Map<String, String> channelNames = new LinkedHashMap<>();

    private ForgeConfig() {
    }

    public static ForgeConfig get() {
        return instance;
    }

    public static void load() {
        try {
            if (Files.exists(PATH)) {
                String json = Files.readString(PATH, StandardCharsets.UTF_8);
                ForgeConfig loaded = GSON.fromJson(json, ForgeConfig.class);
                if (loaded != null) {
                    instance = loaded;
                }
            } else {
                save();
            }
        } catch (Exception e) {
            EyeLocatorForge.LOGGER.warn("读取配置失败，使用默认值: {}", e.getMessage());
        }
        instance.normalize();
        applyToCommon(instance);
    }

    private static void applyToCommon(ForgeConfig cfg) {
        ChannelManager.setNameOverrides(cfg.channelNames);
        ChannelManager.setOutlierResidual(cfg.outlierResidual);
    }

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
            EyeLocatorForge.LOGGER.warn("保存配置失败: {}", e.getMessage());
        }
    }

    public int strongholdColor() {
        return parseColor(colorStronghold, 0xFF55FF55);
    }

    public int adviceColor() {
        return parseColor(colorAdvice, 0xFF4FD8FF);
    }

    public int archivedColor() {
        return parseColor(colorArchived, 0xFFB0B0B0);
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