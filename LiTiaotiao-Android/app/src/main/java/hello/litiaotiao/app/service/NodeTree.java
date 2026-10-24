package hello.litiaotiao.app.service;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

import hello.litiaotiao.app.engine.NodeHandle;
import hello.litiaotiao.app.engine.NodeSnapshot;

/**
 * 把 Android 无障碍节点树转换为与框架无关的 {@link NodeSnapshot}。
 *
 * <p>所有 Android API 调用都收敛在本类里，引擎侧保持纯 Java（可单元测试）。
 */
public final class NodeTree {
    private NodeTree() {}

    public static NodeSnapshot build(AccessibilityNodeInfo node) {
        if (node == null) return null;
        String text = str(node.getText());
        String desc = str(node.getContentDescription());
        String primary = (text != null && !text.isEmpty()) ? text : desc;

        Rect r = new Rect();
        try { node.getBoundsInScreen(r); } catch (Exception ignore) { }

        Boolean checked = null;
        try { checked = node.isCheckable() ? node.isChecked() : null; } catch (Exception ignore) { }

        List<NodeSnapshot> kids = new ArrayList<>();
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo c = node.getChild(i);
            if (c == null) continue;
            NodeSnapshot s = build(c);
            if (s != null) kids.add(s);
        }

        final AccessibilityNodeInfo live = node;
        NodeHandle handle = new NodeHandle() {
            @Override public boolean click() {
                try { return live.performAction(AccessibilityNodeInfo.ACTION_CLICK); }
                catch (Exception e) { return false; }
            }
            @Override public void recycle() {
                try { live.recycle(); } catch (Exception ignore) { }
            }
        };

        return new NodeSnapshot(primary, desc, str(node.getViewIdResourceName()),
                new int[]{r.left, r.top, r.right, r.bottom},
                str(node.getClassName()), node.isClickable(), node.isVisibleToUser(),
                checked, kids, handle);
    }

    private static String str(CharSequence cs) {
        return cs == null ? null : cs.toString();
    }
}
