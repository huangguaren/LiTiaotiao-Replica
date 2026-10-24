package hello.litiaotiao.app.service;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

import hello.litiaotiao.app.util.Prefs;

/**
 * 跳过提示悬浮窗（对齐原版李跳跳跳过广告后闪现的「小兔子乖乖」）。
 *
 * 为什么不用系统 Toast：
 * 本工程 targetSdk 34，在 Android 11+ 上后台服务弹出的 Toast 会被系统拦截不显示，
 * 因此沿用原版做法——用 WindowManager 叠加一个轻量 TextView。
 *
 * 行为：
 *  - 受「隐藏 Toast 提示」(hide_toast) 开关控制，默认显示；
 *  - 若未授予「显示在其他应用上层」权限则静默不显示，避免崩溃；
 *  - 单次跳过只保留一个悬浮窗，连续跳过只刷新文案并重置隐藏计时。
 */
public final class SkipTip {
    private static final long SHOW_MS = 1600;          // 显示时长
    private static final long FADE_MS = 280;           // 淡出时长

    private static TextView sTipView;
    // 延迟初始化：避免在 JVM 单测加载本类时触碰 Android Looper（无 Android 运行时）。
    private static Handler sHandler;
    private static Handler handler() {
        if (sHandler == null) sHandler = new Handler(Looper.getMainLooper());
        return sHandler;
    }
    private static final Runnable sHide = () -> fadeOut();

    private SkipTip() {}

    /** 提示文案：对齐原版李跳跳跳过广告后闪现的悬浮提示文本。 */
    public static String formatTip(long count) {
        return "小兔子乖乖";
    }

    /** 跳过后调用：在屏幕上闪现一条累计跳过次数提示。 */
    public static void show(Context ctx) {
        if (ctx == null) return;
        if (Prefs.getBool(ctx, Prefs.K_HIDE_TOAST, false)) return;   // 用户选择隐藏
        if (!canOverlay(ctx)) return;                                // 无悬浮窗权限，静默
        if (getWm(ctx) == null) return;

        final long count = Prefs.getSkipCount(ctx);
        handler().post(() -> render(ctx, count));
    }

    private static void render(Context ctx, long count) {
        WindowManager wm = getWm(ctx);
        if (wm == null) return;

        if (sTipView == null) {
            TextView tv = new TextView(ctx.getApplicationContext());
            tv.setTextColor(0xFFFFFFFF);
            tv.setTextSize(13);
            tv.setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8));

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xCC000000);
            bg.setCornerRadius(dp(ctx, 16));
            tv.setBackground(bg);

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    windowType(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.y = dp(ctx, 120);

            try {
                wm.addView(tv, lp);
                sTipView = tv;
            } catch (Exception ignore) {
                return; // 极端情况下叠加失败，不影响跳过主流程
            }
        }

        sTipView.animate().cancel();
        sTipView.setAlpha(1f);
        sTipView.setText(formatTip(count));

        handler().removeCallbacks(sHide);
        handler().postDelayed(sHide, SHOW_MS);
    }

    private static void fadeOut() {
        if (sTipView == null) return;
        final TextView tv = sTipView;
        sTipView = null;
        tv.animate().alpha(0f).setDuration(FADE_MS)
                .withEndAction(() -> {
                    try {
                        ((WindowManager) tv.getContext().getSystemService(Context.WINDOW_SERVICE))
                                .removeView(tv);
                    } catch (Exception ignore) {
                        // 已被移除等情况忽略
                    }
                });
    }

    private static boolean canOverlay(Context ctx) {
        try {
            return Settings.canDrawOverlays(ctx);
        } catch (Exception ignore) {
            return false;
        }
    }

    private static WindowManager getWm(Context ctx) {
        try {
            return (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        } catch (Exception ignore) {
            return null;
        }
    }

    private static int windowType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
        return WindowManager.LayoutParams.TYPE_PHONE;
    }

    private static int dp(Context ctx, int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
