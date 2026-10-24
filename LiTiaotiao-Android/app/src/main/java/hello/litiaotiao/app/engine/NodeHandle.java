package hello.litiaotiao.app.engine;

/**
 * 对系统无障碍节点的抽象句柄。
 *
 * <p>规则引擎只会「读」节点，但执行动作时需要「点击」真实控件，并在用完后回收节点。
 * 若直接持有 {@code AccessibilityNodeInfo}，引擎就无法脱离 Android 框架做单元测试。
 * 因此这里用句柄把「执行/回收」这两个写操作抽象出来，Android 侧提供实现，
 * 测试侧可以用内存假实现，从而让 {@link RuleEngine} 在纯 JVM 上被完整测试。
 */
public interface NodeHandle {
    /** 对该控件执行点击，返回是否成功。 */
    boolean click();

    /** 回收底层系统节点，避免节点泄漏。允许重复调用。 */
    void recycle();
}
