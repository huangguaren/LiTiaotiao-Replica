package hello.litiaotiao.app;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import hello.litiaotiao.app.engine.ActionType;
import hello.litiaotiao.app.engine.NodeSnapshot;
import hello.litiaotiao.app.engine.PlannedAction;
import hello.litiaotiao.app.engine.RuleEngine;
import hello.litiaotiao.app.engine.RuleSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * 规则引擎端到端行为测试（纯 JVM，无需 Android 框架）。
 *
 * <p>覆盖：开屏关键词、弹窗规则、动作解析（返回键 / 坐标 / 控件）、
 * 点击次数、检测态（times=0）、联合规则闸门、青少年过滤、不可见节点过滤、
 * 全局规则叠加、以及小程序开屏广告的自动返回兜底。
 */
public class RuleEngineTest {

    // ---------- 节点构造辅助 ----------

    private static NodeSnapshot node(String text, String resId, boolean clickable, boolean visible) {
        return new NodeSnapshot(text, null, resId, new int[]{0, 0, 100, 100},
                "android.widget.Button", clickable, visible, null, null, null);
    }

    private static NodeSnapshot group(NodeSnapshot... kids) {
        return new NodeSnapshot(null, null, "android:id/content", new int[]{0, 0, 1080, 1920},
                "android.widget.FrameLayout", false, true, null, Arrays.asList(kids), null);
    }

    private static RuleSet rs(String json) throws Exception {
        return RuleSet.fromJson(-1, new JSONObject(json));
    }

    /** 默认：过滤不可见节点，非小程序宿主，不开自动返回，不过滤青少年。 */
    private static List<PlannedAction> eval(RuleSet app, NodeSnapshot root) {
        return RuleEngine.evaluate(app, null, root, true, null, null, false, false);
    }

    /** 典型的「开屏广告」界面：一个跳过按钮 + 一个正文。 */
    private static NodeSnapshot splashScreen() {
        return group(
                node("开屏广告 5s", "com.demo:id/ad_text", false, true),
                node("跳过广告", "com.demo:id/btn_skip", true, true));
    }

    /** 典型的「更新弹窗」界面：标题 + 两个按钮。 */
    private static NodeSnapshot dialogScreen() {
        return group(
                node("检测到更新", "android:id/alertTitle", false, true),
                node("发现有新版本，是否立即更新？", "android:id/message", false, true),
                node("立即更新", "android:id/button1", true, true),
                node("以后再说", "android:id/button2", true, true));
    }

    // ---------- 开屏关键词 ----------

    @Test public void keywordHitProducesNodeClick() throws Exception {
        RuleSet r = rs("{\"keywords\":[\"跳过\"]}");
        List<PlannedAction> out = eval(r, splashScreen());
        assertEquals(1, out.size());
        assertEquals(ActionType.NODE, out.get(0).type);
        assertEquals("keyword", out.get(0).ruleType);
        assertEquals("跳过广告", out.get(0).target.text);
        assertEquals("com.demo:id/btn_skip", out.get(0).target.resId);
    }

    @Test public void multipleKeywordsHittingSameNodeAreDeduplicated() throws Exception {
        // 「跳过」和「跳过广告」命中同一个按钮，只应产生一次点击（去重按命中控件，不按关键词）
        RuleSet r = rs("{\"keywords\":[\"跳过\",\"跳过广告\"]}");
        List<PlannedAction> out = eval(r, splashScreen());
        assertEquals(1, out.size());
        assertEquals("com.demo:id/btn_skip", out.get(0).target.resId);
    }

    @Test public void keywordsHittingDistinctNodesEachProduceAClick() throws Exception {
        // 去重只能合并「同一个控件」；命中不同控件时仍应各自点击
        RuleSet r = rs("{\"keywords\":[\"跳过\",\"开屏广告\"]}");
        List<PlannedAction> out = eval(r, splashScreen());
        assertEquals(2, out.size());
    }

    @Test public void noMatchProducesNoAction() throws Exception {
        RuleSet r = rs("{\"keywords\":[\"下载App\"]}");
        assertTrue(eval(r, splashScreen()).isEmpty());
    }

    // ---------- 弹窗规则 + 动作解析 ----------

    @Test public void popupRuleWithBackAction() throws Exception {
        RuleSet r = rs("{\"popup_rules\":[{\"id\":\"检测到更新\",\"action\":\"GLOBAL_ACTION_BACK\",\"times\":1}]}");
        List<PlannedAction> out = eval(r, dialogScreen());
        assertEquals(1, out.size());
        assertEquals(ActionType.BACK, out.get(0).type);
        assertEquals("popup", out.get(0).ruleType);
        assertNull(out.get(0).target);
    }

    @Test public void popupRuleWithBoundsAction() throws Exception {
        RuleSet r = rs("{\"popup_rules\":[{\"id\":\"检测到更新\",\"action\":\"900,1800,1000,1860\",\"times\":1}]}");
        List<PlannedAction> out = eval(r, dialogScreen());
        assertEquals(1, out.size());
        assertEquals(ActionType.BOUNDS, out.get(0).type);
        assertEquals(900, out.get(0).coords[0]);
        assertEquals(1860, out.get(0).coords[3]);
    }

    @Test public void popupRuleActionPointingAtTriggerItself() throws Exception {
        // action 与触发控件的 resource id 相同 → 直接点触发控件
        RuleSet r = rs("{\"popup_rules\":[{\"id\":\"检测到更新\",\"action\":\"android:id/alertTitle\",\"times\":1}]}");
        List<PlannedAction> out = eval(r, dialogScreen());
        assertEquals(1, out.size());
        assertEquals(ActionType.NODE, out.get(0).type);
        assertEquals("android:id/alertTitle", out.get(0).target.resId);
    }

    @Test public void popupRuleActionLocatesAnotherClickableNode() throws Exception {
        // 命中标题，动作指向「以后再说」按钮 —— 与设备上实测到的行为一致
        RuleSet r = rs("{\"popup_rules\":[{\"id\":\"检测到更新\",\"action\":\"以后再说\",\"times\":1}]}");
        List<PlannedAction> out = eval(r, dialogScreen());
        assertEquals(1, out.size());
        assertEquals(ActionType.NODE, out.get(0).type);
        assertEquals("以后再说", out.get(0).target.text);
        assertEquals("android:id/button2", out.get(0).target.resId);
    }

    @Test public void popupRuleTimesGreaterThanOneRepeatsClick() throws Exception {
        RuleSet r = rs("{\"popup_rules\":[{\"id\":\"检测到更新\",\"action\":\"以后再说\",\"times\":3}]}");
        List<PlannedAction> out = eval(r, dialogScreen());
        assertEquals(3, out.size());
        for (PlannedAction a : out) assertEquals("android:id/button2", a.target.resId);
    }

    @Test public void popupRuleTimesZeroIsDetectionOnly() throws Exception {
        RuleSet r = rs("{\"popup_rules\":[{\"id\":\"检测到更新\",\"action\":\"以后再说\",\"times\":0}]}");
        List<PlannedAction> out = eval(r, dialogScreen());
        assertEquals(1, out.size());
        assertEquals(ActionType.NONE, out.get(0).type); // 记录命中但不执行
        assertNotNull(out.get(0).target);
        assertEquals("检测到更新", out.get(0).target.text); // target 仍是触发控件
    }

    // ---------- 联合规则（连续任务闸门） ----------

    @Test public void uniteGateBlocksClicksWhenGateNotMatched() throws Exception {
        // 闸门规则「会员中心」不在屏幕上 → 说明任务还没到可点击阶段，点击规则必须等待
        RuleSet r = rs("{\"unite_popup_rules\":true,\"popup_rules\":["
                + "{\"id\":\"会员中心\",\"action\":\"以后再说\",\"times\":0},"
                + "{\"id\":\"检测到更新\",\"action\":\"以后再说\",\"times\":1}]}");
        assertTrue(eval(r, dialogScreen()).isEmpty());
    }

    @Test public void uniteGateAllowsClicksWhenGateMatched() throws Exception {
        // 闸门规则命中 → 放行点击规则。两条规则的 action 必须不同，
        // 否则会被「id|action」去重键误判为同一条规则而吞掉点击。
        RuleSet r = rs("{\"unite_popup_rules\":true,\"popup_rules\":["
                + "{\"id\":\"检测到更新\",\"action\":\"立即更新\",\"times\":0},"
                + "{\"id\":\"检测到更新\",\"action\":\"以后再说\",\"times\":1}]}");
        List<PlannedAction> out = eval(r, dialogScreen());
        assertEquals(2, out.size());
        assertEquals(ActionType.NONE, out.get(0).type);   // 检测态
        assertEquals(ActionType.NODE, out.get(1).type);   // 真正的点击
        assertEquals("android:id/button2", out.get(1).target.resId);
    }

    // ---------- 过滤 ----------

    @Test public void filterYoungSkipsTeenagerRules() throws Exception {
        RuleSet r = rs("{\"popup_rules\":[{\"id\":\"青少年模式提醒\",\"action\":\"以后再说\",\"times\":1}]}");
        NodeSnapshot screen = group(node("青少年模式提醒", "com.demo:id/teen", false, true),
                                    node("以后再说", "com.demo:id/later", true, true));
        assertTrue(RuleEngine.evaluate(r, null, screen, true, null, null, false, true).isEmpty());
        // 关闭该开关后应当放行
        assertEquals(1, RuleEngine.evaluate(r, null, screen, true, null, null, false, false).size());
    }

    @Test public void filterInvisibleSkipsHiddenNodes() throws Exception {
        RuleSet r = rs("{\"keywords\":[\"跳过\"]}");
        NodeSnapshot hidden = group(node("跳过广告", "com.demo:id/btn_skip", true, false));
        assertTrue(RuleEngine.evaluate(r, null, hidden, true, null, null, false, false).isEmpty());
        assertEquals(1, RuleEngine.evaluate(r, null, hidden, false, null, null, false, false).size());
    }

    // ---------- 找不到目标时的兜底 ----------

    @Test public void forceClickFallsBackToTriggerNode() throws Exception {
        RuleSet r = rs("{\"click_way_popup\":1,\"popup_rules\":["
                + "{\"id\":\"检测到更新\",\"action\":\"一个不存在的按钮\",\"times\":1}]}");
        List<PlannedAction> out = eval(r, dialogScreen());
        assertEquals(1, out.size());
        assertEquals(ActionType.NODE, out.get(0).type);
        assertEquals("android:id/alertTitle", out.get(0).target.resId); // 强制点触发控件
    }

    @Test public void withoutForceNoTargetMeansNoAction() throws Exception {
        RuleSet r = rs("{\"popup_rules\":[{\"id\":\"检测到更新\",\"action\":\"一个不存在的按钮\",\"times\":1}]}");
        List<PlannedAction> out = eval(r, dialogScreen());
        assertEquals(1, out.size());
        assertEquals(ActionType.NONE, out.get(0).type); // 只记录，不点击
    }

    // ---------- 全局规则 + 小程序兜底 ----------

    @Test public void globalAndAppRulesAreBothEvaluated() throws Exception {
        RuleSet global = rs("{\"keywords\":[\"关闭广告\"]}");
        RuleSet app = rs("{\"keywords\":[\"跳过\"]}");
        NodeSnapshot screen = group(node("跳过广告", "com.demo:id/btn_skip", true, true),
                                    node("关闭广告", "com.demo:id/btn_close", true, true));
        List<PlannedAction> out = RuleEngine.evaluate(app, global, screen, true, null, null, false, false);
        assertEquals(2, out.size());
        // 全局规则先执行，note 带 [全局] 标记
        assertTrue(out.get(0).note.contains("[全局]"));
        assertTrue(out.get(1).note.contains("开屏关键词命中"));
    }

    @Test public void globalRuleAloneCoversUnknownPackage() throws Exception {
        // 没有应用规则时，全局规则仍应生效（这是拦截小程序开屏广告的关键）
        RuleSet global = rs("{\"keywords\":[\"跳过广告\"]}");
        List<PlannedAction> out = RuleEngine.evaluate(null, global, splashScreen(),
                true, "com.some.unknown.app", null, false, false);
        assertEquals(1, out.size());
        assertEquals("com.demo:id/btn_skip", out.get(0).target.resId);
    }

    @Test public void miniProgramHostFallbackPressesBack() throws Exception {
        RuleSet global = rs("{\"keywords\":[\"跳过广告\"]}");
        List<String> hosts = Arrays.asList("com.tencent.mm");
        // 宿主包在前台，但屏幕上没有任何跳过按钮 → 自动返回
        NodeSnapshot adScreen = group(node("某小程序开屏", "com.demo:id/ad", false, true));
        List<PlannedAction> out = RuleEngine.evaluate(null, global, adScreen,
                true, "com.tencent.mm", hosts, true, false);
        assertEquals(1, out.size());
        assertEquals(ActionType.BACK, out.get(0).type);
        assertEquals("mini_program", out.get(0).ruleType);
    }

    @Test public void miniProgramFallbackSkippedWhenSkipButtonExists() throws Exception {
        RuleSet global = rs("{\"keywords\":[\"跳过广告\"]}");
        List<String> hosts = new ArrayList<>(Collections.singletonList("com.tencent.mm"));
        List<PlannedAction> out = RuleEngine.evaluate(null, global, splashScreen(),
                true, "com.tencent.mm", hosts, true, false);
        assertEquals(1, out.size());
        assertEquals(ActionType.NODE, out.get(0).type); // 优先点跳过按钮，不按返回
    }

    @Test public void miniProgramFallbackDisabledByDefault() throws Exception {
        RuleSet global = rs("{\"keywords\":[\"跳过广告\"]}");
        NodeSnapshot adScreen = group(node("某小程序开屏", "com.demo:id/ad", false, true));
        List<PlannedAction> out = RuleEngine.evaluate(null, global, adScreen,
                true, "com.tencent.mm", Arrays.asList("com.tencent.mm"), false, false);
        assertTrue(out.isEmpty()); // autoBack=false 时不兜底
    }

    // ---------- 遍历顺序 ----------

    @Test public void reverseTraversalFindsLastMatch() throws Exception {
        RuleSet r = rs("{\"keywords\":[\"关闭\"]}");
        NodeSnapshot screen = group(node("关闭", "com.demo:id/first", true, true),
                                    node("关闭", "com.demo:id/second", true, true));
        assertEquals("com.demo:id/first",
                RuleEngine.evaluate(r, null, screen, true, null, null, false, false).get(0).target.resId);

        RuleSet rev = rs("{\"keywords\":[\"关闭\"],\"reverse_traversal\":true}");
        assertEquals("com.demo:id/second",
                RuleEngine.evaluate(rev, null, screen, true, null, null, false, false).get(0).target.resId);
    }

    @Test public void nullRootProducesNoAction() throws Exception {
        assertTrue(eval(rs("{\"keywords\":[\"跳过\"]}"), null).isEmpty());
    }
}
