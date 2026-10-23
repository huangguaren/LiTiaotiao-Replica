package hello.litiaotiao.app.engine;

/** 引擎产出的动作类型。 */
public enum ActionType {
    /** 点击某个控件节点 */
    NODE,
    /** 触发系统返回键 */
    BACK,
    /** 按坐标点击 */
    BOUNDS,
    /** 仅检测，不点击（unite_popup_rules 中间态） */
    NONE
}
