package com.eyelocator.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** 三角定位核心的几何与投票行为。 */
class StrongholdLocatorTest {

    /** 从 (x, z) 指向 (tx, tz) 的单位射线。 */
    private static StrongholdLocator.Ray rayTo(double x, double z, double tx, double tz) {
        double dx = tx - x;
        double dz = tz - z;
        double len = Math.hypot(dx, dz);
        return new StrongholdLocator.Ray(x, z, dx / len, dz / len);
    }

    @Test
    void twoCrossingRaysLocateTarget() {
        StrongholdLocator locator = new StrongholdLocator();
        locator.addRay(rayTo(0, 0, 1000, 1000));
        locator.addRay(rayTo(1000, 0, 0, 1000));

        assertTrue(locator.hasEstimate());
        assertEquals(500.0, locator.estX(), 1e-6);
        assertEquals(500.0, locator.estZ(), 1e-6);
        assertEquals(2, locator.sampleCount());
        assertFalse(locator.inconsistent());
        assertFalse(locator.unstable());
    }

    @Test
    void singleRayDoesNotLocate() {
        StrongholdLocator locator = new StrongholdLocator();
        locator.addRay(rayTo(0, 0, 100, 0));

        assertFalse(locator.hasEstimate());
        assertEquals(1, locator.sampleCount());
        assertNotNull(locator.lastRay());
    }

    @Test
    void raysPointingAwayFromEachOtherAreRejected() {
        StrongholdLocator locator = new StrongholdLocator();
        // 第一条沿 z=0 向 +X；第二条从 (1000,1000) 朝 -X/+Z 远离 z=0，
        // 两者几何上相交，但交点落在第二条射线的背后
        locator.addRay(new StrongholdLocator.Ray(0, 0, 1, 0));
        locator.addRay(rayTo(1000, 1000, 0, 2000));

        assertFalse(locator.hasEstimate());
        assertTrue(locator.inconsistent(), "夹角够大却交在背后，应判定为指向不同目标");
    }

    @Test
    void nearlyParallelRaysAreUnstable() {
        StrongholdLocator locator = new StrongholdLocator();
        locator.addRay(new StrongholdLocator.Ray(0, 0, 1, 0));
        // 夹角约 0.29°，低于 1.5° 下限
        locator.addRay(new StrongholdLocator.Ray(0, 100, 1, 0.005));

        assertFalse(locator.hasEstimate());
        assertFalse(locator.inconsistent());
        assertTrue(locator.unstable());
    }

    @Test
    void duplicateRayIsIgnored() {
        StrongholdLocator locator = new StrongholdLocator();
        locator.addRay(new StrongholdLocator.Ray(10, 10, 1, 0));
        locator.addRay(new StrongholdLocator.Ray(10, 10, 1, 0));

        assertEquals(1, locator.sampleCount());
    }

    @Test
    void outlierRayIsDroppedFromFit() {
        StrongholdLocator locator = new StrongholdLocator();
        locator.setOutlierResidual(20.0);
        locator.addRay(rayTo(0, 0, 1000, 1000));
        locator.addRay(rayTo(1000, 0, 0, 1000));
        locator.addRay(new StrongholdLocator.Ray(0, 0, 1, 0)); // 偏得离谱的一条
        locator.addRay(rayTo(0, 1000, 1000, 0));
        locator.addRay(rayTo(1000, 1000, 0, 0));

        assertTrue(locator.hasEstimate());
        assertEquals(500.0, locator.estX(), 5.0);
        assertEquals(500.0, locator.estZ(), 5.0);
        assertEquals(1, locator.excluded(), "明显偏离的射线应被剔除");
    }

    @Test
    void restoreRaysRecomputesEstimate() {
        StrongholdLocator source = new StrongholdLocator();
        source.addRay(rayTo(0, 0, 1000, 1000));
        source.addRay(rayTo(1000, 0, 0, 1000));
        assertTrue(source.hasEstimate());

        List<StrongholdLocator.Ray> snapshot = source.rays();
        assertEquals(2, snapshot.size());

        StrongholdLocator restored = new StrongholdLocator();
        restored.restoreRays(snapshot);
        assertTrue(restored.hasEstimate());
        assertEquals(source.estX(), restored.estX(), 1e-9);
        assertEquals(source.estZ(), restored.estZ(), 1e-9);
    }

    @Test
    void restoreRaysSkipsInvalidEntries() {
        StrongholdLocator locator = new StrongholdLocator();
        locator.restoreRays(List.of(
                new StrongholdLocator.Ray(Double.NaN, 0, 1, 0),
                new StrongholdLocator.Ray(0, 0, 1, 0)));

        assertEquals(1, locator.sampleCount());
        assertEquals(1.0, locator.lastRay().dx, 1e-9);
    }

    @Test
    void resetClearsEverything() {
        StrongholdLocator locator = new StrongholdLocator();
        locator.addRay(rayTo(0, 0, 1000, 1000));
        locator.addRay(rayTo(1000, 0, 0, 1000));
        locator.reset();

        assertFalse(locator.hasEstimate());
        assertEquals(0, locator.sampleCount());
        assertEquals(0.0, locator.confidence(), 1e-9);
    }
}