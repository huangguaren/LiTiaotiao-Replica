package hello.litiaotiao.app.engine;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 无障碍节点树的不可变快照。把实时 {@link AccessibilityNodeInfo} 拍成一份普通对象，
 * 让规则引擎与系统框架解耦（便于测试），同时在 {@link #source} 里保留原节点引用，
 * 以便服务端执行点击时调用 performAction。
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
    public transient AccessibilityNodeInfo source; // 执行操作用，不参与序列化

    private NodeSnapshot(String text, String desc, String resId, int[] bounds,
                         String className, boolean clickable, boolean visible,
                         Boolean checked, List<NodeSnapshot> children) {
        this.text = text;
        this.desc = desc;
        this.resId = resId;
        this.bounds = bounds;
        this.className = className;
        this.clickable = clickable;
        this.visible = visible;
        this.checked = checked;
        this.children = children;
    }

    public static NodeSnapshot build(AccessibilityNodeInfo node) {
        if (node == null) return null;
        String text = charSeqToString(node.getText());
        String desc = charSeqToString(node.getContentDescription());
        String primary = (text != null && !text.isEmpty()) ? text : desc;
        Rect r = new Rect();
        try { node.getBoundsInScreen(r); } catch (Exception ignore) { }
        Boolean checked = null;
        try { checked = node.isCheckable() ? node.isChecked() : null; } catch (Exception ignore) { }
        List<NodeSnapshot> kids = new ArrayList<>();
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo c = node.getChild(i);
            if (c != null) {
                NodeSnapshot s = build(c);
                if (s != null) kids.add(s);
            }
        }
        NodeSnapshot snap = new NodeSnapshot(
                primary, desc, str(node.getViewIdResourceName()),
                new int[]{r.left, r.top, r.right, r.bottom},
                str(node.getClassName()), node.isClickable(), node.isVisibleToUser(),
                checked, kids);
        snap.source = node;
        return snap;
    }

    private static String charSeqToString(CharSequence cs) {
        return cs == null ? null : cs.toString();
    }

    /** 回收整棵快照树对应的系统节点，避免长期运行时的节点泄漏。 */
    public void recycleTree() {
        if (source != null) {
            try { source.recycle(); } catch (Exception ignore) { }
        }
        for (NodeSnapshot c : children) c.recycleTree();
    }

    private static String str(CharSequence cs) {
        return cs == null ? "" : cs.toString();
    }
}
