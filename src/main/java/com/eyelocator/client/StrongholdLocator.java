package com.eyelocator.client;

import java.util.ArrayList;
import java.util.List;

/**
 * 末地要塞定位核心：记录若干条"方位射线"，用最小二乘法求所有射线的交点，
 * 交点即为要塞的水平坐标估计。
 *
 * <p>每条射线用水平面上的一个起点 {@code (x, z)} 和一个单位方向 {@code (dx, dz)} 表示。
 * 直线方程：过点 P、方向 d，等价于其法线 n=(-dz, dx) 满足 n·X = n·P。
 * 对多条直线用最小二乘解 2x2 法方程，即可得到最佳交点。</p>
 */
public final class StrongholdLocator {

    /** 一条由一次末影之眼投掷得到的方位射线（水平面）。 */
    public static final class Ray {
        public final double x;
        public final double z;
        public final double dx;
        public final double dz;

        public Ray(double x, double z, double dx, double dz) {
            this.x = x;
            this.z = z;
            this.dx = dx;
            this.dz = dz;
        }
    }

    private final List<Ray> rays = new ArrayList<>();

    private boolean hasEstimate;
    private double estX;
    private double estZ;
    /** 各射线到估计点的垂直距离 RMS（方块），越小越可信。 */
    private double residual;

    public void reset() {
        rays.clear();
        hasEstimate = false;
        estX = 0;
        estZ = 0;
        residual = 0;
    }

    public int sampleCount() {
        return rays.size();
    }

    /** 最近一条方位射线；没有则返回 null。 */
    public Ray lastRay() {
        return rays.isEmpty() ? null : rays.get(rays.size() - 1);
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

    /**
     * 方位射线的角度分散度（弧度，0..π/2）。分散度越大，交点越稳定。
     * 若两次投掷方向几乎平行，则交点会非常不可靠。
     */
    public double angleSpread() {
        if (rays.size() < 2) {
            return 0;
        }
        double minDir = Double.POSITIVE_INFINITY;
        double maxDir = Double.NEGATIVE_INFINITY;
        for (Ray r : rays) {
            double a = Math.atan2(r.dz, r.dx);
            // 方向线是双向的，归一到 [0, π)
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

    /** 综合可信度 0..1。样本越多、残差越小、方向越分散，置信度越高。 */
    public double confidence() {
        if (!hasEstimate) {
            return 0;
        }
        double spread = angleSpread();
        double spreadFactor = Math.min(1.0, spread / (Math.PI / 6.0)); // 30° 即满
        double residualFactor = 1.0 / (1.0 + residual / 40.0);       // 残差 40 格降一半
        double sampleFactor = Math.min(1.0, rays.size() / 3.0);
        return Math.max(0.0, Math.min(1.0, spreadFactor * residualFactor * sampleFactor));
    }

    /**
     * 加入一条新射线。若与已有射线方向和起点都几乎相同（重复投掷），则忽略以免干扰拟合。
     */
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

    private void recompute() {
        if (rays.size() < 2) {
            hasEstimate = false;
            return;
        }
        double sxx = 0, sxy = 0, syy = 0, bx = 0, by = 0;
        for (Ray r : rays) {
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
            // 方向全部平行，无法求交点
            hasEstimate = false;
            return;
        }
        double x = (syy * bx - sxy * by) / det;
        double z = (sxx * by - sxy * bx) / det;

        double sumSq = 0;
        for (Ray r : rays) {
            double nx = -r.dz;
            double ny = r.dx;
            double d = nx * (x - r.x) + ny * (z - r.z);
            sumSq += d * d;
        }
        residual = Math.sqrt(sumSq / rays.size());

        estX = x;
        estZ = z;
        hasEstimate = true;
    }
}
