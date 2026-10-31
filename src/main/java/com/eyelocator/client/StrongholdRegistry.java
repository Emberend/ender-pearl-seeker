package com.eyelocator.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * 已定位的要塞归档，持久化到 {@code config/eyelocator_strongholds.json}。
 * 同维度内相距较近的估计会合并，相距较远视为不同要塞。
 */
public final class StrongholdRegistry {

    public static final class Entry {
        public double x;
        public double z;
        public double confidence;
        public int hits;
        public String dimension;

        Entry() {
        }

        Entry(double x, double z, double confidence, String dimension) {
            this.x = x;
            this.z = z;
            this.confidence = confidence;
            this.hits = 1;
            this.dimension = dimension;
        }
    }

    private static final class Data {
        List<Entry> entries = new ArrayList<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH =
            FabricLoader.getInstance().getConfigDir().resolve("eyelocator_strongholds.json");
    private static final double MERGE_DISTANCE = 128.0;
    private static final List<Entry> ENTRIES = new ArrayList<>();

    private StrongholdRegistry() {
    }

    public static void load() {
        try {
            if (!Files.exists(PATH)) {
                return;
            }
            Data data = GSON.fromJson(Files.readString(PATH, StandardCharsets.UTF_8), Data.class);
            ENTRIES.clear();
            if (data != null && data.entries != null) {
                for (Entry e : data.entries) {
                    if (e != null && e.dimension != null) {
                        ENTRIES.add(e);
                    }
                }
            }
        } catch (Exception e) {
            EyeLocatorClient.LOGGER.warn("读取要塞归档失败: {}", e.getMessage());
        }
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Data data = new Data();
            data.entries = ENTRIES;
            Files.writeString(PATH, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (Exception e) {
            EyeLocatorClient.LOGGER.warn("保存要塞归档失败: {}", e.getMessage());
        }
    }

    public static void clear() {
        ENTRIES.clear();
        save();
    }

    public static boolean remove(Entry entry) {
        boolean removed = ENTRIES.remove(entry);
        if (removed) {
            save();
        }
        return removed;
    }

    public static int size() {
        return ENTRIES.size();
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    /** 更新归档：同维度内近邻合并、否则新增。 */
    public static void update(double x, double z, double confidence, String dimension) {
        Entry nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (Entry e : ENTRIES) {
            if (!e.dimension.equals(dimension)) {
                continue;
            }
            double d = Math.hypot(e.x - x, e.z - z);
            if (d < nearestDist) {
                nearestDist = d;
                nearest = e;
            }
        }
        if (nearest != null && nearestDist < MERGE_DISTANCE) {
            double w1 = Math.max(0.05, nearest.confidence);
            double w2 = Math.max(0.05, confidence);
            nearest.x = (nearest.x * w1 + x * w2) / (w1 + w2);
            nearest.z = (nearest.z * w1 + z * w2) / (w1 + w2);
            nearest.confidence = Math.max(nearest.confidence, confidence);
            nearest.hits++;
        } else {
            ENTRIES.add(new Entry(x, z, confidence, dimension));
        }
        save();
    }
}
