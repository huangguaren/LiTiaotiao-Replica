package hello.litiaotiao.app.service;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import hello.litiaotiao.app.data.Repository;
import hello.litiaotiao.app.data.Repository.AppDetail;
import hello.litiaotiao.app.engine.NodeSnapshot;
import hello.litiaotiao.app.engine.PlannedAction;
import hello.litiaotiao.app.engine.RuleEngine;
import hello.litiaotiao.app.engine.RuleSet;
import hello.litiaotiao.app.util.JavaHash;
import hello.litiaotiao.app.util.Prefs;

/**
 * 李跳跳无障碍服务：监听窗口/内容变化事件，取当前前台应用的规则，遍历节点树，
 * 按规则自动点击「跳过 / 关闭」等控件，或触发系统返回键。
 */
public class LttAccessibilityService extends AccessibilityService {
    private static final long THROTTLE_MS = 120;      // 事件节流
    private static final long DEDUPE_MS = 1500;       // 宽松模式：同一控件冷却 1.5 秒
    private static final long DEDUPE_MS_STRICT = 5000;// 严格模式：同一控件冷却 5 秒
    private static final long MAX_SIG_ENTRIES = 200;  // 去重签名表上限

    /** 系统界面 / 桌面 / 输入法等不参与广告跳过（避免误点系统 UI）。 */
    private static final java.util.Set<String> IGNORED_PACKAGES = new java.util.HashSet<>(
            java.util.Arrays.asList(
                    "android",
                    "com.android.systemui",
                    "com.android.launcher",
                    "com.android.launcher3",
                    "com.android.settings",
                    "com.android.packageinstaller",
                    "com.android.permissioncontroller",
                    "com.android.inputmethod",
                    "com.android.internal.systemui",
                    "com.google.android.inputmethod.latin"
            ));
    private static final java.util.List<String> IGNORED_PREFIXES = java.util.Arrays.asList(
            "com.android.inputmethod.",
            "com.google.android.inputmethod."
    );

    private boolean isIgnoredPackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) return true;
        if (pkg.equals(getPackageName())) return true;      // 忽略自身界面
        if (IGNORED_PACKAGES.contains(pkg)) return true;
        for (String prefix : IGNORED_PREFIXES) {
            if (pkg.startsWith(prefix)) return true;
        }
        return false;
    }

    private void recycleNode(AccessibilityNodeInfo node) {
        if (node == null) return;
        try {
            node.recycle();
        } catch (Exception ignore) {
            // 新版本 API 上 recycle 已废弃，失败可忽略
        }
    }

    private final android.os.Handler handler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private Repository repo;
    private long lastProcessedAt = 0L;
    private final Map<String, Long> lastClickSig = new HashMap<>();

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        repo = new Repository(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || repo == null) return;
        if (!Prefs.getBool(this, Prefs.K_MAIN, true)) return;                 // 主开关
        if (Prefs.getBool(this, Prefs.K_WL_ALL, false)) return;              // 全应用白名单

        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && type != AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            return;
        }

        long now = SystemClock.uptimeMillis();
        if (now - lastProcessedAt < THROTTLE_MS) return;
        lastProcessedAt = now;

        // 先取当前窗口，再以「窗口所属应用」为准（事件来源可能是系统 UI / 输入法）
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        String pkg = null;
        if (root.getPackageName() != null) {
            pkg = root.getPackageName().toString();
        }
        if (pkg == null || pkg.isEmpty()) {
            pkg = resolvePackage(event);
        }
        if (isIgnoredPackage(pkg)) return;
        final String pkgName = pkg;

        int hash = JavaHash.hash(pkg);
        try {
            AppDetail app = repo.getApp(hash);
            // 白名单应用：完全跳过（含全局规则），尊重用户选择
            if (app != null && app.whitelisted) return;

            RuleSet appRules = (app != null && app.enabled) ? repo.buildRuleSet(app) : null;

            boolean globalEnabled = Prefs.getBool(this, Prefs.K_GLOBAL_ENABLED, true);
            RuleSet globalRules = globalEnabled ? buildGlobalRules() : null;

            boolean hasApp = appRules != null && appRules.hasAnyRule();
            boolean hasGlobal = globalRules != null && globalRules.hasAnyRule();
            if (!hasApp && !hasGlobal) {
                recycleNode(root);
                return;
            }

            NodeSnapshot snapshot = NodeTree.build(root);
            if (snapshot == null) {
                recycleNode(root);
                return;
            }

            boolean filterInvisible = Prefs.getBool(this, Prefs.K_FILTER_INVISIBLE, true);
            boolean filterYoung = Prefs.getBool(this, Prefs.K_FILTER_YOUNG, true);
            boolean strictDedupe = Prefs.getBool(this, Prefs.K_STRICT_DEDUPE, true);
            java.util.List<String> mpHosts = parseHosts(Prefs.getString(this, Prefs.K_MP_HOSTS, ""));
            boolean mpAutoBack = Prefs.getBool(this, Prefs.K_MP_AUTO_BACK, false);

            List<PlannedAction> actions = RuleEngine.evaluate(
                    hasApp ? appRules : null, globalRules, snapshot, filterInvisible,
                    pkg, mpHosts, mpAutoBack, filterYoung);
            if (actions.isEmpty()) {
                snapshot.recycleTree();
                return;
            }

            final NodeSnapshot snap = snapshot;
            final List<PlannedAction> acts = actions;
            final long dedupeMs = strictDedupe ? DEDUPE_MS_STRICT : DEDUPE_MS;
            final int delayMs = appRules == null ? 0
                    : Math.max(appRules.getParamInt("delay_popup", 0), appRules.getParamInt("delay", 0));

            Runnable task = () -> {
                try {
                    // 用「实际执行时刻」而非「事件到达时刻」做去重，
                    // 否则配置了 delay 时，延迟会让 nowMs - last 偏小，误判为刚点过。
                    long execMs = System.currentTimeMillis();
                    int performed = 0;
                    for (PlannedAction a : acts) {
                        String sig = signature(pkgName, a);
                        Long last = lastClickSig.get(sig);
                        if (last != null && execMs - last < dedupeMs) continue;
                        lastClickSig.put(sig, execMs);

                        boolean ok;
                        try {
                            ok = perform(a);
                        } catch (Exception ignore) {
                            ok = false;
                        }
                        if (ok) {
                            performed++;
                            repo.logSkip(hash, a.ruleType, pkgName, triggerOf(a), a.type.name(), targetOf(a));
                        }
                    }
                    if (performed > 0) {
                        Prefs.addSkip(this, performed);
                    }
                    if (lastClickSig.size() > MAX_SIG_ENTRIES) lastClickSig.clear();
                } finally {
                    snap.recycleTree();
                }
            };
            if (delayMs > 0) {
                handler.postDelayed(task, delayMs);
            } else {
                task.run();
            }
        } catch (Exception ignore) {
            // 单个应用处理失败不应影响服务本身
        }
    }

    private String resolvePackage(AccessibilityEvent event) {
        if (event.getPackageName() != null) return event.getPackageName().toString();
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null && root.getPackageName() != null) return root.getPackageName().toString();
        return null;
    }

    private boolean perform(PlannedAction a) {
        switch (a.type) {
            case BACK:
                return performGlobalAction(GLOBAL_ACTION_BACK);
            case NODE:
                if (a.target != null && a.target.handle != null) {
                    boolean r = a.target.handle.click();
                    if (r) return true;
                }
                if (a.target != null && a.target.bounds != null) {
                    tapCenter(a.target.bounds);
                    return true;
                }
                return false;
            case BOUNDS:
                if (a.coords != null) { tapCenter(a.coords); return true; }
                return false;
            case NONE:
            default:
                return false;
        }
    }

    private void tapCenter(int[] b) {
        float x = (b[0] + b[2]) / 2f;
        float y = (b[1] + b[3]) / 2f;
        Path p = new Path();
        p.moveTo(x, y);
        android.accessibilityservice.GestureDescription.StrokeDescription stroke =
                new android.accessibilityservice.GestureDescription.StrokeDescription(p, 0, 60);
        android.accessibilityservice.GestureDescription gesture =
                new android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build();
        dispatchGesture(gesture, null, null);
    }

    /**
     * 同一个控件的去重身份。
     *
     * <p>注意：页面转场/开屏倒计时动画期间，同一个控件在不同时刻的屏幕坐标会漂移。
     * 若把坐标算进身份，就会把「动画中的同一个按钮」误判成两个不同控件，
     * 从而在 1 秒内重复点击。因此当控件有 resource id 时，用 id 作为稳定身份；
     * 只有在没有 id（纯文本控件）时才退回坐标。
     */
    private String signature(String pkg, PlannedAction a) {
        StringBuilder sb = new StringBuilder(pkg).append('|').append(a.note).append('|');
        if (a.target != null) {
            sb.append(a.target.text).append('/').append(a.target.resId);
            if (a.target.resId == null || a.target.resId.isEmpty()) {
                sb.append('/').append(a.target.bounds[0]).append(',').append(a.target.bounds[1]);
            }
        } else if (a.coords != null) {
            sb.append(a.coords[0]).append(',').append(a.coords[1]);
        }
        return sb.toString();
    }

    private String triggerOf(PlannedAction a) {
        if (a.target != null) {
            if (a.target.text != null && !a.target.text.isEmpty()) return a.target.text;
            if (a.target.resId != null && !a.target.resId.isEmpty()) return a.target.resId;
        }
        return a.note;
    }

    private String targetOf(PlannedAction a) {
        if (a.type == hello.litiaotiao.app.engine.ActionType.BACK) return "GLOBAL_ACTION_BACK";
        if (a.coords != null) return a.coords[0] + "," + a.coords[1] + "," + a.coords[2] + "," + a.coords[3];
        if (a.target != null && a.target.resId != null && !a.target.resId.isEmpty()) return a.target.resId;
        return a.note;
    }

    /** 从 SharedPreferences 读取全局规则（对所有包生效）。 */
    private RuleSet buildGlobalRules() {
        String json = Prefs.getString(this, Prefs.K_GLOBAL_RULES, Prefs.DEFAULT_GLOBAL_RULES);
        try {
            return RuleSet.fromJson(0, new JSONObject(json));
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析小程序宿主包名列表（JSON 数组字符串）。 */
    private java.util.List<String> parseHosts(String json) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (json == null || json.trim().isEmpty()) return out;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.optString(i, "").trim();
                if (!s.isEmpty()) out.add(s);
            }
        } catch (Exception ignore) { }
        return out;
    }

    @Override
    public void onInterrupt() { }
}
