package hello.litiaotiao.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import hello.litiaotiao.app.engine.RuleSet;
import hello.litiaotiao.app.io.RuleJson;
import hello.litiaotiao.app.util.PackageIndex;
import hello.litiaotiao.app.util.Prefs;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 数据访问层：应用 / 规则 / 参数 / 日志 / 导入导出。 */
public class Repository {
    private final DbHelper db;
    private final Context ctx;

    public static class AppRow {
        public int hash;
        public String packageName, displayName;
        public boolean enabled, whitelisted;
        public int ruleCount;
    }

    public static class RuleRow {
        public int id;
        public String ruleType, matchId, action;
        public int times;
    }

    public static class AppDetail {
        public int hash;
        public String packageName, displayName, appName;
        public boolean enabled, whitelisted;
        public List<RuleRow> rules = new ArrayList<>();
        public JSONObject params = new JSONObject();
    }

    public static class ImportResult {
        public int importedApps, importedRules;
        public List<String> errors = new ArrayList<>();
    }

    public Repository(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.db = new DbHelper(this.ctx);
    }

    private String now() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", java.util.Locale.US)
                .format(new java.util.Date());
    }

    // ---------- apps ----------
    public List<AppRow> listApps(String q, int filter, int limit, int offset) {
        SQLiteDatabase d = db.getReadableDatabase();
        StringBuilder where = new StringBuilder();
        List<String> args = new ArrayList<>();
        if (q != null && !q.trim().isEmpty()) {
            where.append("(package LIKE ? OR display_name LIKE ? OR CAST(hash AS TEXT)=?)");
            args.add("%" + q.trim() + "%");
            args.add("%" + q.trim() + "%");
            args.add(q.trim());
        }
        if (filter == 1) { if (where.length() > 0) where.append(" AND "); where.append("enabled=1"); }
        else if (filter == 2) { if (where.length() > 0) where.append(" AND "); where.append("enabled=0"); }
        else if (filter == 3) { if (where.length() > 0) where.append(" AND "); where.append("whitelisted=1"); }

        List<AppRow> out = new ArrayList<>();
        String sql = "SELECT a.hash, a.package, a.display_name, a.enabled, a.whitelisted, "
                + "(SELECT COUNT(*) FROM rules r WHERE r.app_hash=a.hash) AS rc FROM apps a "
                + (where.length() > 0 ? "WHERE " + where + " " : "")
                + "ORDER BY a.display_name COLLATE NOCASE LIMIT ? OFFSET ?";
        args.add(String.valueOf(limit));
        args.add(String.valueOf(offset));
        try (Cursor c = d.rawQuery(sql, toArray(args))) {
            while (c.moveToNext()) {
                AppRow a = new AppRow();
                a.hash = c.getInt(0);
                a.packageName = c.getString(1);
                a.displayName = c.getString(2);
                a.enabled = c.getInt(3) == 1;
                a.whitelisted = c.getInt(4) == 1;
                a.ruleCount = c.getInt(5);
                out.add(a);
            }
        }
        return out;
    }

    public int countApps(String q, int filter) {
        SQLiteDatabase d = db.getReadableDatabase();
        StringBuilder where = new StringBuilder();
        List<String> args = new ArrayList<>();
        if (q != null && !q.trim().isEmpty()) {
            where.append("(package LIKE ? OR display_name LIKE ? OR CAST(hash AS TEXT)=?)");
            args.add("%" + q.trim() + "%"); args.add("%" + q.trim() + "%"); args.add(q.trim());
        }
        if (filter == 1) { if (where.length() > 0) where.append(" AND "); where.append("enabled=1"); }
        else if (filter == 2) { if (where.length() > 0) where.append(" AND "); where.append("enabled=0"); }
        else if (filter == 3) { if (where.length() > 0) where.append(" AND "); where.append("whitelisted=1"); }
        String sql = "SELECT COUNT(*) FROM apps " + (where.length() > 0 ? "WHERE " + where : "");
        try (Cursor c = d.rawQuery(sql, toArray(args))) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public AppDetail getApp(int hash) {
        SQLiteDatabase d = db.getReadableDatabase();
        AppDetail a = null;
        try (Cursor c = d.rawQuery("SELECT package, display_name, enabled, whitelisted FROM apps WHERE hash=?",
                new String[]{String.valueOf(hash)})) {
            if (c.moveToFirst()) {
                a = new AppDetail();
                a.hash = hash;
                a.packageName = c.getString(0);
                a.displayName = c.getString(1);
                a.enabled = c.getInt(2) == 1;
                a.whitelisted = c.getInt(3) == 1;
            }
        }
        if (a == null) return null;
        a.appName = PackageIndex.appNameOf(ctx, hash);
        if (a.packageName == null || a.packageName.isEmpty())
            a.packageName = PackageIndex.packageOf(ctx, hash);
        try (Cursor c = d.rawQuery("SELECT id, rule_type, match_id, action, times FROM rules WHERE app_hash=? ORDER BY sort_order, id",
                new String[]{String.valueOf(hash)})) {
            while (c.moveToNext()) {
                RuleRow r = new RuleRow();
                r.id = c.getInt(0); r.ruleType = c.getString(1);
                r.matchId = c.getString(2); r.action = c.getString(3); r.times = c.getInt(4);
                a.rules.add(r);
            }
        }
        try (Cursor c = d.rawQuery("SELECT key, value FROM app_params WHERE app_hash=?",
                new String[]{String.valueOf(hash)})) {
            while (c.moveToNext()) {
                try { a.params.put(c.getString(0), coerce(c.getString(1))); } catch (JSONException ignore) { }
            }
        }
        return a;
    }

    public void ensureApp(int hash, String source) {
        SQLiteDatabase d = db.getWritableDatabase();
        try (Cursor c = d.rawQuery("SELECT hash FROM apps WHERE hash=?", new String[]{String.valueOf(hash)})) {
            if (c.moveToFirst()) return;
        }
        String pkg = PackageIndex.packageOf(ctx, hash);
        String name = PackageIndex.appNameOf(ctx, hash);
        if (name == null || name.isEmpty()) name = (pkg != null && !pkg.isEmpty()) ? pkg : ("未知应用 " + hash);
        ContentValues v = new ContentValues();
        v.put("hash", hash);
        v.put("package", pkg);
        v.put("display_name", name);
        v.put("enabled", 1);
        v.put("whitelisted", 0);
        v.put("source", source);
        v.put("created_at", now());
        v.put("updated_at", now());
        d.insert(DbHelper.T_APPS, null, v);
    }

    public void setEnabled(int hash, boolean on) {
        ContentValues v = new ContentValues();
        v.put("enabled", on ? 1 : 0);
        v.put("updated_at", now());
        db.getWritableDatabase().update(DbHelper.T_APPS, v, "hash=?", new String[]{String.valueOf(hash)});
    }

    public void setWhitelisted(int hash, boolean on) {
        ContentValues v = new ContentValues();
        v.put("whitelisted", on ? 1 : 0);
        v.put("updated_at", now());
        db.getWritableDatabase().update(DbHelper.T_APPS, v, "hash=?", new String[]{String.valueOf(hash)});
    }

    public void deleteApp(int hash) {
        SQLiteDatabase d = db.getWritableDatabase();
        d.delete(DbHelper.T_RULES, "app_hash=?", new String[]{String.valueOf(hash)});
        d.delete(DbHelper.T_PARAMS, "app_hash=?", new String[]{String.valueOf(hash)});
        d.delete(DbHelper.T_APPS, "hash=?", new String[]{String.valueOf(hash)});
    }

    /** 由数据库记录构造引擎可用的 RuleSet（供无障碍服务调用）。 */
    public RuleSet buildRuleSet(AppDetail a) {
        if (a == null) return null;
        RuleSet rs = new RuleSet(a.hash);
        rs.packageName = a.packageName;
        for (RuleRow r : a.rules) {
            if ("keyword".equals(r.ruleType)) rs.keywords.add(r.matchId);
            else if ("keyword_append".equals(r.ruleType)) rs.keywordsAppend.add(r.matchId);
            else rs.popupRules.add(new hello.litiaotiao.app.engine.Rule(r.matchId, r.action, r.times));
        }
        if (a.params != null) {
            for (java.util.Iterator<String> it = a.params.keys(); it.hasNext(); ) {
                String k = it.next();
                rs.params.put(k, a.params.opt(k));
            }
        }
        return rs;
    }

    // ---------- rules ----------
    public int addRule(int hash, String type, String match, String action, int times) {
        SQLiteDatabase d = db.getWritableDatabase();
        int order = 0;
        try (Cursor c = d.rawQuery("SELECT COALESCE(MAX(sort_order),0)+1 FROM rules WHERE app_hash=?",
                new String[]{String.valueOf(hash)})) {
            if (c.moveToFirst()) order = c.getInt(0);
        }
        ContentValues v = new ContentValues();
        v.put("app_hash", hash);
        v.put("rule_type", type);
        v.put("match_id", match);
        v.put("action", (action == null || action.isEmpty()) ? match : action);
        v.put("times", times);
        v.put("sort_order", order);
        return (int) d.insert(DbHelper.T_RULES, null, v);
    }

    public void updateRule(int ruleId, String type, String match, String action, int times) {
        ContentValues v = new ContentValues();
        v.put("rule_type", type);
        v.put("match_id", match);
        v.put("action", (action == null || action.isEmpty()) ? match : action);
        v.put("times", times);
        db.getWritableDatabase().update(DbHelper.T_RULES, v, "id=?", new String[]{String.valueOf(ruleId)});
    }

    public void deleteRule(int ruleId) {
        db.getWritableDatabase().delete(DbHelper.T_RULES, "id=?", new String[]{String.valueOf(ruleId)});
    }

    public void setParam(int hash, String key, Object value) {
        ContentValues v = new ContentValues();
        v.put("app_hash", hash);
        v.put("key", key);
        v.put("value", String.valueOf(value));
        db.getWritableDatabase().insertWithOnConflict(DbHelper.T_PARAMS, null, v,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    // ---------- import / export ----------
    public ImportResult importRules(String text) {
        ImportResult res = new ImportResult();
        try {
            List<RuleJson.RuleRecord> records = RuleJson.parseAllRules(text);
            SQLiteDatabase d = db.getWritableDatabase();
            d.beginTransaction();
            try {
                for (RuleJson.RuleRecord rec : records) {
                    int h = rec.hash;
                    ensureApp(h, "import");
                    d.delete(DbHelper.T_RULES, "app_hash=?", new String[]{String.valueOf(h)});
                    d.delete(DbHelper.T_PARAMS, "app_hash=?", new String[]{String.valueOf(h)});
                    RuleSet rs = RuleSet.fromJson(h, rec.obj);
                    int order = 0;
                    for (String kw : rs.keywords) {
                        insertRule(d, h, "keyword", kw, kw, 1, order++);
                    }
                    for (String kw : rs.keywordsAppend) {
                        insertRule(d, h, "keyword_append", kw, kw, 1, order++);
                    }
                    for (hello.litiaotiao.app.engine.Rule r : rs.popupRules) {
                        insertRule(d, h, "popup", r.id, r.action, r.times, order++);
                    }
                    for (java.util.Map.Entry<String, Object> e : rs.params.entrySet()) {
                        ContentValues v = new ContentValues();
                        v.put("app_hash", h); v.put("key", e.getKey());
                        v.put("value", String.valueOf(e.getValue()));
                        d.insertWithOnConflict(DbHelper.T_PARAMS, null, v, SQLiteDatabase.CONFLICT_REPLACE);
                    }
                    res.importedApps++;
                    res.importedRules += rs.keywords.size() + rs.keywordsAppend.size() + rs.popupRules.size();
                }
                d.setTransactionSuccessful();
            } finally {
                d.endTransaction();
            }
        } catch (Exception e) {
            res.errors.add(e.getMessage() == null ? e.toString() : e.getMessage());
        }
        return res;
    }

    private void insertRule(SQLiteDatabase d, int h, String type, String match, String action, int times, int order) {
        ContentValues v = new ContentValues();
        v.put("app_hash", h); v.put("rule_type", type); v.put("match_id", match);
        v.put("action", action); v.put("times", times); v.put("sort_order", order);
        d.insert(DbHelper.T_RULES, null, v);
    }

    public String exportApps(String scope) {
        SQLiteDatabase d = db.getReadableDatabase();
        String sql;
        String[] sqlArgs = null;
        if (scope != null && scope.startsWith("single:")) {
            sql = "SELECT hash FROM apps WHERE hash=?";
            sqlArgs = new String[]{scope.substring(7)};
        } else if ("enabled".equals(scope)) {
            sql = "SELECT hash FROM apps WHERE enabled=1";
        } else {
            sql = "SELECT hash FROM apps";
        }
        List<RuleJson.RuleRecord> recs = new ArrayList<>();
        try (Cursor c = d.rawQuery(sql, sqlArgs)) {
            while (c.moveToNext()) {
                int h = c.getInt(0);
                JSONObject obj = buildRuleObject(h);
                if (obj == null || obj.length() == 0) continue;
                recs.add(new RuleJson.RuleRecord(h, obj));
            }
        }
        try {
            return RuleJson.buildExport(recs);
        } catch (JSONException e) {
            return "[]";
        }
    }

    private JSONObject buildRuleObject(int hash) {
        JSONObject o = new JSONObject();
        JSONArray kw = new JSONArray(), kwa = new JSONArray(), pr = new JSONArray();
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT rule_type, match_id, action, times FROM rules WHERE app_hash=? ORDER BY sort_order, id",
                new String[]{String.valueOf(hash)})) {
            while (c.moveToNext()) {
                String type = c.getString(0);
                if ("keyword".equals(type)) kw.put(c.getString(1));
                else if ("keyword_append".equals(type)) kwa.put(c.getString(1));
                else {
                    JSONObject r = new JSONObject();
                    r.put("id", c.getString(1));
                    r.put("action", c.getString(2));
                    r.put("times", c.getInt(3));
                    pr.put(r);
                }
            }
        } catch (JSONException ignore) { }
        try {
            if (kw.length() > 0) o.put("keywords", kw);
            if (kwa.length() > 0) o.put("keywords_append", kwa);
            if (pr.length() > 0) o.put("popup_rules", pr);
            try (Cursor c = db.getReadableDatabase().rawQuery("SELECT key, value FROM app_params WHERE app_hash=?",
                    new String[]{String.valueOf(hash)})) {
                while (c.moveToNext()) o.put(c.getString(0), coerce(c.getString(1)));
            }
        } catch (JSONException ignore) { }
        return o;
    }

    /** 把参数值还原成 JSON 类型（数字/布尔/字符串），以匹配原版规则文件。 */
    private Object coerce(String v) {
        if (v == null) return "";
        if (v.equalsIgnoreCase("true")) return Boolean.TRUE;
        if (v.equalsIgnoreCase("false")) return Boolean.FALSE;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException ignore) { }
        return v;
    }

    // ---------- logs / stats / seed ----------
    public void logSkip(int hash, String ruleType, String trigger, String kind, String target) {
        ContentValues v = new ContentValues();
        v.put("app_hash", hash);
        v.put("rule_type", ruleType);
        v.put("trigger_text", trigger);
        v.put("action_kind", kind);
        v.put("action_target", target);
        v.put("created_at", now());
        db.getWritableDatabase().insert(DbHelper.T_LOGS, null, v);
    }

    public int[] stats() {
        SQLiteDatabase d = db.getReadableDatabase();
        int apps = 0, enabled = 0, rules = 0, logs = 0;
        try (Cursor c = d.rawQuery("SELECT COUNT(*), COALESCE(SUM(enabled),0) FROM apps", null)) {
            if (c.moveToFirst()) { apps = c.getInt(0); enabled = c.getInt(1); }
        }
        try (Cursor c = d.rawQuery("SELECT COUNT(*) FROM rules", null)) {
            if (c.moveToFirst()) rules = c.getInt(0);
        }
        try (Cursor c = d.rawQuery("SELECT COUNT(*) FROM skip_logs", null)) {
            if (c.moveToFirst()) logs = c.getInt(0);
        }
        return new int[]{apps, enabled, rules, logs};
    }

    /** 首次启动从 assets/AllRules.json 导入社区规则（幂等）。 */
    public boolean seedFromAssets() {
        if (Prefs.isSeeded(ctx)) return false;
        String json = hello.litiaotiao.app.util.Assets.read(ctx, "AllRules.json");
        if (json == null) return false;
        importRules(json);
        Prefs.setSeeded(ctx);
        return true;
    }

    private static String[] toArray(List<String> l) {
        return l.toArray(new String[0]);
    }
}
