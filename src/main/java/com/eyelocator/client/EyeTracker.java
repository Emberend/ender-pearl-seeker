package com.eyelocator.client;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EyeOfEnderEntity;

/**
 * 追踪客户端上的末影之眼实体：记录它刚出现时的位置，等它飞出一段距离后，
 * 用"起点 -> 当前位置"的水平位移方向作为指向要塞的方位射线。
 *
 * <p>由于末影之眼在客户端是由服务端同步的位置驱动的，我们不需要读取它的内部目标点，
 * 只观察它实际飞行的水平方向即可，兼容单人/联机。</p>
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
        int age;

        Track(double x, double z) {
            this.x = x;
            this.z = z;
        }
    }

    /** 当一个新的末影之眼实体被客户端加载时调用。 */
    public static void begin(Entity entity) {
        TRACKED.put(entity.getId(), new Track(entity.getX(), entity.getZ()));
    }

    public static void clear() {
        TRACKED.clear();
    }

    public static int trackedCount() {
        return TRACKED.size();
    }

    public static void tick(ClientWorld world, StrongholdLocator locator) {
        if (TRACKED.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Integer, Track>> it = TRACKED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Track> entry = it.next();
            Track track = entry.getValue();
            Entity entity = world.getEntityById(entry.getKey());

            if (entity == null || !(entity instanceof EyeOfEnderEntity) || entity.isRemoved()) {
                it.remove();
                continue;
            }

            double dx = entity.getX() - track.x;
            double dz = entity.getZ() - track.z;
            double dist = Math.hypot(dx, dz);

            if (dist >= MIN_MOVE) {
                locator.addRay(new StrongholdLocator.Ray(track.x, track.z, dx / dist, dz / dist));
                it.remove();
            } else if (++track.age > MAX_AGE_TICKS) {
                it.remove();
            }
        }
    }
}
