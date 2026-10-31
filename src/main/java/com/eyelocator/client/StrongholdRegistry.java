package com.eyelocator.client;

import com.eyelocator.core.ChannelManager;
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
        /** 玩家自定义名称，可为空。 */
        public String name;
        /** 来源通道（实体 id）；旧数据没有这个字段时按原版末影之眼处理。 */
        public String channel;

        Entry() {
        }

        Entry(double x, double z, double confidence, String dimension, String channel) {
            this.x = x;
            this.z = z;
            this.confidence = confidence;
            this.hits = 1;
            this.dimension = dimension;
            this.channel = channel;
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
                        if (e.channel == null || e.channel.isEmpty()) {
                            e.channel = ChannelManager.VANILLA_EYE;
                        }
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

    /** 手动归档一个坐标，可带名称。 */
    public static Entry add(String channel, String name, double x, double z, String dimension) {
        Entry entry = new Entry(x, z, 1.0, dimension, channel);
        entry.name = normalizeName(name);
        ENTRIES.add(entry);
        save();
        return entry;
    }

    public static void rename(Entry entry, String name) {
        entry.name = normalizeName(name);
        save();
    }

    /**
     * 合并同通道、同维度内距离小于 {@code threshold} 的归档，返回被合并掉的条目数。
     * {@code channel} 为 null 时对所有通道生效。
     */
    public static int dedupe(double threshold, String channel) {
        int removed = 0;
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry a = ENTRIES.get(i);
            for (int j = i + 1; j < ENTRIES.size(); ) {
                Entry b = ENTRIES.get(j);
                if (a.dimension.equals(b.dimension)
                        && (channel == null || channel.equals(a.channel))
                        && Math.hypot(a.x - b.x, a.z - b.z) < threshold) {
                    double w1 = Math.max(0.05, a.confidence);
                    double w2 = Math.max(0.05, b.confidence);
                    a.x = (a.x * w1 + b.x * w2) / (w1 + w2);
                    a.z = (a.z * w1 + b.z * w2) / (w1 + w2);
                    a.confidence = Math.max(a.confidence, b.confidence);
                    a.hits += b.hits;
                    if (isEmpty(a.name) && !isEmpty(b.name)) {
                        a.name = b.name;
                    }
                    ENTRIES.remove(j);
                    removed++;
                } else {
                    j++;
                }
            }
        }
        if (removed > 0) {
            save();
        }
        return removed;
    }

    private static String normalizeName(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    public static int size() {
        return ENTRIES.size();
    }

    public static int size(String channel) {
        if (channel == null) {
            return ENTRIES.size();
        }
        int count = 0;
        for (Entry e : ENTRIES) {
            if (channel.equals(e.channel)) {
                count++;
            }
        }
        return count;
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    /** 某通道的归档（返回新列表，避免调用方误改）。 */
    public static List<Entry> entries(String channel) {
        if (channel == null) {
            return ENTRIES;
        }
        List<Entry> filtered = new ArrayList<>();
        for (Entry e : ENTRIES) {
            if (channel.equals(e.channel)) {
                filtered.add(e);
            }
        }
        return filtered;
    }

    /** 更新归档：同通道、同维度内近邻合并，否则新增。 */
    public static void update(String channel, double x, double z, double confidence, String dimension) {
        Entry nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (Entry e : ENTRIES) {
            if (!e.dimension.equals(dimension) || !channel.equals(e.channel)) {
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
            ENTRIES.add(new Entry(x, z, confidence, dimension, channel));
        }
        save();
    }
}
