package hello.litiaotiao.app.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 无障碍节点树的不可变快照，<b>不含任何 Android 框架依赖</b>。
 *
 * <p>把实时 {@code AccessibilityNodeInfo} 拍成一份普通对象，让规则引擎与系统框架解耦；
 * 写操作（点击 / 回收）通过 {@link NodeHandle} 句柄回调到 Android 侧，
 * 因此本类可以在纯 JVM 环境下直接构造并参与单元测试。
 */
public final class NodeSnapshot {
    public final String text;      // getText() 优先，其次 contentDescription
    public final String desc;      // contentDescription
    public final String resId;     // viewIdResourceName
    public final int[] bounds;     // {left, top, right, bottom}
    public final String className;
    public final boolean clickable;
    public final boolean visible;
    public final Boolean checked;  // CheckBox 状态
    public final List<NodeSnapshot> children;
    /** 执行动作/回收用的句柄；纯 Java 测试环境下可为 null。 */
    public final NodeHandle handle;

    public NodeSnapshot(String text, String desc, String resId, int[] bounds,
                        String className, boolean clickable, boolean visible,
                        Boolean checked, List<NodeSnapshot> children, NodeHandle handle) {
        this.text = text;
        this.desc = desc;
        this.resId = resId;
        this.bounds = bounds;
        this.className = className;
        this.clickable = clickable;
        this.visible = visible;
        this.checked = checked;
        this.children = (children == null) ? Collections.emptyList()
                                           : Collections.unmodifiableList(new ArrayList<>(children));
        this.handle = handle;
    }

    /** 无子节点的便捷构造，测试用。 */
    public static NodeSnapshot of(String text, String resId) {
        return new NodeSnapshot(text, null, resId, new int[]{0, 0, 100, 100},
                "android.widget.Button", true, true, null, null, null);
    }

    /** 带子节点与可见性的便捷构造，测试用。 */
    public static NodeSnapshot of(String text, String resId, boolean visible,
                                  List<NodeSnapshot> children) {
        return new NodeSnapshot(text, null, resId, new int[]{0, 0, 100, 100},
                "android.widget.ViewGroup", false, visible, null, children, null);
    }

    /** 回收整棵快照树对应的系统节点，避免长期运行时的节点泄漏。 */
    public void recycleTree() {
        if (handle != null) {
            try { handle.recycle(); } catch (Exception ignore) { }
        }
        for (NodeSnapshot c : children) c.recycleTree();
    }
}
