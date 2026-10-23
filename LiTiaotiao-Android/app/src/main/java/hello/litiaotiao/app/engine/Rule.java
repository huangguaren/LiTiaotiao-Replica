package hello.litiaotiao.app.engine;

import org.json.JSONException;
import org.json.JSONObject;

/** 单条规则：触发器 id + 动作 action + 点击次数 times。 */
public final class Rule {
    public final String id;
    public final String action;
    public final int times;

    public Rule(String id, String action, int times) {
        this.id = id;
        this.action = (action == null || action.isEmpty()) ? id : action;
        this.times = times;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("action", action);
        o.put("times", times);
        return o;
    }

    public static Rule fromJson(JSONObject o) {
        String id = o.optString("id", "");
        String action = o.optString("action", id);
        int times = o.has("times") ? o.optInt("times", 1) : 1;
        return new Rule(id, action, times);
    }

    public String ruleType() {
        return "popup";
    }
}
