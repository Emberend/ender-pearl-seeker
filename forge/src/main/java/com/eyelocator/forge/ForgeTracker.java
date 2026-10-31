package com.eyelocator.forge;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.eyelocator.core.StrongholdLocator;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * 追踪客户端上的"定位投射物"（原版末影之眼，以及其它模组里同样会飞向结构的道具）。
 *
 * <p>只观察实体实际的水平位移方向，不读取它内部的目标点，因此单人/联机都适用，
 * 也不受地形/结构模组影响。</p>
 */
public final class ForgeTracker {

    private static final double MIN_MOVE = 4.0;
    private static final int MAX_AGE_TICKS = 120;

    private static final Map<Integer, Track> TRACKED = new ConcurrentHashMap<>();

    private ForgeTracker() {
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

    public static void begin(Entity entity, String channel) {
        TRACKED.put(entity.getId(),
                new Track(entity.getX(), entity.getZ(), entity.getType(), channel));
    }

    /** 已追踪的实体 id，用于避免重复 begin。 */
    public static boolean isTracked(Entity entity) {
        return TRACKED.containsKey(entity.getId());
    }

    public static void clear() {
        TRACKED.clear();
    }

    public static void tick(ClientLevel level) {
        if (TRACKED.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Integer, Track>> it = TRACKED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Track> entry = it.next();
            Track track = entry.getValue();
            Entity entity = level.getEntity(entry.getKey());

            // 实体消失，或 id 被别的实体复用（类型对不上）
            if (entity == null || entity.isRemoved() || entity.getType() != track.type) {
                it.remove();
                continue;
            }

            double dx = entity.getX() - track.x;
            double dz = entity.getZ() - track.z;
            double dist = Math.hypot(dx, dz);

            if (dist >= MIN_MOVE) {
                EyeLocatorForge.onRayCompleted(track.channel,
                        new StrongholdLocator.Ray(track.x, track.z, dx / dist, dz / dist));
                it.remove();
            } else if (++track.age > MAX_AGE_TICKS) {
                it.remove();
            }
        }
    }
}