package com.eyelocator.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * ModMenu 集成：在 ModMenu 里给本 Mod 提供一个"设置"按钮。
 *
 * <p>这个类只在安装了 ModMenu 时才会被加载（entrypoint 由 ModMenu 自己解析），
 * 没装 ModMenu 时不影响 Mod 本身运行。因此 ModMenu 依赖是 compileOnly。</p>
 */
public final class EyeLocatorModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return EyeLocatorScreen::new;
    }
}