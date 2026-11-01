package com.eyelocator.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 未完成定位的磁盘快照。 */
class LocatorStateTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    @AfterEach
    void resetChannels() {
        ChannelManager.clear();
    }

    private static StrongholdLocator.Ray ray(double x, double z, double dx, double dz) {
        double len = Math.hypot(dx, dz);
        return new StrongholdLocator.Ray(x, z, dx / len, dz / len);
    }

    @Test
    void roundTripKeepsRaysAndActiveChannel() throws Exception {
        LocatorChannel eye = ChannelManager.getOrCreate("minecraft:eye_of_ender");
        eye.locator.addRay(ray(0, 0, 1000, 1000));
        eye.locator.addRay(ray(1000, 0, 0, 1000));
        ChannelManager.setActive(eye);

        LocatorChannel other = ChannelManager.getOrCreate("testmod:ancient_eye");
        other.locator.addRay(ray(0, 0, 1, 0));

        Path file = tempDir.resolve("state.json");
        LocatorState.capture("minecraft:overworld").write(file);
        assertTrue(Files.exists(file));

        LocatorState loaded = LocatorState.read(file);
        assertNotNull(loaded);
        assertEquals("minecraft:overworld", loaded.dimension);
        assertEquals("minecraft:eye_of_ender", loaded.activeChannel);
        assertEquals(3, loaded.rayCount());

        ChannelManager.resetAllRays();
        loaded.apply();

        assertEquals(2, ChannelManager.byId("minecraft:eye_of_ender").locator.sampleCount());
        assertEquals(1, ChannelManager.byId("testmod:ancient_eye").locator.sampleCount());
        assertEquals("minecraft:eye_of_ender", ChannelManager.active().id);
        assertTrue(ChannelManager.byId("minecraft:eye_of_ender").locator.hasEstimate());
    }

    @Test
    void writeWithoutRaysDeletesFile() throws Exception {
        Path file = tempDir.resolve("state.json");
        Files.writeString(file, "{}");

        LocatorState.capture("minecraft:overworld").write(file);
        assertFalse(Files.exists(file));
    }

    @Test
    void missingFileReadsAsNull() throws Exception {
        assertNull(LocatorState.read(tempDir.resolve("missing.json")));
    }

    @Test
    void keepOnlyDropsOtherDimension() {
        LocatorState state = new LocatorState();
        state.dimension = "minecraft:the_nether";
        state.channels.put("minecraft:eye_of_ender", java.util.List.of(ray(0, 0, 1, 0)));

        assertFalse(state.keepOnly("minecraft:overworld"));
        assertEquals(0, state.rayCount());
        assertEquals("minecraft:overworld", state.dimension);
    }

    @Test
    void keepOnlyKeepsSameDimension() {
        LocatorState state = new LocatorState();
        state.dimension = "minecraft:overworld";
        state.channels.put("minecraft:eye_of_ender", java.util.List.of(ray(0, 0, 1, 0)));

        assertTrue(state.keepOnly("minecraft:overworld"));
        assertEquals(1, state.rayCount());
    }

    @Test
    void unknownActiveChannelFallsBackToDefault() {
        LocatorState state = new LocatorState();
        state.activeChannel = "nope:nothing";
        state.apply();

        // active() 在没有有效通道时会回退到原版末影之眼
        assertNotNull(ChannelManager.active());
        assertEquals(ChannelManager.VANILLA_EYE, ChannelManager.active().id);
    }
}