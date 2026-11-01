package com.eyelocator.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** 归档去重规则：同维度、同通道才合并。 */
class ArchiveDedupeTest {

    private static final class Item implements ArchiveDedupe.Item {
        double x;
        double z;
        double confidence;
        int hits;
        String dimension;
        String channel;
        String name;

        Item(double x, double z, double confidence, String dimension, String channel) {
            this.x = x;
            this.z = z;
            this.confidence = confidence;
            this.hits = 1;
            this.dimension = dimension;
            this.channel = channel;
        }

        @Override
        public double x() {
            return x;
        }

        @Override
        public double z() {
            return z;
        }

        @Override
        public double confidence() {
            return confidence;
        }

        @Override
        public int hits() {
            return hits;
        }

        @Override
        public String dimension() {
            return dimension;
        }

        @Override
        public String channel() {
            return channel;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void update(double x, double z, double confidence, int hits, String name) {
            this.x = x;
            this.z = z;
            this.confidence = confidence;
            this.hits = hits;
            this.name = name;
        }
    }

    private static final String OVERWORLD = "minecraft:overworld";
    private static final String EYE = "minecraft:eye_of_ender";
    private static final String DUNGEON = "cataclysm:eye_of_dungeon";

    @Test
    void sameChannelWithinThresholdIsMerged() {
        List<Item> items = new ArrayList<>(List.of(
                new Item(4816.0, 1680.0, 1.0, OVERWORLD, EYE),
                new Item(4822.0, 1689.0, 0.5, OVERWORLD, EYE)));

        int removed = ArchiveDedupe.run(items, 48.0);

        assertEquals(1, removed);
        assertEquals(1, items.size());
        Item merged = items.get(0);
        // 置信度高的那条权重更大，结果应靠近 4816/1680
        assertEquals(4819.0, merged.x, 2.0);
        assertEquals(1683.0, merged.z, 3.0);
        assertEquals(1.0, merged.confidence, 1e-9);
        assertEquals(2, merged.hits);
    }

    /** 本次问题的复现场景：末地要塞与灾变地牢相距 11 格，但属于不同通道，不能合并。 */
    @Test
    void differentChannelIsNeverMerged() {
        List<Item> items = new ArrayList<>(List.of(
                new Item(4816.0, 1680.0, 1.0, OVERWORLD, EYE),
                new Item(4822.5, 1688.9, 0.67, OVERWORLD, DUNGEON)));

        assertEquals(0, ArchiveDedupe.run(items, 48.0));
        assertEquals(2, items.size());
    }

    @Test
    void differentDimensionIsNeverMerged() {
        List<Item> items = new ArrayList<>(List.of(
                new Item(100.0, 100.0, 1.0, OVERWORLD, EYE),
                new Item(101.0, 101.0, 1.0, "minecraft:the_nether", EYE)));

        assertEquals(0, ArchiveDedupe.run(items, 48.0));
        assertEquals(2, items.size());
    }

    @Test
    void beyondThresholdIsKept() {
        List<Item> items = new ArrayList<>(List.of(
                new Item(0.0, 0.0, 1.0, OVERWORLD, EYE),
                new Item(60.0, 0.0, 1.0, OVERWORLD, EYE)));

        assertEquals(0, ArchiveDedupe.run(items, 48.0));
        assertEquals(2, items.size());
    }

    @Test
    void legacyEntryWithoutChannelCountsAsVanillaEye() {
        // 旧版归档没有 channel 字段，读取时会被规范化成原版末影之眼；
        // 这里直接构造 null 条目，验证去重时也按原版之眼处理。
        List<Item> items = new ArrayList<>(List.of(
                new Item(0.0, 0.0, 1.0, OVERWORLD, null),
                new Item(5.0, 5.0, 1.0, OVERWORLD, EYE)));

        assertEquals(1, ArchiveDedupe.run(items, 48.0));
        assertEquals(1, items.size());
    }

    @Test
    void nameIsKeptWhenMerging() {
        Item a = new Item(0.0, 0.0, 1.0, OVERWORLD, EYE);
        a.name = "家旁边的要塞";
        Item b = new Item(1.0, 1.0, 1.0, OVERWORLD, EYE);
        List<Item> items = new ArrayList<>(List.of(a, b));

        assertEquals(1, ArchiveDedupe.run(items, 48.0));
        assertEquals("家旁边的要塞", items.get(0).name);
    }

    @Test
    void nullNameIsTakenFromTheOtherEntry() {
        Item a = new Item(0.0, 0.0, 1.0, OVERWORLD, EYE);
        Item b = new Item(1.0, 1.0, 1.0, OVERWORLD, EYE);
        b.name = "命名为先";
        List<Item> items = new ArrayList<>(List.of(a, b));

        assertEquals(1, ArchiveDedupe.run(items, 48.0));
        assertEquals("命名为先", items.get(0).name);
    }

    @Test
    void nonPositiveThresholdDoesNothing() {
        List<Item> items = new ArrayList<>(List.of(
                new Item(0.0, 0.0, 1.0, OVERWORLD, EYE),
                new Item(0.0, 0.0, 1.0, OVERWORLD, EYE)));

        assertEquals(0, ArchiveDedupe.run(items, 0));
        assertEquals(2, items.size());
        assertNull(items.get(0).name);
    }
}