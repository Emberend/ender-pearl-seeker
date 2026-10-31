package com.eyelocator.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 已知模组定位道具的内置映射（实体 id -> 默认通道显示名）。
 *
 * <p>这些实体都是"使用后生成一个飞向某个结构的投射物"，和原版末影之眼行为一致，
 * 因此不需要用户做任何配置。配置里的 {@code channelNames} 仍可覆盖这里的名字。</p>
 *
 * <p>不认识的模组仍可通过自动关键词或 {@code /eyelocator track next} 识别。</p>
 */
public final class BuiltInCompat {

    private static final Map<String, String> NAMES;

    static {
        Map<String, String> map = new LinkedHashMap<>();

        // 原版
        map.put("minecraft:eye_of_ender", "末地要塞");

        // Goety（诡厄巫法）
        map.put("goety:cryptic_eye", "隐秘之眼");
        map.put("goety:void_eye", "虚空之眼");
        map.put("goety:survey_eye", "探知之眼");

        // Goety: Awaken（诡厄巫法：觉醒）
        // 这些道具用 findNearestMapStructure + signalTo 实现，指向各自的模组结构
        map.put("goetyawaken:ominous_eye", "不祥之眼");
        map.put("goetyawaken:prison_eye", "囚禁之眼");
        map.put("goetyawaken:mirage_eye", "幻影之眼");
        map.put("goetyawaken:eye_of_overwatch_entity", "守望之眼");

        NAMES = Collections.unmodifiableMap(map);
    }

    private BuiltInCompat() {
    }

    /** 内置显示名；不是已知道具则返回 {@code null}。 */
    public static String displayName(String id) {
        return NAMES.get(id);
    }

    public static boolean isKnown(String id) {
        return NAMES.containsKey(id);
    }

    public static Map<String, String> all() {
        return NAMES;
    }
}