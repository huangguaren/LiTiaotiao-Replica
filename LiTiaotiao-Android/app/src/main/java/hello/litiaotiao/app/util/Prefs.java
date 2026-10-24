package hello.litiaotiao.app.util;

import android.content.Context;
import android.content.SharedPreferences;

/** 全局设置 + 跳过计数，键名与原版一致。 */
public final class Prefs {
    private static final String NAME = "ltt_prefs";

    // keys
    public static final String K_MAIN = "is_main_switch_on";
    public static final String K_HIDE_TOAST = "hide_toast";
    public static final String K_SHOW_ALL = "show_all_apps";
    public static final String K_WL_ALL = "whitelist_all_apps";
    public static final String K_FILTER_INVISIBLE = "filter_invisible_nodes";
    public static final String K_FILTER_YOUNG = "filter_young_popups";
    public static final String K_REVERSE = "reverse_traversal";
    /** 严格防重复点击：同一控件冷却时间由 1.5 秒放宽到 5 秒，避免常驻“跳过”按钮被反复点击。 */
    public static final String K_STRICT_DEDUPE = "strict_dedupe";
    public static final String K_SKIP_COUNT = "skip_count";
    public static final String K_SEEDED = "seeded";

    // 全局规则 + 小程序
    public static final String K_GLOBAL_ENABLED = "global_rules_enabled";
    public static final String K_GLOBAL_RULES = "global_rules_json";
    public static final String K_MP_HOSTS = "mini_program_hosts";     // JSON 数组字符串
    public static final String K_MP_AUTO_BACK = "mini_program_auto_back";

    public static final String DEFAULT_GLOBAL_RULES =
            "{\"keywords\":[\"跳过广告\",\"关闭广告\"],\"popup_rules\":[]}";

    private Prefs() {}

    private static SharedPreferences sp(Context ctx) {
        return ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static boolean getBool(Context ctx, String key, boolean def) {
        return sp(ctx).getBoolean(key, def);
    }

    public static void putBool(Context ctx, String key, boolean value) {
        sp(ctx).edit().putBoolean(key, value).apply();
    }

    public static String getString(Context ctx, String key, String def) {
        return sp(ctx).getString(key, def);
    }

    public static void putString(Context ctx, String key, String value) {
        sp(ctx).edit().putString(key, value).apply();
    }

    public static long getSkipCount(Context ctx) {
        return sp(ctx).getLong(K_SKIP_COUNT, 0L);
    }

    public static void addSkip(Context ctx, long delta) {
        SharedPreferences.Editor e = sp(ctx).edit();
        e.putLong(K_SKIP_COUNT, getSkipCount(ctx) + delta);
        e.apply();
    }

    public static boolean isSeeded(Context ctx) {
        return sp(ctx).getBoolean(K_SEEDED, false);
    }

    public static void setSeeded(Context ctx) {
        sp(ctx).edit().putBoolean(K_SEEDED, true).apply();
    }
}
