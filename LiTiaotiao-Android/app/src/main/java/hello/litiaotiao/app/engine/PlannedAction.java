package hello.litiaotiao.app.engine;

/** 引擎对某个规则求解出的「将要执行的动作」。 */
public final class PlannedAction {
    public final ActionType type;
    public final NodeSnapshot target; // NODE 时的目标控件（含 live source 引用）
    public final int[] coords;        // BOUNDS 时的 {left,top,right,bottom}
    public final String note;

    public PlannedAction(ActionType type, NodeSnapshot target, int[] coords, String note) {
        this.type = type;
        this.target = target;
        this.coords = coords;
        this.note = note;
    }
}
