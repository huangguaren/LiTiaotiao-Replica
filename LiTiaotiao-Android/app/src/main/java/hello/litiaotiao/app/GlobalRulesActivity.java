package hello.litiaotiao.app;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import hello.litiaotiao.app.util.Prefs;

/** 全局规则 + 小程序宿主配置（对所有应用生效）。 */
public class GlobalRulesActivity extends AppCompatActivity {
    private Switch swEnabled, swAutoBack;
    private EditText editKeywords, editPopup, editHosts;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_global_rules);
        swEnabled = findViewById(R.id.switchGlobalEnabled);
        swAutoBack = findViewById(R.id.switchMpAutoBack);
        editKeywords = findViewById(R.id.editGlobalKeywords);
        editPopup = findViewById(R.id.editGlobalPopup);
        editHosts = findViewById(R.id.editMpHosts);

        swEnabled.setChecked(Prefs.getBool(this, Prefs.K_GLOBAL_ENABLED, true));
        swAutoBack.setChecked(Prefs.getBool(this, Prefs.K_MP_AUTO_BACK, false));

        String rulesJson = Prefs.getString(this, Prefs.K_GLOBAL_RULES, Prefs.DEFAULT_GLOBAL_RULES);
        StringBuilder kw = new StringBuilder();
        StringBuilder pop = new StringBuilder();
        try {
            JSONObject obj = new JSONObject(rulesJson);
            JSONArray kws = obj.optJSONArray("keywords");
            if (kws != null) {
                for (int i = 0; i < kws.length(); i++) {
                    if (kw.length() > 0) kw.append("\n");
                    kw.append(kws.optString(i));
                }
            }
            JSONArray prs = obj.optJSONArray("popup_rules");
            if (prs != null) {
                for (int i = 0; i < prs.length(); i++) {
                    JSONObject r = prs.optJSONObject(i);
                    if (r == null) continue;
                    if (pop.length() > 0) pop.append("\n");
                    pop.append(r.optString("id")).append(" | ")
                       .append(r.optString("action", r.optString("id")));
                }
            }
        } catch (Exception ignore) { }
        editKeywords.setText(kw.toString());
        editPopup.setText(pop.toString());

        StringBuilder hosts = new StringBuilder();
        try {
            JSONArray arr = new JSONArray(Prefs.getString(this, Prefs.K_MP_HOSTS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                if (hosts.length() > 0) hosts.append("\n");
                hosts.append(arr.optString(i));
            }
        } catch (Exception ignore) { }
        editHosts.setText(hosts.toString());

        findViewById(R.id.btnSaveGlobal).setOnClickListener(v -> save());
    }

    private void save() {
        try {
            JSONArray kws = new JSONArray();
            for (String line : editKeywords.getText().toString().split("\n")) {
                String s = line.trim();
                if (!s.isEmpty()) kws.put(s);
            }
            JSONArray prs = new JSONArray();
            for (String line : editPopup.getText().toString().split("\n")) {
                String s = line.trim();
                if (s.isEmpty()) continue;
                int sep = s.indexOf('|');
                String id = (sep >= 0 ? s.substring(0, sep) : s).trim();
                if (id.isEmpty()) continue;
                String action = sep >= 0 ? s.substring(sep + 1).trim() : id;
                JSONObject r = new JSONObject();
                r.put("id", id);
                r.put("action", action.isEmpty() ? id : action);
                r.put("times", 1);
                prs.put(r);
            }
            JSONObject rules = new JSONObject();
            rules.put("keywords", kws);
            rules.put("popup_rules", prs);

            JSONArray hosts = new JSONArray();
            for (String line : editHosts.getText().toString().split("\n")) {
                String s = line.trim();
                if (!s.isEmpty()) hosts.put(s);
            }

            Prefs.putBool(this, Prefs.K_GLOBAL_ENABLED, swEnabled.isChecked());
            Prefs.putBool(this, Prefs.K_MP_AUTO_BACK, swAutoBack.isChecked());
            Prefs.putString(this, Prefs.K_GLOBAL_RULES, rules.toString());
            Prefs.putString(this, Prefs.K_MP_HOSTS, hosts.toString());
            Toast.makeText(this, "全局规则已保存", Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
