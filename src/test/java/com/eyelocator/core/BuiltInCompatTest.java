package com.eyelocator.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 兼容表读取：整份替换、叠加内置、损坏文件回退。 */
class BuiltInCompatTest {

    @TempDir
    Path dir;

    private static final java.util.List<String> LOG = new java.util.ArrayList<>();

    private void load(Path file) {
        LOG.clear();
        BuiltInCompat.load(file, LOG::add, LOG::add);
    }

    /** 每个用例先从"文件不存在"开始，拿到内置默认表。 */
    private void reset() {
        load(dir.resolve("reset-" + System.nanoTime() + ".json"));
    }

    @Test
    void missingFileCreatesTemplateAndUsesBuiltIn() throws IOException {
        Path file = dir.resolve("eyelocator_compat.json");

        load(file);

        assertTrue(Files.exists(file), "文件不存在时应生成模板");
        assertTrue(BuiltInCompat.isKnown("minecraft:eye_of_ender"));
        assertTrue(BuiltInCompat.isKnown("goety:cryptic_eye"));
        assertTrue(BuiltInCompat.isIgnored("goety:survey_eye"));
        assertEquals("末地要塞", BuiltInCompat.displayName("minecraft:eye_of_ender"));
        assertEquals("eyelocator.channel.cryptic_eye",
                BuiltInCompat.displayKey("goety:cryptic_eye"));
        assertEquals(7, BuiltInCompat.all().size());

        // 生成的模板本身能被重新读回
        String json = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(json.contains("minecraft:eye_of_ender"));
        assertTrue(json.contains("inheritBuiltIn"));
    }

    @Test
    void fileReplacesBuiltInTableEntirely() throws IOException {
        Path file = dir.resolve("custom.json");
        Files.writeString(file, """
                {
                  "channels": {
                    "mymod:ancient_eye": {"name": "远古之眼", "key": "mymod.channel.ancient_eye"}
                  },
                  "ignored": ["mymod:fake_eye"]
                }
                """, StandardCharsets.UTF_8);

        load(file);

        assertTrue(BuiltInCompat.isKnown("mymod:ancient_eye"));
        assertEquals("远古之眼", BuiltInCompat.displayName("mymod:ancient_eye"));
        assertEquals("mymod.channel.ancient_eye", BuiltInCompat.displayKey("mymod:ancient_eye"));
        assertTrue(BuiltInCompat.isIgnored("mymod:fake_eye"));

        // 整份替换：内置条目和内置排除项都不再生效
        assertFalse(BuiltInCompat.isKnown("goety:cryptic_eye"));
        assertFalse(BuiltInCompat.isIgnored("goety:survey_eye"));
        assertEquals(1, BuiltInCompat.all().size());
    }

    @Test
    void inheritBuiltInMergesInsteadOfReplacing() throws IOException {
        Path file = dir.resolve("merge.json");
        Files.writeString(file, """
                {
                  "inheritBuiltIn": true,
                  "channels": {
                    "mymod:ancient_eye": {"name": "远古之眼"},
                    "minecraft:eye_of_ender": {"name": "覆盖后的末地要塞"}
                  },
                  "ignored": ["mymod:fake_eye"]
                }
                """, StandardCharsets.UTF_8);

        load(file);

        // 内置仍在
        assertTrue(BuiltInCompat.isKnown("goety:void_eye"));
        assertTrue(BuiltInCompat.isIgnored("goety:survey_eye"));
        // 新增条目生效
        assertTrue(BuiltInCompat.isKnown("mymod:ancient_eye"));
        assertTrue(BuiltInCompat.isIgnored("mymod:fake_eye"));
        // 同名时文件优先
        assertEquals("覆盖后的末地要塞", BuiltInCompat.displayName("minecraft:eye_of_ender"));
        assertEquals(8, BuiltInCompat.all().size());
    }

    @Test
    void brokenFileKeepsBuiltInAndDoesNotOverwrite() throws IOException {
        reset();
        Path file = dir.resolve("broken.json");
        String broken = "{ \"channels\": { not json";
        Files.writeString(file, broken, StandardCharsets.UTF_8);

        load(file);

        assertTrue(BuiltInCompat.isKnown("minecraft:eye_of_ender"), "解析失败应回退内置");
        assertEquals(broken, Files.readString(file, StandardCharsets.UTF_8), "不能覆盖用户文件");
        assertFalse(LOG.isEmpty());
        assertTrue(LOG.get(0).contains("失败"));
    }

    @Test
    void blankEntriesAreSkippedAndEntryMayOmitFields() throws IOException {
        Path file = dir.resolve("partial.json");
        Files.writeString(file, """
                {
                  "channels": {
                    "   ": {"name": "空白 id 应被忽略"},
                    "mymod:no_key": {"name": "只有名字"},
                    "mymod:no_name": {}
                  },
                  "ignored": ["", "  "]
                }
                """, StandardCharsets.UTF_8);

        load(file);

        assertEquals(2, BuiltInCompat.all().size());
        assertTrue(BuiltInCompat.isKnown("mymod:no_key"));
        assertNull(BuiltInCompat.displayKey("mymod:no_key"));
        assertTrue(BuiltInCompat.isKnown("mymod:no_name"));
        assertNull(BuiltInCompat.displayName("mymod:no_name"));
    }
}