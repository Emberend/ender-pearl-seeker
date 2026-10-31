package com.eyelocator.client;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;

/**
 * 追踪客户端上的"定位投射物"（原版末影之眼，以及其它模组里同样会飞向结构的道具）。
 *
 * <p>记录它刚出现时的位置，等它飞出一段距离后，用"起点 -> 当前位置"的水平位移方向
 * 作为指向目标的方位射线。由于只观察实际飞行方向，不需要读取实体内部的目标点，
 * 单人/联机都适用，也不受地形/结构 Mod 影响。</p>
 */
public final class EyeTracker {

    private static final double MIN_MOVE = 4.0;
    private static final int MAX_AGE_TICKS = 120;

    private static final Map<Integer, Track> TRACKED = new ConcurrentHashMap<>();

    private EyeTracker() {
    }

    private static final class Track {
        final double x;
        final double z;
        final EntityType<?> type;
        final String channel;
        int age;

        Track(double x, double z, EntityType<?> type, String channel) {
            this.x = x;
            this.z = z;
            this.type = type;
            this.channel = channel;
        }
    }

    /** 当一个新的定位投射物被客户端加载时调用。 */
    public static void begin(Entity entity, String channel) {
        TRACKED.put(entity.getId(),
                new Track(entity.getX(), entity.getZ(), entity.getType(), channel));
    }

    public static void clear() {
        TRACKED.clear();
    }

    public static int trackedCount() {
        return TRACKED.size();
    }

    public static void tick(ClientWorld world) {
        if (TRACKED.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Integer, Track>> it = TRACKED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Track> entry = it.next();
            Track track = entry.getValue();
            Entity entity = world.getEntityById(entry.getKey());

            // 实体消失，或 id 被别的实体复用（类型对不上）
            if (entity == null || entity.isRemoved() || entity.getType() != track.type) {
                it.remove();
                continue;
            }

            double dx = entity.getX() - track.x;
            double dz = entity.getZ() - track.z;
            double dist = Math.hypot(dx, dz);

            if (dist >= MIN_MOVE) {
                EyeLocatorClient.onRayCompleted(track.channel,
                        new StrongholdLocator.Ray(track.x, track.z, dx / dist, dz / dist));
                it.remove();
            } else if (++track.age > MAX_AGE_TICKS) {
                it.remove();
            }
        }
    }
}