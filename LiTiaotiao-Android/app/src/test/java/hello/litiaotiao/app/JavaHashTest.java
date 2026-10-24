package hello.litiaotiao.app;

import org.junit.Test;

import hello.litiaotiao.app.util.JavaHash;

import static org.junit.Assert.assertEquals;

/**
 * Java {@code String.hashCode()} 跨语言一致性。
 *
 * <p>规则文件的 key 就是包名的 Java hashCode，所以 Android 端与 Web 工作台（Python）
 * 必须对同一个包名算出完全相同的值，否则规则会挂到错误的应用上。
 * 下面的期望值由 Python 参考实现 {@code litiaotiao.hashutil.java_hashcode} 生成。
 */
public class JavaHashTest {

    @Test public void knownSamples() {
        assertEquals(0, JavaHash.hash(""));
        assertEquals(97, JavaHash.hash("a"));
        assertEquals(99162322, JavaHash.hash("hello"));
        assertEquals(1163316, JavaHash.hash("跳过"));
    }

    @Test public void realPackageNames() {
        assertEquals(-973170826, JavaHash.hash("com.tencent.mm"));
        assertEquals(-860300598, JavaHash.hash("com.tencent.android.qqdownloader"));
        // 测试专用 App，设备数据库里查到的 hash 就是它
        assertEquals(-326074944, JavaHash.hash("hello.litiaotiao.testapp"));
    }

    @Test public void matchesJdkStringHashCode() {
        String[] samples = {
                "", "a", "hello", "跳过", "com.tencent.mm",
                "com.tencent.android.qqdownloader", "hello.litiaotiao.testapp",
                "com.some.very.long.package.name.for.hash.testing"
        };
        for (String s : samples) {
            assertEquals("mismatch for: " + s, s.hashCode(), JavaHash.hash(s));
        }
    }
}
