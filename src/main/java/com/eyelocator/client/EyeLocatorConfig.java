package com.eyelocator.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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

    /** 下一最佳投掷点的横向基准距离（格）。 */
    public double baseline = 400.0;

    /** 残差超过该值的射线会被当作异常样本剔除（样本次数 >=3 时生效）。 */
    public double outlierResidual = 80.0;
    /** 新射线明显指向另一个要塞时（离当前估计超过该距离），自动归档并重新开始。 */
    public boolean detectStrongholdChange = true;
    public double changeDistance = 512.0;

    /** 是否显示已归档的其它要塞。 */
    public boolean showArchived = true;

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
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(instance), StandardCharsets.UTF_8);
        } catch (Exception e) {
            EyeLocatorClient.LOGGER.warn("保存配置失败: {}", e.getMessage());
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
