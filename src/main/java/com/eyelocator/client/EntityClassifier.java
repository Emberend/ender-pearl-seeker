package com.eyelocator.client;

import com.eyelocator.core.BuiltInCompat;

import java.util.Locale;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EyeOfEnderEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * 判断一个实体是不是"会飞向某个结构"的定位道具。
 *
 * <p>识别优先级：</p>
 * <ol>
 *   <li>配置里的 {@code trackedEntities} 白名单（支持 {@code *} 通配）</li>
 *   <li>原版 {@link EyeOfEnderEntity} 及其子类（大多数模组的眼睛都继承它）</li>
 *   <li>自动启发式：实体 id 含 eye / seeker / locator 等关键词</li>
 * </ol>
 *
 * <p>返回通道 id（就是实体类型 id），不是定位道具则返回 {@code null}。</p>
 */
public final class EntityClassifier {

    /** 学习模式持续时间。 */
    private static final long LEARN_WINDOW_MS = 15_000L;

    private static long learnUntil = 0L;

    private EntityClassifier() {
    }

    public static String classify(Entity entity) {
        Identifier identifier = Registries.ENTITY_TYPE.getId(entity.getType());
        if (identifier == null) {
            return null;
        }
        String id = identifier.toString();
        EyeLocatorConfig config = EyeLocatorConfig.get();

        // 1. 显式白名单
        for (String pattern : config.trackedEntities) {
            if (pattern != null && matches(pattern, id)) {
                return id;
            }
        }

        // 2. 内置兼容表（含原版末影之眼与已知模组的同类道具）
        if (BuiltInCompat.isKnown(id) || entity instanceof EyeOfEnderEntity) {
            return id;
        }

        // 3. 自动启发式（只对非生物实体生效，避免误追踪玩家/生物）
        if (config.autoDetect && !(entity instanceof LivingEntity)) {
            String path = identifier.getPath().toLowerCase(Locale.ROOT);
            for (String keyword : config.autoDetectKeywords) {
                if (keyword != null && !keyword.isEmpty()
                        && path.contains(keyword.toLowerCase(Locale.ROOT))) {
                    return id;
                }
            }
        }
        return null;
    }

    // ---- 手动学习模式 ----

    /** 开始学习：接下来新生成的第一个投射物会被加入白名单。 */
    public static void requestLearning() {
        learnUntil = System.currentTimeMillis() + LEARN_WINDOW_MS;
    }

    public static boolean isLearning() {
        return System.currentTimeMillis() < learnUntil;
    }

    public static void stopLearning() {
        learnUntil = 0L;
    }

    /**
     * 学习模式下判断该实体是否值得记录。
     * 只接受非生物实体（投射物、自定义飞行物），并且要离玩家足够近。
     */
    public static boolean isLearnCandidate(Entity entity, Entity player) {
        if (!isLearning() || entity instanceof LivingEntity) {
            return false;
        }
        if (player == null) {
            return false;
        }
        return entity.squaredDistanceTo(player) < 64.0 * 64.0;
    }

    /** 通配符匹配：{@code modid:*}、{@code *eye*}、完整 id。 */
    private static boolean matches(String pattern, String id) {
        if (pattern.equals(id)) {
            return true;
        }
        if (pattern.indexOf('*') < 0) {
            return false;
        }
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else {
                regex.append(java.util.regex.Pattern.quote(String.valueOf(c)));
            }
        }
        return id.matches(regex.toString());
    }
}