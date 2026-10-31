package com.eyelocator.client;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** 管理所有定位通道，并记录"最近一次投掷"所属的活跃通道。 */
public final class ChannelManager {

    /** 原版末影之眼通道。 */
    public static final String VANILLA_EYE = "minecraft:eye_of_ender";

    private static final Map<String, LocatorChannel> CHANNELS = new LinkedHashMap<>();
    private static LocatorChannel active;

    private ChannelManager() {
    }

    /** 取得或创建一个通道（显示名会自动推导，或用配置覆盖）。 */
    public static LocatorChannel getOrCreate(String id) {
        return CHANNELS.computeIfAbsent(id, key -> new LocatorChannel(key, displayNameFor(key)));
    }

    public static LocatorChannel byId(String id) {
        return CHANNELS.get(id);
    }

    /** 当前 HUD 正在展示的通道；没有投掷过时回退到原版末影之眼。 */
    public static LocatorChannel active() {
        if (active == null) {
            active = getOrCreate(VANILLA_EYE);
        }
        return active;
    }

    public static void setActive(LocatorChannel channel) {
        if (channel != null) {
            active = channel;
        }
    }

    public static Collection<LocatorChannel> all() {
        return CHANNELS.values();
    }

    public static boolean isEmpty() {
        return CHANNELS.isEmpty();
    }

    /** 清掉所有通道的射线，但保留通道本身。 */
    public static void resetAllRays() {
        for (LocatorChannel channel : CHANNELS.values()) {
            channel.locator.reset();
        }
    }

    public static void clear() {
        CHANNELS.clear();
        active = null;
    }

    private static String displayNameFor(String id) {
        EyeLocatorConfig config = EyeLocatorConfig.get();
        String custom = config.channelNames.get(id);
        if (custom != null && !custom.trim().isEmpty()) {
            return custom.trim();
        }
        if (VANILLA_EYE.equals(id)) {
            return "末地要塞";
        }
        // 没配置时用实体 id 的路径部分，例如 "ancient_eye" -> "Ancient Eye"
        String path = id;
        int idx = id.indexOf(':');
        if (idx >= 0) {
            path = id.substring(idx + 1);
        }
        String[] parts = path.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.length() == 0 ? id : sb.toString();
    }
}