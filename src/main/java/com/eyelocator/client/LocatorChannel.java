package com.eyelocator.client;

/**
 * 一个定位通道：对应一种"会飞向某个结构"的道具/实体。
 *
 * <p>不同模组的眼睛可能指向不同结构（要塞、远古城市、海底神殿……），
 * 混在一起求交会得到错误结果，所以每个来源单独一套射线与归档。</p>
 */
public final class LocatorChannel {

    /** 通道 id，通常就是实体类型 id，例如 {@code minecraft:eye_of_ender}。 */
    public final String id;

    /** 给玩家看的名字，例如"末地要塞"。可在配置里覆盖。 */
    public final String displayName;

    /** 本通道自己的射线集合。 */
    public final StrongholdLocator locator = new StrongholdLocator();

    public LocatorChannel(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /** 来源 mod id（实体 id 冒号前部分）。 */
    public String modId() {
        int idx = id.indexOf(':');
        return idx < 0 ? "minecraft" : id.substring(0, idx);
    }
}