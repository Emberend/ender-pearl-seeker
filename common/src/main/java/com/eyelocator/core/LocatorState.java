package com.eyelocator.core;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * 进行中的定位状态（还没归档的方位射线）的磁盘快照。
 *
 * <p>每个通道分开存，退出游戏后重新进入仍能接着投，不用从头开始。
 * 只保存射线本身和维度，不保存任何 Minecraft 类型，因此 Fabric 与 Forge 共用。</p>
 */
public final class LocatorState {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 记录这些射线时所在的维度。换维度后旧数据没有意义。 */
    public String dimension;

    /** 上次 HUD 显示的通道。 */
    public String activeChannel;

    /** 通道 id -> 射线列表。 */
    public Map<String, List<StrongholdLocator.Ray>> channels = new LinkedHashMap<>();

    public LocatorState() {
    }

    /** 从当前内存状态生成快照。 */
    public static LocatorState capture(String dimension) {
        LocatorState state = new LocatorState();
        state.dimension = dimension;
        state.activeChannel = ChannelManager.active().id;
        for (LocatorChannel channel : ChannelManager.all()) {
            List<StrongholdLocator.Ray> rays = channel.locator.rays();
            if (!rays.isEmpty()) {
                state.channels.put(channel.id, rays);
            }
        }
        return state;
    }

    /** 把快照写回内存中的通道。会先清空所有现有射线。 */
    public void apply() {
        ChannelManager.resetAllRays();
        for (Map.Entry<String, List<StrongholdLocator.Ray>> entry : channels.entrySet()) {
            LocatorChannel channel = ChannelManager.getOrCreate(entry.getKey());
            channel.locator.restoreRays(entry.getValue());
        }
        LocatorChannel active = activeChannel == null ? null : ChannelManager.byId(activeChannel);
        if (active != null) {
            ChannelManager.setActive(active);
        }
    }

    public boolean hasRays() {
        for (List<StrongholdLocator.Ray> rays : channels.values()) {
            if (rays != null && !rays.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** 读取快照；文件不存在返回 null，内容损坏抛 IOException。 */
    public static LocatorState read(Path path) throws IOException {
        if (!Files.exists(path)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            LocatorState state = GSON.fromJson(reader, LocatorState.class);
            if (state == null) {
                return null;
            }
            if (state.channels == null) {
                state.channels = new LinkedHashMap<>();
            }
            return state;
        } catch (RuntimeException e) {
            throw new IOException("状态文件格式无效", e);
        }
    }

    /** 写入快照；射线为空时直接删除文件，避免留下空壳。 */
    public void write(Path path) throws IOException {
        if (!hasRays()) {
            Files.deleteIfExists(path);
            return;
        }
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(this, writer);
        }
    }

    /**
     * 只保留指定维度的数据；维度不同说明是上一个世界的残留，直接丢弃。
     * 返回是否保留了数据。
     */
    public boolean keepOnly(String currentDimension) {
        if (dimension == null || currentDimension == null) {
            return true;
        }
        if (dimension.equals(currentDimension)) {
            return true;
        }
        channels = new LinkedHashMap<>();
        dimension = currentDimension;
        return false;
    }

    /** 便于调试与测试：所有射线的总数。 */
    public int rayCount() {
        int count = 0;
        for (List<StrongholdLocator.Ray> rays : channels.values()) {
            if (rays != null) {
                count += rays.size();
            }
        }
        return count;
    }

    /** 深拷贝（测试用）。 */
    public LocatorState copy() {
        LocatorState copy = new LocatorState();
        copy.dimension = dimension;
        copy.activeChannel = activeChannel;
        for (Map.Entry<String, List<StrongholdLocator.Ray>> entry : channels.entrySet()) {
            copy.channels.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return copy;
    }
}