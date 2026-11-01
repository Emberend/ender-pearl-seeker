package com.eyelocator.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 末地要塞定位核心：记录若干条"方位射线"，用"射线对求交 + 投票"的方式找共同交点。
 *
 * <p>只接受<strong>正向交点</strong>：交点必须落在两条射线的前方。这样当两次投掷其实指向了
 * 不同的要塞时，最小二乘不会再拿反向延长线的交点冒充答案，而是判定为矛盾并让玩家补投。
 * 同一个交点被越多条方位支持越可信；若出现两组支持数相同、相距又很远的候选，
 * 宁可判定为"无法确定"也不在两座要塞之间瞎猜。</p>
 */
public final class StrongholdLocator {

    /**
     * 一条由一次末影之眼投掷得到的方位射线（水平面）。
     *
     * <p>字段不是 final：持久化时 Gson 需要能直接写入。语义上依然当作不可变对象使用。</p>
     */
    public static final class Ray {
        public double x;
        public double z;
        public double dx;
        public double dz;

        /** Gson 反序列化用。 */
        public Ray() {
        }

        public Ray(double x, double z, double dx, double dz) {
            this.x = x;
            this.z = z;
            this.dx = dx;
            this.dz = dz;
        }
    }

    /** 离群射线剔除阈值（水平垂距，单位格）。由配置注入。 */
    private double outlierResidual = 80.0;

    /** 判定交点在射线"前方"的容差（格），留一点数值余量。 */
    private static final double BEHIND_TOLERANCE = 1.0;

    /**
     * 两条射线夹角的下限（弧度）。
     *
     * <p>夹角太小时交点对方向误差极其敏感，结果不可信。这里刻意<strong>不</strong>用"交点距离上限"
     * 来判断——模组可能改变结构间距、生成范围甚至探测半径，距离本身说明不了对错；
     * 夹角则只取决于玩家自己的两次投掷位置，与目标多远无关。</p>
     */
    private static final double MIN_CROSS_ANGLE = Math.toRadians(1.5);

    private final List<Ray> rays = new ArrayList<>();
    private final List<Ray> usedRays = new ArrayList<>();

    private boolean hasEstimate;
    private double estX;
    private double estZ;
    private double residual;
    private int excluded;
    /** 有至少两条方位互相矛盾（交点在射线背后），说明它们大概指向了不同的目标。 */
    private boolean inconsistent;
    /** 有至少两条方位几乎平行，交点被推到极远处，结果不可用。 */
    private boolean unstable;

    /** 设置离群剔除阈值，并立即重算（配置变更后调用）。 */
    public void setOutlierResidual(double outlierResidual) {
        if (outlierResidual <= 0) {
            return;
        }
        this.outlierResidual = outlierResidual;
        recompute();
    }

    public double outlierResidual() {
        return outlierResidual;
    }

    public void reset() {
        rays.clear();
        usedRays.clear();
        hasEstimate = false;
        estX = 0;
        estZ = 0;
        residual = 0;
        excluded = 0;
        inconsistent = false;
        unstable = false;
    }

    public int sampleCount() {
        return rays.size();
    }

    /** 实际参与拟合的样本数（剔除离群后）。 */
    public int usedCount() {
        return usedRays.size();
    }

    /** 被判定为离群并剔除的样本数。 */
    public int excluded() {
        return excluded;
    }

    /**
     * 已有方位但拟合不出可信交点，且原因是"交点在射线背后"——
     * 通常意味着两次投掷指向了不同的要塞（或其中一次数据异常）。
     */
    public boolean inconsistent() {
        return inconsistent;
    }

    /** 已有方位但交点不可信，原因是两条方位几乎平行（或重合），交点被推到极远处。 */
    public boolean unstable() {
        return unstable;
    }

    public Ray lastRay() {
        return rays.isEmpty() ? null : rays.get(rays.size() - 1);
    }

    /** 当前所有方位射线的快照（用于持久化，外部修改不会影响内部状态）。 */
    public List<Ray> rays() {
        return new ArrayList<>(rays);
    }

    /** 直接回填一批射线（从磁盘恢复时使用），只重算一次。 */
    public void restoreRays(List<Ray> saved) {
        rays.clear();
        if (saved != null) {
            for (Ray ray : saved) {
                if (ray != null && Double.isFinite(ray.x) && Double.isFinite(ray.z)
                        && Double.isFinite(ray.dx) && Double.isFinite(ray.dz)) {
                    rays.add(ray);
                }
            }
        }
        recompute();
    }

    public boolean hasEstimate() {
        return hasEstimate;
    }

    public double estX() {
        return estX;
    }

    public double estZ() {
        return estZ;
    }

    public double residual() {
        return residual;
    }

    /** 射线到估计交点的垂直距离。 */
    public double perpDistanceToEstimate(Ray ray) {
        return perpDistance(estX, estZ, ray);
    }

    public static double perpDistance(double x, double z, Ray ray) {
        return Math.abs(-ray.dz * (x - ray.x) + ray.dx * (z - ray.z));
    }

    /** 点 (x,z) 在射线方向上的投影距离：>0 表示在射线前方，<0 表示落在反向延长线上。 */
    public static double forwardDistance(double x, double z, Ray ray) {
        return (x - ray.x) * ray.dx + (z - ray.z) * ray.dz;
    }

    /** 方位射线的角度分散度（弧度，0..π/2）。 */
    public double angleSpread() {
        if (usedRays.size() < 2) {
            return 0;
        }
        double minDir = Double.POSITIVE_INFINITY;
        double maxDir = Double.NEGATIVE_INFINITY;
        for (Ray r : usedRays) {
            double a = Math.atan2(r.dz, r.dx);
            if (a < 0) {
                a += Math.PI;
            }
            if (a >= Math.PI) {
                a -= Math.PI;
            }
            minDir = Math.min(minDir, a);
            maxDir = Math.max(maxDir, a);
        }
        return maxDir - minDir;
    }

    /** 综合可信度 0..1。 */
    public double confidence() {
        if (!hasEstimate) {
            return 0;
        }
        double spread = angleSpread();
        double spreadFactor = Math.min(1.0, spread / (Math.PI / 6.0));
        double residualFactor = 1.0 / (1.0 + residual / 40.0);
        double sampleFactor = Math.min(1.0, usedRays.size() / 3.0);
        return Math.max(0.0, Math.min(1.0, spreadFactor * residualFactor * sampleFactor));
    }

    public void addRay(Ray ray) {
        for (Ray r : rays) {
            double sameDir = Math.abs(r.dx * ray.dx + r.dz * ray.dz);
            double originDist = Math.hypot(r.x - ray.x, r.z - ray.z);
            if (sameDir > 0.9999 && originDist < 2.0) {
                return;
            }
        }
        rays.add(ray);
        recompute();
    }

    private static final class Fit {
        boolean ok;
        double x;
        double z;
    }

    private Fit fit(List<Ray> list) {
        Fit fit = new Fit();
        if (list.size() < 2) {
            return fit;
        }
        double sxx = 0, sxy = 0, syy = 0, bx = 0, by = 0;
        for (Ray r : list) {
            double nx = -r.dz;
            double ny = r.dx;
            double b = nx * r.x + ny * r.z;
            sxx += nx * nx;
            sxy += nx * ny;
            syy += ny * ny;
            bx += nx * b;
            by += ny * b;
        }
        double det = sxx * syy - sxy * sxy;
        if (Math.abs(det) < 1e-9) {
            return fit;
        }
        fit.x = (syy * bx - sxy * by) / det;
        fit.z = (sxx * by - sxy * bx) / det;
        fit.ok = true;
        return fit;
    }

    private void recompute() {
        usedRays.clear();
        excluded = 0;
        inconsistent = false;
        unstable = false;

        if (rays.size() < 2) {
            hasEstimate = false;
            return;
        }

        double threshold = outlierResidual;

        // 枚举射线对求交，按"有多少条射线同意这个交点"投票。
        // 只接受正向交点：交点必须在两条射线的前方，反向延长线上的解直接作废。
        int bestCount = -1;
        double bestX = 0;
        double bestZ = 0;
        List<double[]> bestPoints = new ArrayList<>();

        boolean sawBehind = false;

        double[] p = new double[2];
        for (int i = 0; i < rays.size(); i++) {
            for (int j = i + 1; j < rays.size(); j++) {
                Ray a = rays.get(i);
                Ray b = rays.get(j);
                if (!intersection(a, b, p)) {
                    continue;
                }
                double ta = forwardDistance(p[0], p[1], a);
                double tb = forwardDistance(p[0], p[1], b);
                if (ta < -BEHIND_TOLERANCE || tb < -BEHIND_TOLERANCE) {
                    sawBehind = true;
                    continue;
                }
                // 夹角太小：交点位置对方向误差极其敏感，不能当作可信结果
                if (acuteAngle(a, b) < MIN_CROSS_ANGLE) {
                    continue;
                }

                int count = 0;
                for (Ray r : rays) {
                    if (forwardDistance(p[0], p[1], r) >= -BEHIND_TOLERANCE
                            && perpDistance(p[0], p[1], r) <= threshold) {
                        count++;
                    }
                }

                if (count > bestCount) {
                    bestCount = count;
                    bestX = p[0];
                    bestZ = p[1];
                    bestPoints.clear();
                    bestPoints.add(new double[]{p[0], p[1]});
                } else if (count == bestCount) {
                    bestPoints.add(new double[]{p[0], p[1]});
                }
            }
        }

        if (bestCount < 2) {
            // 完全没有可用的正向交点。用"有没有夹角够大的射线对"区分两种原因：
            // 夹角大却交在背后 → 大概率指向不同要塞；夹角本来就小 → 两条线太平行。
            double widestAngle = 0;
            for (int i = 0; i < rays.size(); i++) {
                for (int j = i + 1; j < rays.size(); j++) {
                    widestAngle = Math.max(widestAngle, acuteAngle(rays.get(i), rays.get(j)));
                }
            }
            hasEstimate = false;
            inconsistent = sawBehind && widestAngle >= Math.toRadians(5.0);
            unstable = !inconsistent;
            return;
        }

        // 票数相同、却解出相隔很远的两组点：两个目标势均力敌，不猜
        for (double[] other : bestPoints) {
            if (Math.hypot(other[0] - bestX, other[1] - bestZ) > threshold * 4.0) {
                hasEstimate = false;
                inconsistent = true;
                return;
            }
        }

        // 用所有同意该交点的射线做最小二乘精修
        List<Ray> inliers = new ArrayList<>();
        for (Ray r : rays) {
            if (forwardDistance(bestX, bestZ, r) >= -BEHIND_TOLERANCE
                    && perpDistance(bestX, bestZ, r) <= threshold) {
                inliers.add(r);
            }
        }

        double fx = bestX;
        double fz = bestZ;
        Fit refined = fit(inliers);
        if (refined != null && refined.ok) {
            boolean stillForward = true;
            for (Ray r : inliers) {
                if (forwardDistance(refined.x, refined.z, r) < -BEHIND_TOLERANCE) {
                    stillForward = false;
                    break;
                }
            }
            if (stillForward) {
                fx = refined.x;
                fz = refined.z;
            }
        }

        double sumSq = 0;
        for (Ray r : inliers) {
            double d = perpDistance(fx, fz, r);
            sumSq += d * d;
            usedRays.add(r);
        }
        excluded = rays.size() - inliers.size();
        estX = fx;
        estZ = fz;
        residual = Math.sqrt(sumSq / Math.max(1, inliers.size()));
        hasEstimate = true;
    }

    /** 两条射线的交点（无限延长线）。平行时返回 false。 */
    private static boolean intersection(Ray a, Ray b, double[] out) {
        double det = a.dx * b.dz - a.dz * b.dx;
        if (Math.abs(det) < 1e-4) {
            return false;
        }
        double ex = b.x - a.x;
        double ez = b.z - a.z;
        double t = (ex * b.dz - ez * b.dx) / det;
        out[0] = a.x + t * a.dx;
        out[1] = a.z + t * a.dz;
        return true;
    }

    /** 两条射线方向的锐夹角（0..π/2）。 */
    private static double acuteAngle(Ray a, Ray b) {
        double dot = Math.abs(a.dx * b.dx + a.dz * b.dz);
        return Math.acos(Math.min(1.0, dot));
    }
}
