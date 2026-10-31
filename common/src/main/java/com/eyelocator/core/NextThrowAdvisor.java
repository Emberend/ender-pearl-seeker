package com.eyelocator.core;

/**
 * 第一次投掷之后，给玩家一个"最佳下一投掷点"：
 *
 * <p>只拿到一条方位射线时，要塞在射线上某个未知位置。第二次投掷如果还站在这条线附近，
 * 两条线几乎平行，交点会非常不稳定（甚至解不出来）。因此最佳策略是<strong>横向错开</strong>一段基准距离
 * {@link #BASELINE}，让两条方位线的夹角足够大，交点唯一且稳定。</p>
 *
 * <p>错开方向不是纯垂直，而是再朝要塞方向偏转 {@link #FORWARD_BIAS}（约 30°）：
 * 纯垂直移动有时会把玩家带进相邻要塞的范围，第二次投掷就会指向别的要塞；
 * 斜着朝目标走，横向偏移仍有约 346 格（角度分辨率几乎不变），同时离目标更近、
 * 离要塞边界更远，跨到别人地盘的概率更低。</p>
 *
 * <p>如果上一次两条方位互相矛盾（{@link StrongholdLocator#inconsistent()}），
 * 说明它们已经指向不同要塞，这时走 400 格也解不出正确结果，改为让玩家小幅侧移后补投一次，
 * 由稳健拟合自动剔除矛盾的那条。</p>
 */
public final class NextThrowAdvisor {

    /**
     * 推荐的横向基准距离（格）。越大两条方位线夹角越大、交点越稳，但走路越远。
     * 400 格时，即使要塞在 1500 格外，夹角也有约 13°，足以稳定求交。
     */
    public static final double BASELINE = 400.0;

    /** 推荐点相对"纯垂直方向"朝要塞偏转的角度。 */
    private static final double FORWARD_BIAS = Math.toRadians(30.0);

    /** 方位矛盾时建议的侧移距离：只要新方位和前一次不重合即可。 */
    private static final double RETHROW_LATERAL = 80.0;

    /** 方位矛盾时建议的向目标方向偏移，让新位置稍微离开原射线。 */
    private static final double RETHROW_FORWARD = 20.0;

    private NextThrowAdvisor() {
    }

    /** 推荐结果的性质，决定 HUD 上的说法。 */
    public enum Kind {
        /** 走到标记处再扔。 */
        MOVE,
        /** 当前站位已经够好，原地再扔。 */
        THROW_HERE,
        /** 上次两条方位矛盾，小幅侧移后补投，让第三次方位纠正。 */
        RETHROW,
        /** 两条方位几乎平行，交点不可靠，需要横向多走一些。 */
        MOVE_WIDER
    }

    public static final class Advice {
        /** 推荐站立点（水平）。 */
        public final double x;
        public final double z;
        /** 玩家当前位置到推荐点的直线距离。 */
        public final double travel;
        public final Kind kind;

        Advice(double x, double z, double travel, Kind kind) {
            this.x = x;
            this.z = z;
            this.travel = travel;
            this.kind = kind;
        }
    }

    /**
     * @param px 玩家水平坐标（可用插值坐标以获得更平滑的显示）
     * @param pz 玩家水平坐标
     * @param baseline 横向基准距离（来自配置）
     * @return 推荐的下一个投掷点；若已能定位、或还没有任何方位射线，返回 null。
     */
    public static Advice advise(double px, double pz, StrongholdLocator locator, double baseline) {
        // 已经有可信交点（≥2 条射线）时不再需要建议
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
        double sign = side >= 0.0 ? 1.0 : -1.0;

        // 上次方位互相矛盾：走 400 格没有意义，小幅侧移后补投，让第三次方位来纠正
        if (locator.inconsistent()) {
            if (Math.abs(side) >= RETHROW_LATERAL) {
                return new Advice(px, pz, 0.0, Kind.RETHROW);
            }
            double rx = fx + nx * RETHROW_LATERAL * sign + ray.dx * RETHROW_FORWARD;
            double rz = fz + nz * RETHROW_LATERAL * sign + ray.dz * RETHROW_FORWARD;
            return new Advice(rx, rz, Math.hypot(rx - px, rz - pz), Kind.RETHROW);
        }

        double lateral = baseline * Math.cos(FORWARD_BIAS);
        double forward = baseline * Math.sin(FORWARD_BIAS);

        // 已经横向错开得足够远，原地再扔一次即可，不必再走
        if (Math.abs(side) >= lateral) {
            return new Advice(px, pz, 0.0, Kind.THROW_HERE);
        }

        // 推荐点 = 垂足 + 法线方向 * lateral + 射线方向 * forward（保持在玩家所在一侧）
        double rx = fx + nx * lateral * sign + ray.dx * forward;
        double rz = fz + nz * lateral * sign + ray.dz * forward;

        double travel = Math.hypot(rx - px, rz - pz);
        return new Advice(rx, rz, travel, locator.unstable() ? Kind.MOVE_WIDER : Kind.MOVE);
    }
}