package com.eyelocator.core;

import java.util.List;

/**
 * 归档去重：同一维度、同一通道内距离过近的条目合并成一条。
 *
 * <p>为什么必须按通道隔离：不同通道代表不同结构（末地要塞、灾变地牢、某某之眼指向的
 * 另一处地牢），坐标即使几乎重合也应各留一条。跨通道合并会直接丢掉一条目标的信息，
 * 所以这里不做。</p>
 *
 * <p>规则放在共享层，两个加载器用同一份实现，也方便单元测试。</p>
 */
public final class ArchiveDedupe {

    /** 归档条目的最小视图，由各加载器的 registry 实现。 */
    public interface Item {

        double x();

        double z();

        double confidence();

        int hits();

        String dimension();

        String channel();

        String name();

        /** 合并后写回字段。 */
        void update(double x, double z, double confidence, int hits, String name);
    }

    private ArchiveDedupe() {
    }

    /**
     * 就地合并 {@code items}，返回被合并掉的条目数。
     *
     * @param threshold 距离阈值（格）；&lt;= 0 时不做任何事
     */
    public static int run(List<? extends Item> items, double threshold) {
        if (threshold <= 0 || items.size() < 2) {
            return 0;
        }

        int removed = 0;
        for (int i = 0; i < items.size(); i++) {
            Item a = items.get(i);
            for (int j = i + 1; j < items.size(); ) {
                Item b = items.get(j);
                if (sameTarget(a, b) && Math.hypot(a.x() - b.x(), a.z() - b.z()) < threshold) {
                    merge(a, b);
                    items.remove(j);
                    removed++;
                } else {
                    j++;
                }
            }
        }
        return removed;
    }

    /** 同一维度、同一通道才算同一个目标。 */
    private static boolean sameTarget(Item a, Item b) {
        return sameText(a.dimension(), b.dimension()) && sameText(channelOf(a), channelOf(b));
    }

    private static boolean sameText(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    /** 旧数据可能没有通道字段，按原版末影之眼处理。 */
    private static String channelOf(Item item) {
        String channel = item.channel();
        return channel == null || channel.isEmpty() ? ChannelManager.VANILLA_EYE : channel;
    }

    /**
     * 把 {@code b} 并进 {@code a}：坐标按置信度加权，置信度取更高的一条，
     * 命中次数累加，名称优先保留已有的。
     */
    private static void merge(Item a, Item b) {
        double w1 = Math.max(0.05, a.confidence());
        double w2 = Math.max(0.05, b.confidence());
        double x = (a.x() * w1 + b.x() * w2) / (w1 + w2);
        double z = (a.z() * w1 + b.z() * w2) / (w1 + w2);
        double confidence = Math.max(a.confidence(), b.confidence());
        int hits = a.hits() + b.hits();
        String name = isEmpty(a.name()) ? b.name() : a.name();
        a.update(x, z, confidence, hits, name);
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }
}