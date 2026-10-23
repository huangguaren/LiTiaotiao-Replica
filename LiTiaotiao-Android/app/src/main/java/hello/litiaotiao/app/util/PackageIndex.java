package hello.litiaotiao.app.util;

import android.content.Context;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ConcurrentHashMap;

/** hash ↔ 包名 ↔ 应用名 反向索引（来自 assets/package_index.json）。 */
public final class PackageIndex {
    private static final ConcurrentHashMap<String, String[]> BY_HASH = new ConcurrentHashMap<>();

    private PackageIndex() {}

    private static void ensure(Context ctx) {
        if (!BY_HASH.isEmpty()) return;
        String json = Assets.read(ctx, "package_index.json");
        if (json == null) return;
        try {
            JSONObject root = new JSONObject(json);
            JSONObject byHash = root.optJSONObject("by_hash");
            if (byHash == null) return;
            for (java.util.Iterator<String> it = byHash.keys(); it.hasNext(); ) {
                String h = it.next();
                JSONObject rec = byHash.optJSONObject(h);
                if (rec == null) continue;
                BY_HASH.put(h, new String[]{rec.optString("package", ""), rec.optString("app_name", "")});
            }
        } catch (JSONException ignore) { }
    }

    public static String packageOf(Context ctx, int hash) {
        ensure(ctx);
        String[] v = BY_HASH.get(String.valueOf(hash));
        return v != null && !TextUtils.isEmpty(v[0]) ? v[0] : "";
    }

    public static String appNameOf(Context ctx, int hash) {
        ensure(ctx);
        String[] v = BY_HASH.get(String.valueOf(hash));
        return v != null && !TextUtils.isEmpty(v[1]) ? v[1] : "";
    }
}
