package com.eyelocator.client;

import net.minecraft.client.network.ClientPlayerEntity;

/**
 * 第一次投掷之后，给玩家一个"最佳下一投掷点"：
 *
 * <p>只拿到一条方位射线时，要塞在射线上某个未知位置。第二次投掷如果还站在这条线附近，
 * 两条线几乎平行，交点会非常不稳定（甚至解不出来）。因此最佳策略是<strong>垂直于这条方位线</strong>
 * 横向错开一段基准距离 {@link #BASELINE}，让两条方位线的夹角足够大，交点唯一且稳定。</p>
 *
 * <p>推荐点取"到第一条方位线垂直距离恰为 BASELINE、且离玩家最近"的那个点，
 * 这样既能保证几何条件，又尽量少走路。</p>
 */
public final class NextThrowAdvisor {

    /**
     * 推荐的横向基准距离（格）。越大两条方位线夹角越大、交点越稳，但走路越远。
     * 400 格时，即使要塞在 2000 格外，夹角也有约 11°，足以稳定求交。
     */
    public static final double BASELINE = 400.0;

    private NextThrowAdvisor() {
    }

    public static final class Advice {
        /** 推荐站立点（水平）。 */
        public final double x;
        public final double z;
        /** 玩家当前位置到推荐点的直线距离。 */
        public final double travel;
        /** 当前站位已经与首次方位错开足够远，直接原地再扔即可。 */
        public final boolean alreadyOptimal;

        Advice(double x, double z, double travel, boolean alreadyOptimal) {
            this.x = x;
            this.z = z;
            this.travel = travel;
            this.alreadyOptimal = alreadyOptimal;
        }
    }

    /**
     * @return 推荐的下一个投掷点；若已能定位、或还没有任何方位射线，返回 null。
     */
    public static Advice advise(ClientPlayerEntity player, StrongholdLocator locator) {
        return advise(player.getX(), player.getZ(), locator);
    }

    /**
     * @param px 玩家水平坐标（可用插值坐标以获得更平滑的显示）
     * @param pz 玩家水平坐标
     * @return 推荐的下一个投掷点；若已能定位、或还没有任何方位射线，返回 null。
     */
    public static Advice advise(double px, double pz, StrongholdLocator locator) {
        // 已经有交点（≥2 条射线）时不再需要建议
        if (locator.hasEstimate()) {
            return null;
        }
        StrongholdLocator.Ray ray = locator.lastRay();
        if (ray == null) {
            return null;
        }

        // 方位线的单位法线
        double nx = -ray.dz;
        double nz = ray.dx;

        // 把玩家投影到方位线上，得到垂足 F
        double t = (px - ray.x) * ray.dx + (pz - ray.z) * ray.dz;
        double fx = ray.x + t * ray.dx;
        double fz = ray.z + t * ray.dz;

        // 玩家当前在这条线的哪一侧（有符号垂距）
        double side = (px - ray.x) * nx + (pz - ray.z) * nz;

        // 已经横向错开得足够远，原地再扔一次即可，不必再走
        if (Math.abs(side) >= BASELINE) {
            return new Advice(px, pz, 0.0, true);
        }

        double sign = side >= 0.0 ? 1.0 : -1.0;

        // 推荐点 = 垂足 + 法线方向 * BASELINE（保持在玩家所在一侧，路程更短）
        double rx = fx + nx * BASELINE * sign;
        double rz = fz + nz * BASELINE * sign;

        double travel = Math.hypot(rx - px, rz - pz);
        return new Advice(rx, rz, travel, false);
    }
}
