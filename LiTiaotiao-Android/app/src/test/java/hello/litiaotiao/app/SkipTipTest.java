package hello.litiaotiao.app.service;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** SkipTip 的纯文本格式化逻辑（无 Android 依赖，可在 JVM 上直接验证）。 */
public class SkipTipTest {
    @Test
    public void formatTipZero() {
        assertEquals("已为你跳过 0 次", SkipTip.formatTip(0));
    }

    @Test
    public void formatTipSingle() {
        assertEquals("已为你跳过 1 次", SkipTip.formatTip(1));
    }

    @Test
    public void formatTipLarge() {
        assertEquals("已为你跳过 10086 次", SkipTip.formatTip(10086));
    }
}
