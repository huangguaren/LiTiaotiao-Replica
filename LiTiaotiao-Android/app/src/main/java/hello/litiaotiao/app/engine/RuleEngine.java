package hello.litiaotiao.app.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 规则引擎：给定一个应用的规则集与当前屏幕的节点树快照，产出「将要执行的动作」列表。
 * 这是李跳跳无障碍点击逻辑的等价实现（已通过 Python 版单元测试验证语义）。
 */
public final class RuleEngine {
    private RuleEngine() {}

    public static List<PlannedAction> evaluate(RuleSet rs, NodeSnapshot root) {
        return evaluate(rs, null, root, false, null, null, false);
    }

    public static List<PlannedAction> evaluate(RuleSet rs, NodeSnapshot root, boolean filterInvisible) {
        return evaluate(rs, null, root, filterInvisible, null, null, false);
    }

    /**
     * 同时评估「全局规则」与「应用规则」，并支持小程序宿主自动返回。
     *
     * @param appRules       当前应用规则（可为 null，表示该包没有专属规则）
     * @param globalRules    全局规则（对所有包生效，用于小程序 / 异包广告）
     * @param packageName    当前前台包名（用于小程序宿主识别）
     * @param mpHosts        小程序宿主包名列表
     * @param mpAutoBack     找不到跳过按钮时是否自动返回
     */
    public static List<PlannedAction> evaluate(RuleSet appRules, RuleSet globalRules, NodeSnapshot root,
                                               boolean filterInvisible, String packageName,
                                               List<String> mpHosts, boolean mpAutoBack) {
        return evaluate(appRules, globalRules, root, filterInvisible, packageName, mpHosts, mpAutoBack, false);
    }

    public static List<PlannedAction> evaluate(RuleSet appRules, RuleSet globalRules, NodeSnapshot root,
                                               boolean filterInvisible, String packageName,
                                               List<String> mpHosts, boolean mpAutoBack, boolean filterYoung) {
        List<PlannedAction> out = new ArrayList<>();
        if (root == null) return out;
        Set<String> seenNodes = new HashSet<>();
        Set<String> seenRules = new HashSet<>();
        if (globalRules != null) run(globalRules, root, filterInvisible, out, seenNodes, seenRules, true, filterYoung);
        if (appRules != null) run(appRules, root, filterInvisible, out, seenNodes, seenRules, false, filterYoung);

        // 小程序开屏广告兜底：命中宿主包但没找到可点击跳过按钮时自动返回。
        if (out.isEmpty() && packageName != null && mpHosts != null
                && mpHosts.contains(packageName) && mpAutoBack) {
            out.add(new PlannedAction(ActionType.BACK, null, null,
                    "小程序开屏广告：未找到跳过按钮，自动返回"));
        }
        return out;
    }

    private static void run(RuleSet rs, NodeSnapshot root, boolean filterInvisible,
                            List<PlannedAction> out, Set<String> seenNodes, Set<String> seenRules,
                            boolean global, boolean filterYoung) {
        boolean force = rs.getParamBool("click_way_popup");
        boolean reverse = rs.getParamBool("reverse_traversal");
        int searchTimes = Math.max(rs.getParamInt("search_times_popup", 1), 1);
        String suffix = global ? " [全局]" : "";

        for (String kw : rs.keywords) {
            NodeSnapshot hit = findFirst(root, kw, reverse, filterInvisible);
            if (hit == null) continue;
            String nkey = "node|" + hit.text + "|" + hit.resId + "|"
                    + hit.bounds[0] + "," + hit.bounds[1];
            if (seenNodes.contains(nkey)) continue;
            seenNodes.add(nkey);
            out.add(new PlannedAction(ActionType.NODE, hit, null, "开屏关键词命中: " + kw + suffix));
        }

        // 联合规则：存在 times=0 的中间态规则但当前未命中时，等待、不执行点击规则。
        boolean unite = rs.getParamBool("unite_popup_rules");
        if (unite) {
            boolean hasGate = false;
            boolean gateMatched = false;
            for (Rule g : rs.popupRules) {
                if (g.times == 0 && g.id != null && !g.id.isEmpty()) {
                    hasGate = true;
                    if (findFirst(root, g.id, reverse, filterInvisible) != null) {
                        gateMatched = true;
                        break;
                    }
                }
            }
            if (hasGate && !gateMatched) return;
        }

        for (int pass = 0; pass < searchTimes; pass++) {
            for (Rule r : rs.popupRules) {
                if (r.id == null || r.id.isEmpty()) continue;
                if (filterYoung && r.id.contains("青少年")) continue;
                String key = r.id + "|" + r.action;
                if (seenRules.contains(key)) continue;
                NodeSnapshot trigger = findFirst(root, r.id, reverse, filterInvisible);
                if (trigger == null) continue;
                seenRules.add(key);
                if (r.times == 0) {
                    out.add(new PlannedAction(ActionType.NONE, trigger, null, "检测态命中 (times=0)"));
                    continue;
                }
                for (int i = 0; i < Math.max(r.times, 1); i++) {
                    out.add(resolveAction(r.action, trigger, root, force, filterInvisible));
                }
            }
        }
    }

    private static PlannedAction resolveAction(String action, NodeSnapshot trigger,
                                               NodeSnapshot root, boolean force, boolean filterInvisible) {
        if ("GLOBAL_ACTION_BACK".equals(action)) {
            return new PlannedAction(ActionType.BACK, null, null, "系统返回键");
        }
        if (RuleMatcher.isBounds(action)) {
            return new PlannedAction(ActionType.BOUNDS, null, RuleMatcher.parseBounds(action),
                    "点击坐标 " + action);
        }
        // action 指向命中的控件本身
        if (eq(action, trigger.text) || eq(action, trigger.desc) || eq(action, trigger.resId)) {
            return new PlannedAction(ActionType.NODE, trigger, null, "点击命中的控件本身");
        }
        // 否则定位目标控件（优先可点击）
        NodeSnapshot t = findFirstClickable(root, action, filterInvisible);
        if (t == null) t = findFirst(root, action, false, filterInvisible);
        if (t != null) {
            return new PlannedAction(ActionType.NODE, t, null, "点击匹配控件");
        }
        if (force) {
            return new PlannedAction(ActionType.NODE, trigger, null, "强制点击命中控件");
        }
        return new PlannedAction(ActionType.NONE, trigger, null, "未找到可点击目标");
    }

    private static boolean eq(String a, String b) {
        return a != null && a.equals(b);
    }

    public static NodeSnapshot findFirst(NodeSnapshot root, String cond, boolean reverse) {
        return findFirst(root, cond, reverse, false);
    }

    public static NodeSnapshot findFirst(NodeSnapshot root, String cond, boolean reverse, boolean filterInvisible) {
        if (root == null) return null;
        if ((!filterInvisible || root.visible) && RuleMatcher.matchCondition(cond, root)) return root;
        List<NodeSnapshot> kids = root.children;
        if (reverse) {
            for (int i = kids.size() - 1; i >= 0; i--) {
                NodeSnapshot r = findFirst(kids.get(i), cond, reverse, filterInvisible);
                if (r != null) return r;
            }
        } else {
            for (NodeSnapshot c : kids) {
                NodeSnapshot r = findFirst(c, cond, reverse, filterInvisible);
                if (r != null) return r;
            }
        }
        return null;
    }

    private static NodeSnapshot findFirstClickable(NodeSnapshot root, String cond, boolean filterInvisible) {
        if (root == null) return null;
        if ((!filterInvisible || root.visible) && root.clickable && RuleMatcher.matchCondition(cond, root)) return root;
        for (NodeSnapshot c : root.children) {
            NodeSnapshot r = findFirstClickable(c, cond, filterInvisible);
            if (r != null) return r;
        }
        return null;
    }
}
