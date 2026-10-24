package hello.litiaotiao.app.service;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** SkipTip 的纯文本格式化逻辑（无 Android 依赖，可在 JVM 上直接验证）。 */
public class SkipTipTest {
    @Test
    public void formatTipZero() {
        assertEquals("小兔子乖乖", SkipTip.formatTip(0));
    }

    @Test
    public void formatTipSingle() {
        assertEquals("小兔子乖乖", SkipTip.formatTip(1));
    }

    @Test
    public void formatTipLarge() {
        assertEquals("小兔子乖乖", SkipTip.formatTip(10086));
    }
}
