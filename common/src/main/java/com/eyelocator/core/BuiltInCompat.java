package com.eyelocator.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * 已知模组定位道具的映射表。
 *
 * <p>收录标准：<strong>使用时生成一个飞向某个结构的实体</strong>，
 * 即和原版末影之眼一样走 {@code findNearestMapStructure} + {@code signalTo}。
 * 内置条目都是解包对应模组 jar、核对实体注册名与行为后确认的。</p>
 *
 * <p>这张表可以在 {@code config/eyelocator_compat.json} 里整份替换，
 * 不需要改代码，也不需要改本文件里的内置数据：</p>
 * <ul>
 *   <li>文件不存在 → 使用内置默认，并自动生成一份模板，方便复制修改；</li>
 *   <li>文件存在 → <strong>整份替换</strong>内置表（{@code inheritBuiltIn} 为 {@code true} 时改为叠加，同名条目以文件为准）；</li>
 *   <li>文件解析失败 → 保留内置默认，不覆盖用户文件，日志里给出原因。</li>
 * </ul>
 *
 * <p>{@link #IGNORED} 里是名字像定位道具、但实际不是的实体，必须显式排除，
 * 否则会被 id 关键词误判并产生错误射线。</p>
 */
public final class BuiltInCompat {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 兼容表文件名（放在各加载器的 config 目录下）。 */
    public static final String FILE_NAME = "eyelocator_compat.json";

    /** 当前生效的通道表：实体 id -> 显示名/翻译键。 */
    private static Map<String, Entry> entries = defaults();

    /** 当前生效的排除表。 */
    private static Set<String> ignored = defaultIgnored();

    private BuiltInCompat() {
    }

    /** 一个通道的显示信息。两个字段都可省略。 */
    public static final class Entry {
        /** 硬编码兜底显示名（界面优先用 {@link #key}）。 */
        public String name;
        /** 语言文件里的翻译键；留空则退回 {@link #name} 或实体 id。 */
        public String key;

        Entry() {
        }

        Entry(String name, String key) {
            this.name = name;
            this.key = key;
        }
    }

    /** 磁盘上的文件结构。未知字段会被 Gson 忽略。 */
    public static final class File {
        /** 为 {@code true} 时在内置表基础上叠加；默认 {@code false}，整份替换。 */
        public boolean inheritBuiltIn;
        /** 实体 id -> 显示信息。 */
        public Map<String, Entry> channels = new LinkedHashMap<>();
        /** 明确不是定位道具、必须跳过的实体 id。 */
        public Set<String> ignored = new LinkedHashSet<>();
    }

    /**
     * 读取兼容表；文件不存在时写出一份内置默认模板。
     *
     * @param file 配置文件路径
     * @param info 普通日志
     * @param warn 异常日志
     */
    public static void load(Path file, Consumer<String> info, Consumer<String> warn) {
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, GSON.toJson(template()), StandardCharsets.UTF_8);
                entries = defaults();
                ignored = defaultIgnored();
                info.accept("兼容表不存在，已生成模板 " + file + "（当前使用内置默认）");
                return;
            }

            String json = Files.readString(file, StandardCharsets.UTF_8);
            File parsed = GSON.fromJson(json, File.class);
            if (parsed == null) {
                warn.accept("兼容表内容为空，继续使用内置默认：" + file);
                return;
            }

            Map<String, Entry> channels = new LinkedHashMap<>();
            if (parsed.inheritBuiltIn) {
                channels.putAll(defaults());
            }
            if (parsed.channels != null) {
                for (Map.Entry<String, Entry> item : parsed.channels.entrySet()) {
                    String id = trim(item.getKey());
                    if (id == null || item.getValue() == null) {
                        continue;
                    }
                    channels.put(id, item.getValue());
                }
            }

            Set<String> skip = new LinkedHashSet<>();
            if (parsed.inheritBuiltIn) {
                skip.addAll(defaultIgnored());
            }
            if (parsed.ignored != null) {
                for (String id : parsed.ignored) {
                    String trimmed = trim(id);
                    if (trimmed != null) {
                        skip.add(trimmed);
                    }
                }
            }

            entries = Collections.unmodifiableMap(channels);
            ignored = Collections.unmodifiableSet(skip);
            info.accept("已读取兼容表 " + file + "：通道 " + channels.size()
                    + " 个，排除 " + skip.size() + " 个，"
                    + (parsed.inheritBuiltIn ? "叠加内置" : "整份替换内置"));
        } catch (Exception e) {
            // 解析失败时保留当前表（首次加载就是内置默认），不动用户文件
            warn.accept("读取兼容表失败，继续使用内置默认：" + e.getMessage());
        }
    }

    /** 内置显示名（硬编码兜底，界面优先用 {@link #displayKey(String)}）。 */
    public static String displayName(String id) {
        Entry entry = entries.get(id);
        return entry == null ? null : entry.name;
    }

    /** 显示名对应的翻译键；没有配置或留空时返回 {@code null}。 */
    public static String displayKey(String id) {
        Entry entry = entries.get(id);
        return entry == null ? null : entry.key;
    }

    public static boolean isKnown(String id) {
        return entries.containsKey(id);
    }

    /** 明确排除的实体（宁可漏追踪，也不要错误射线）。 */
    public static boolean isIgnored(String id) {
        return ignored.contains(id);
    }

    /** 当前生效的通道表（只读，实体 id -> 显示名）。 */
    public static Map<String, String> all() {
        Map<String, String> names = new LinkedHashMap<>();
        for (Map.Entry<String, Entry> item : entries.entrySet()) {
            names.put(item.getKey(), item.getValue().name);
        }
        return Collections.unmodifiableMap(names);
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 默认模板：内置条目全量写出，用户可以直接复制后替换。 */
    private static File template() {
        File file = new File();
        file.inheritBuiltIn = false;
        file.channels = new LinkedHashMap<>(defaults());
        file.ignored = new LinkedHashSet<>(defaultIgnored());
        return file;
    }

    private static Map<String, Entry> defaults() {
        Map<String, Entry> map = new LinkedHashMap<>();

        // 原版
        map.put("minecraft:eye_of_ender", new Entry("末地要塞", "eyelocator.channel.eye_of_ender"));

        // Goety（诡厄巫法）：CrypticEyeItem / VoidEyeItem -> EyeItemEntity#signalTo
        map.put("goety:cryptic_eye", new Entry("墓穴之眼", "eyelocator.channel.cryptic_eye"));
        map.put("goety:void_eye", new Entry("虚空之眼", "eyelocator.channel.void_eye"));

        // Goety: Awaken（诡厄巫法：觉醒）：*EyeItem -> *EyeEntity#signalTo
        map.put("goetyawaken:ominous_eye", new Entry("不祥之眼", "eyelocator.channel.ominous_eye"));
        map.put("goetyawaken:prison_eye", new Entry("监狱之眼", "eyelocator.channel.prison_eye"));
        map.put("goetyawaken:mirage_eye", new Entry("迷乱之眼", "eyelocator.channel.mirage_eye"));

        // L_Ender's Cataclysm（灾变）：DungeonEyeItem -> Eye_Of_Dungeon_Entity#signalTo
        // 注意：8 种地牢之眼共用这一个实体 id，指向不同的地牢结构，
        // 客户端拿不到是哪种眼睛，只能靠"检测到不同目标就归档重来"来区分。
        map.put("cataclysm:eye_of_dungeon", new Entry("灾变地牢", "eyelocator.channel.eye_of_dungeon"));

        return Collections.unmodifiableMap(map);
    }

    private static Set<String> defaultIgnored() {
        Set<String> skip = new LinkedHashSet<>();
        // 隐形侦察眼（FlyingMob），只用于给玩家提供视野，不会飞向结构
        skip.add("goety:survey_eye");
        // 守望之眼掉在地上的 ItemEntity，不是投射物
        skip.add("goetyawaken:eye_of_overwatch_entity");
        return Collections.unmodifiableSet(skip);
    }
}