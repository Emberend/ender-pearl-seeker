package com.eyelocator.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forge 版入口。
 *
 * <p><strong>这个类必须保持"加载器无关"</strong>：不能 import 任何 {@code net.minecraft.client.*}
 * 类型，也不能直接引用 {@link ClientSetup} 之外的客户端类。Forge 的 DistCleaner 在专用服务端
 * 加载主类时，一旦发现常量池里出现客户端类型就会抛
 * {@code Attempted to load class net/minecraft/client/player/LocalPlayer for invalid dist}，
 * 整个 mod 直接加载失败。客户端代码一律通过 {@link DistExecutor} 延迟加载。</p>
 */
@Mod(EyeLocatorForge.MOD_ID)
public final class EyeLocatorForge {

    public static final String MOD_ID = "eyelocator";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public EyeLocatorForge() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientSetup::init);
    }
}