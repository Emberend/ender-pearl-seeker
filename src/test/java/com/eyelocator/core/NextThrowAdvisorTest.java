package com.eyelocator.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 第一次投掷之后"下一投该去哪"的建议逻辑。 */
class NextThrowAdvisorTest {

    private static StrongholdLocator.Ray unitRay(double x, double z, double dx, double dz) {
        double len = Math.hypot(dx, dz);
        return new StrongholdLocator.Ray(x, z, dx / len, dz / len);
    }

    @Test
    void noRayMeansNoAdvice() {
        assertNull(NextThrowAdvisor.advise(0, 0, new StrongholdLocator(), 400.0));
    }

    @Test
    void estimateMeansNoAdvice() {
        StrongholdLocator locator = new StrongholdLocator();
        locator.addRay(new StrongholdLocator.Ray(0, 0, 1, 0));
        locator.addRay(new StrongholdLocator.Ray(1000, 0, 0, 1));
        assertTrue(locator.hasEstimate());

        assertNull(NextThrowAdvisor.advise(0, 0, locator, 400.0));
    }

    @Test
    void firstThrowRecommendsSidestepAtBaselineDistance() {
        StrongholdLocator locator = new StrongholdLocator();
        locator.addRay(new StrongholdLocator.Ray(0, 0, 1, 0));

        NextThrowAdvisor.Advice advice = NextThrowAdvisor.advise(0, 0, locator, 400.0);

        assertNotNull(advice);
        assertEquals(NextThrowAdvisor.Kind.MOVE, advice.kind);
        // 推荐点离第一次投掷点正好一个基准距离
        assertEquals(400.0, Math.hypot(advice.x, advice.z), 1e-6);
        // 横向必须错开得足够多，否则两条射线几乎平行
        assertTrue(Math.abs(advice.z) > 300.0);
    }

    @Test
    void alreadySidesteppedEnoughMeansThrowHere() {
        StrongholdLocator locator = new StrongholdLocator();
        locator.addRay(new StrongholdLocator.Ray(0, 0, 1, 0));

        // 玩家已在射线右侧 400 格，横向够了
        NextThrowAdvisor.Advice advice = NextThrowAdvisor.advise(0, 400, locator, 400.0);

        assertNotNull(advice);
        assertEquals(NextThrowAdvisor.Kind.THROW_HERE, advice.kind);
        assertEquals(0.0, advice.travel, 1e-9);
    }

    @Test
    void inconsistentBearingsSuggestSmallRethrow() {
        StrongholdLocator locator = new StrongholdLocator();
        // 交叉但交点在第二条射线背后 → 判定为指向不同目标
        locator.addRay(new StrongholdLocator.Ray(0, 0, 1, 0));
        locator.addRay(unitRay(1000, 1000, -1, 1));
        assertFalse(locator.hasEstimate());
        assertTrue(locator.inconsistent());

        NextThrowAdvisor.Advice advice = NextThrowAdvisor.advise(1000, 1000, locator, 400.0);

        assertNotNull(advice);
        assertEquals(NextThrowAdvisor.Kind.RETHROW, advice.kind);
        assertTrue(advice.travel > 0.0);
    }
}