package com.eyelocator.client;

import java.util.ArrayList;
import java.util.List;

/**
 * 已定位的要塞归档。多个估计点相距较近时会合并（加权平均），相距较远则视为不同要塞。
 * 用于"检测到最近要塞变了"后保留上一个结果，以及在一个存档里管理多个要塞。
 */
public final class StrongholdRegistry {

    public static final class Entry {
        public double x;
        public double z;
        public double confidence;
        public int hits;
        public final String dimension;

        Entry(double x, double z, double confidence, String dimension) {
            this.x = x;
            this.z = z;
            this.confidence = confidence;
            this.hits = 1;
            this.dimension = dimension;
        }
    }

    private static final double MERGE_DISTANCE = 128.0;
    private static final List<Entry> ENTRIES = new ArrayList<>();

    private StrongholdRegistry() {
    }

    public static void clear() {
        ENTRIES.clear();
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
    }
}
