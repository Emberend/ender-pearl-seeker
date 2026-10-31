package com.eyelocator.client;

import java.util.ArrayList;
import java.util.List;

/**
 * 末地要塞定位核心：记录若干条"方位射线"，用最小二乘法求所有射线的交点。
 * 当样本 >=3 条时会做一次稳健拟合：残差过大的离群射线会被剔除后再重新拟合。
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
    private final List<Ray> usedRays = new ArrayList<>();

    private boolean hasEstimate;
    private double estX;
    private double estZ;
    private double residual;
    private int excluded;

    public void reset() {
        rays.clear();
        usedRays.clear();
        hasEstimate = false;
        estX = 0;
        estZ = 0;
        residual = 0;
        excluded = 0;
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

    /** 射线到估计交点的垂直距离。 */
    public double perpDistanceToEstimate(Ray ray) {
        return perpDistance(estX, estZ, ray);
    }

    public static double perpDistance(double x, double z, Ray ray) {
        return Math.abs(-ray.dz * (x - ray.x) + ray.dx * (z - ray.z));
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

        if (rays.size() < 2) {
            hasEstimate = false;
            return;
        }

        double threshold = EyeLocatorConfig.get().outlierResidual;
        List<Ray> working = new ArrayList<>(rays);

        // 稳健拟合：样本 >=3 时迭代剔除残差最大的离群射线
        for (int iter = 0; iter < 4 && working.size() >= 3; iter++) {
            Fit f = fit(working);
            if (!f.ok) {
                break;
            }
            double worst = -1;
            int worstIndex = -1;
            for (int i = 0; i < working.size(); i++) {
                double d = perpDistance(f.x, f.z, working.get(i));
                if (d > worst) {
                    worst = d;
                    worstIndex = i;
                }
            }
            if (worst > threshold && working.size() - 1 >= 2) {
                working.remove(worstIndex);
                excluded++;
            } else {
                break;
            }
        }

        Fit finalFit = fit(working);
        if (!finalFit.ok) {
            hasEstimate = false;
            return;
        }
        double sumSq = 0;
        for (Ray r : working) {
            double d = perpDistance(finalFit.x, finalFit.z, r);
            sumSq += d * d;
        }
        usedRays.addAll(working);
        estX = finalFit.x;
        estZ = finalFit.z;
        residual = Math.sqrt(sumSq / working.size());
        hasEstimate = true;
    }
}
