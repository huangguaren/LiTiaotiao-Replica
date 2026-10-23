package hello.litiaotiao.app.engine;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一个应用的完整规则集，对应官方规则文件里某个 hash 的内层 JSON：
 *   { "keywords":[...], "keywords_append":[...],
 *     "popup_rules":[{"id","action","times"}],
 *     "click_way_popup":1, "search_times_popup":5, "delay":200,
 *     "unite_popup_rules":true, "reverse_traversal":true, "ltt_service":true, ... }
 */
public final class RuleSet {
    public final int hash;
    public String packageName;
    public final List<String> keywords = new ArrayList<>();
    public final List<String> keywordsAppend = new ArrayList<>();
    public final List<Rule> popupRules = new ArrayList<>();
    public final Map<String, Object> params = new LinkedHashMap<>();

    public RuleSet(int hash) {
        this.hash = hash;
    }

    public static RuleSet fromJson(int hash, JSONObject obj) {
        RuleSet rs = new RuleSet(hash);
        JSONArray kw = obj.optJSONArray("keywords");
        if (kw != null) for (int i = 0; i < kw.length(); i++) rs.keywords.add(kw.optString(i));
        JSONArray kwa = obj.optJSONArray("keywords_append");
        if (kwa != null) for (int i = 0; i < kwa.length(); i++) rs.keywordsAppend.add(kwa.optString(i));
        JSONArray pr = obj.optJSONArray("popup_rules");
        if (pr != null) for (int i = 0; i < pr.length(); i++) {
            JSONObject r = pr.optJSONObject(i);
            if (r != null) rs.popupRules.add(Rule.fromJson(r));
        }
        for (java.util.Iterator<String> it = obj.keys(); it.hasNext(); ) {
            String k = it.next();
            if (k.equals("keywords") || k.equals("keywords_append") || k.equals("popup_rules"))
                continue;
            rs.params.put(k, obj.opt(k));
        }
        return rs;
    }

    public JSONObject toJsonObject() throws JSONException {
        JSONObject o = new JSONObject();
        if (!keywords.isEmpty()) o.put("keywords", new JSONArray(keywords));
        if (!keywordsAppend.isEmpty()) o.put("keywords_append", new JSONArray(keywordsAppend));
        if (!popupRules.isEmpty()) {
            JSONArray arr = new JSONArray();
            for (Rule r : popupRules) arr.put(r.toJson());
            o.put("popup_rules", arr);
        }
        for (Map.Entry<String, Object> e : params.entrySet()) {
            try { o.put(e.getKey(), e.getValue()); } catch (JSONException ignore) { }
        }
        return o;
    }

    public boolean hasAnyRule() {
        return !keywords.isEmpty() || !keywordsAppend.isEmpty() || !popupRules.isEmpty();
    }

    public boolean getParamBool(String key) {
        Object v = params.get(key);
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).intValue() != 0;
        if (v instanceof String) {
            String s = ((String) v).trim();
            return s.equals("1") || s.equalsIgnoreCase("true");
        }
        return false;
    }

    public int getParamInt(String key, int def) {
        Object v = params.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try { return Integer.parseInt(((String) v).trim()); } catch (NumberFormatException ignore) { }
        }
        return def;
    }
}
