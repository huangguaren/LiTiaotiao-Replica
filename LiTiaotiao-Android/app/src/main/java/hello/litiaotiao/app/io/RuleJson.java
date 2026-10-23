package hello.litiaotiao.app.io;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 解析 / 重建李跳跳官方规则文件格式：
 *   [ {"<hash>": "{\"popup_rules\":[...],...}"}, ... ]
 * 兼容社区文件中偶发的尾随逗号（宽松解析）。
 */
public final class RuleJson {
    private static final Pattern TRAILING_COMMA = Pattern.compile(",\\s*([}\\]])");

    public static final class RuleRecord {
        public int hash;
        public JSONObject obj;
        public RuleRecord(int hash, JSONObject obj) { this.hash = hash; this.obj = obj; }
    }

    private RuleJson() {}

    /** 解析规则文本；格式错误抛 JSONException。 */
    public static List<RuleRecord> parseAllRules(String text) throws JSONException {
        // 预清洗尾随逗号：同时兼容外层数组与内层对象（社区文件偶发）。
        String t = text == null ? "" : TRAILING_COMMA.matcher(text.trim()).replaceAll("$1");
        List<RuleRecord> out = new ArrayList<>();
        if (t.isEmpty()) return out;
        if (t.charAt(0) == '[') {
            JSONArray arr = new JSONArray(t);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item == null) continue;
                collectFrom(item, out);
            }
        } else if (t.charAt(0) == '{') {
            collectFrom(new JSONObject(t), out);
        } else {
            throw new JSONException("不是合法的规则 JSON");
        }
        return out;
    }

    private static void collectFrom(JSONObject item, List<RuleRecord> out) throws JSONException {
        for (java.util.Iterator<String> it = item.keys(); it.hasNext(); ) {
            String h = it.next();
            String val = item.optString(h);
            JSONObject obj = lenientObject(val);
            if (obj == null) throw new JSONException("hash " + h + " 的规则内容不是合法 JSON");
            out.add(new RuleRecord(Integer.parseInt(h.trim()), obj));
        }
    }

    /** 宽松解析：先严格，失败则去掉尾随逗号再解析。 */
    public static JSONObject lenientObject(String text) {
        try {
            return new JSONObject(text);
        } catch (JSONException e) {
            try {
                return new JSONObject(TRAILING_COMMA.matcher(text).replaceAll("$1"));
            } catch (JSONException ignore) {
                return null;
            }
        }
    }

    /** 由 (hash, 内层规则对象) 重建官方格式字符串。 */
    public static String buildExport(List<RuleRecord> records) throws JSONException {
        JSONArray arr = new JSONArray();
        for (RuleRecord r : records) {
            if (r.obj == null) continue;
            String inner = r.obj.toString();
            JSONObject wrapper = new JSONObject();
            wrapper.put(String.valueOf(r.hash), inner);
            arr.put(wrapper);
        }
        return arr.toString(2);
    }
}
