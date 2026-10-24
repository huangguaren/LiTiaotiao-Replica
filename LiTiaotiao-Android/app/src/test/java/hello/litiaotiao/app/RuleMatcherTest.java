package hello.litiaotiao.app;

import org.junit.Test;

import hello.litiaotiao.app.engine.NodeSnapshot;
import hello.litiaotiao.app.engine.RuleMatcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 规则匹配语义：+ 开头 / - 结尾 / = 完全 / 默认模糊包含 / &amp; 与。 */
public class RuleMatcherTest {

    private NodeSnapshot node(String text, String resId) {
        return NodeSnapshot.of(text, resId);
    }

    @Test public void fuzzyContainsMatches() {
        NodeSnapshot n = node("跳过广告 3s", "com.demo:id/btn_skip");
        assertTrue(RuleMatcher.matchCondition("跳过", n));
        assertTrue(RuleMatcher.matchCondition("广告", n));
        assertFalse(RuleMatcher.matchCondition("下载", n));
    }

    @Test public void prefixOperatorRequiresStart() {
        NodeSnapshot n = node("跳过广告", "com.demo:id/btn_skip");
        assertTrue(RuleMatcher.matchCondition("+跳过", n));
        assertFalse(RuleMatcher.matchCondition("+广告", n));
    }

    @Test public void suffixOperatorRequiresEnd() {
        NodeSnapshot n = node("跳过广告", "com.demo:id/btn_skip");
        assertTrue(RuleMatcher.matchCondition("-广告", n));
        assertFalse(RuleMatcher.matchCondition("-跳过", n));
    }

    @Test public void exactOperatorIsStrict() {
        NodeSnapshot n = node("跳过", "com.demo:id/btn_skip");
        assertTrue(RuleMatcher.matchCondition("=跳过", n));
        assertFalse(RuleMatcher.matchCondition("=跳过广告", n));
    }

    @Test public void andOperatorRequiresAllSubConditions() {
        NodeSnapshot n = node("跳过广告", "com.demo:id/btn_skip");
        assertTrue(RuleMatcher.matchCondition("跳过&广告", n));
        assertFalse(RuleMatcher.matchCondition("跳过&下载", n));
    }

    @Test public void resourceIdIsFallbackMatchTarget() {
        // text 为空时，应退而匹配 resource id
        NodeSnapshot n = node("", "com.demo:id/btn_close");
        assertTrue(RuleMatcher.matchCondition("btn_close", n));
        assertTrue(RuleMatcher.matchCondition("+com.demo:id", n));
    }

    @Test public void nullAndEmptyNeverMatch() {
        assertFalse(RuleMatcher.matchCondition(null, node("跳过", "id")));
        assertFalse(RuleMatcher.matchCondition("", node("跳过", "id")));
        assertFalse(RuleMatcher.matchCondition("跳过", null));
    }

    @Test public void boundsDetection() {
        assertTrue(RuleMatcher.isBounds("10,20,30,40"));
        assertTrue(RuleMatcher.isBounds(" 10 , 20 , 30 , 40 "));
        assertTrue(RuleMatcher.isBounds("-10,20,30,40"));
        assertTrue(RuleMatcher.isBounds("10,20,30,40,1"));   // 带 CheckBox 状态
        assertFalse(RuleMatcher.isBounds("跳过广告"));
        assertFalse(RuleMatcher.isBounds("10,20,30"));
        assertFalse(RuleMatcher.isBounds("10,20,30,40,2"));  // 状态位只能 0/1
        assertFalse(RuleMatcher.isBounds(null));
    }

    @Test public void boundsParsing() {
        int[] b = RuleMatcher.parseBounds("10,20,30,40");
        assertEquals(10, b[0]);
        assertEquals(20, b[1]);
        assertEquals(30, b[2]);
        assertEquals(40, b[3]);
        int[] neg = RuleMatcher.parseBounds("-5,0,100,200");
        assertEquals(-5, neg[0]);
    }
}
