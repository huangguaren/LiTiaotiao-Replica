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
    private static final long DEDUPE_MS = 1500;       // 同一控件防重复点击窗口

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

        String pkg = resolvePackage(event);
        if (pkg == null || pkg.isEmpty()) return;
        if (pkg.equals(getPackageName())) return; // 忽略自身界面

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
            if (!hasApp && !hasGlobal) return;

            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;
            NodeSnapshot snapshot = NodeSnapshot.build(root);
            if (snapshot == null) return;

            boolean filterInvisible = Prefs.getBool(this, Prefs.K_FILTER_INVISIBLE, true);
            java.util.List<String> mpHosts = parseHosts(Prefs.getString(this, Prefs.K_MP_HOSTS, ""));
            boolean mpAutoBack = Prefs.getBool(this, Prefs.K_MP_AUTO_BACK, false);

            List<PlannedAction> actions = RuleEngine.evaluate(
                    hasApp ? appRules : null, globalRules, snapshot, filterInvisible,
                    pkg, mpHosts, mpAutoBack);
            if (actions.isEmpty()) return;

            int performed = 0;
            for (PlannedAction a : actions) {
                String sig = signature(pkg, a);
                Long last = lastClickSig.get(sig);
                if (last != null && now - last < DEDUPE_MS) continue;
                lastClickSig.put(sig, now);

                boolean ok;
                try {
                    ok = perform(a);
                } catch (Exception ignore) {
                    ok = false;
                }
                if (ok) {
                    performed++;
                    repo.logSkip(hash, pkg, triggerOf(a), a.type.name(), targetOf(a));
                }
            }
            if (performed > 0) {
                Prefs.addSkip(this, performed);
            }
            if (lastClickSig.size() > 200) lastClickSig.clear();
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
                if (a.target != null && a.target.source != null) {
                    boolean r = a.target.source.performAction(AccessibilityNodeInfo.ACTION_CLICK);
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

    private String signature(String pkg, PlannedAction a) {
        StringBuilder sb = new StringBuilder(pkg).append('|').append(a.note).append('|');
        if (a.target != null) {
            sb.append(a.target.text).append('/').append(a.target.resId).append('/')
              .append(a.target.bounds[0]).append(',').append(a.target.bounds[1]);
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
