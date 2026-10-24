package hello.litiaotiao.app.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** SQLite 建库/升级。表结构与 Web 工作台一致，保证数据可互通。 */
public class DbHelper extends SQLiteOpenHelper {
    public static final String DB_NAME = "litiaotiao.db";
    public static final int DB_VERSION = 2;

    public static final String T_APPS = "apps";
    public static final String T_RULES = "rules";
    public static final String T_PARAMS = "app_params";
    public static final String T_SETTINGS = "settings";
    public static final String T_LOGS = "skip_logs";

    public DbHelper(Context ctx) {
        super(ctx, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS apps ("
                + "hash INTEGER PRIMARY KEY,"
                + "package TEXT,"
                + "display_name TEXT,"
                + "enabled INTEGER NOT NULL DEFAULT 1,"
                + "whitelisted INTEGER NOT NULL DEFAULT 0,"
                + "source TEXT NOT NULL DEFAULT 'manual',"
                + "created_at TEXT NOT NULL,"
                + "updated_at TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS rules ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "app_hash INTEGER NOT NULL,"
                + "rule_type TEXT NOT NULL,"
                + "match_id TEXT NOT NULL,"
                + "action TEXT NOT NULL,"
                + "times INTEGER NOT NULL DEFAULT 1,"
                + "sort_order INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_rules_app ON rules(app_hash)");
        db.execSQL("CREATE TABLE IF NOT EXISTS app_params ("
                + "app_hash INTEGER NOT NULL,"
                + "key TEXT NOT NULL,"
                + "value TEXT,"
                + "PRIMARY KEY (app_hash, key))");
        db.execSQL("CREATE TABLE IF NOT EXISTS settings ("
                + "key TEXT PRIMARY KEY,"
                + "value TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS skip_logs ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "app_hash INTEGER,"
                + "rule_type TEXT,"
                + "trigger_text TEXT,"
                + "action_kind TEXT,"
                + "action_target TEXT,"
                + "package TEXT,"
                + "created_at TEXT NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if (oldV < 2) {
            // 2.x：skip_logs 增加 package 列（区分「哪个应用」与「规则类型」）
            try {
                db.execSQL("ALTER TABLE skip_logs ADD COLUMN package TEXT");
            } catch (Exception ignore) {
                // 列已存在时忽略
            }
        }
    }
}
