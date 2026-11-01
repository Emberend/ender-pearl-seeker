package com.eyelocator.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 已知模组定位道具的内置映射。
 *
 * <p>收录标准：<strong>使用时生成一个飞向某个结构的实体</strong>，
 * 即和原版末影之眼一样走 {@code findNearestMapStructure} + {@code signalTo}。
 * 每一个都是解包对应模组 jar、核对实体注册名与行为后确认的。</p>
 *
 * <p>下面 {@link #IGNORED} 里是名字像定位道具、但实际不是的实体，必须显式排除，
 * 否则会被 id 关键词误判并产生错误射线。</p>
 */
public final class BuiltInCompat {

    /** 已确认的定位道具：实体 id -> 默认通道显示名（仅作兜底，界面用翻译键）。 */
    private static final Map<String, String> NAMES;

    /** 已确认的定位道具：实体 id -> 语言文件里的翻译键。 */
    private static final Map<String, String> KEYS;

    /**
     * 已确认<strong>不是</strong>定位道具的实体 id。
     *
     * <ul>
     *   <li>{@code goety:survey_eye} —— 隐形侦察眼（{@code FlyingMob}），
     *       只用于给玩家提供视野，不会飞向结构</li>
     *   <li>{@code goetyawaken:eye_of_overwatch_entity} —— 守望之眼掉在地上的
     *       {@code ItemEntity}，不是投射物</li>
     * </ul>
     */
    private static final Set<String> IGNORED;

    static {
        Map<String, String> names = new LinkedHashMap<>();

        // 原版
        names.put("minecraft:eye_of_ender", "末地要塞");

        // Goety（诡厄巫法）：CrypticEyeItem / VoidEyeItem -> EyeItemEntity#signalTo
        names.put("goety:cryptic_eye", "墓穴之眼");
        names.put("goety:void_eye", "虚空之眼");

        // Goety: Awaken（诡厄巫法：觉醒）：*EyeItem -> *EyeEntity#signalTo
        names.put("goetyawaken:ominous_eye", "不祥之眼");
        names.put("goetyawaken:prison_eye", "监狱之眼");
        names.put("goetyawaken:mirage_eye", "迷乱之眼");

        // L_Ender's Cataclysm（灾变）：DungeonEyeItem -> Eye_Of_Dungeon_Entity#signalTo
        // 注意：8 种地牢之眼共用这一个实体 id，指向不同的地牢结构，
        // 客户端拿不到是哪种眼睛，只能靠"检测到不同目标就归档重来"来区分。
        names.put("cataclysm:eye_of_dungeon", "灾变地牢");

        NAMES = Collections.unmodifiableMap(names);

        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("minecraft:eye_of_ender", "eyelocator.channel.eye_of_ender");
        keys.put("goety:cryptic_eye", "eyelocator.channel.cryptic_eye");
        keys.put("goety:void_eye", "eyelocator.channel.void_eye");
        keys.put("goetyawaken:ominous_eye", "eyelocator.channel.ominous_eye");
        keys.put("goetyawaken:prison_eye", "eyelocator.channel.prison_eye");
        keys.put("goetyawaken:mirage_eye", "eyelocator.channel.mirage_eye");
        keys.put("cataclysm:eye_of_dungeon", "eyelocator.channel.eye_of_dungeon");
        KEYS = Collections.unmodifiableMap(keys);

        Set<String> ignored = new HashSet<>();
        ignored.add("goety:survey_eye");
        ignored.add("goetyawaken:eye_of_overwatch_entity");
        IGNORED = Collections.unmodifiableSet(ignored);
    }

    private BuiltInCompat() {
    }

    /** 内置显示名（硬编码兜底，界面优先用 {@link #displayKey(String)}）。 */
    public static String displayName(String id) {
        return NAMES.get(id);
    }

    /** 内置显示名对应的翻译键；不是已知道具则返回 {@code null}。 */
    public static String displayKey(String id) {
        return KEYS.get(id);
    }

    public static boolean isKnown(String id) {
        return NAMES.containsKey(id);
    }

    /** 明确排除的实体（宁可漏追踪，也不要错误射线）。 */
    public static boolean isIgnored(String id) {
        return IGNORED.contains(id);
    }

    public static Map<String, String> all() {
        return NAMES;
    }
}