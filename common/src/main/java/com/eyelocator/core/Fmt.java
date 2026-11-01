package com.eyelocator.core;

import java.util.Locale;

/** 跨加载器共用的小工具：把坐标格式化成固定小数位，避免受系统语言影响。 */
public final class Fmt {

    private Fmt() {
    }

    /** 坐标：整数显示（四舍五入）。 */
    public static String number(double value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }
}