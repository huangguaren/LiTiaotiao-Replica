package hello.litiaotiao.app;

import android.os.Bundle;
import android.widget.Switch;

import androidx.appcompat.app.AppCompatActivity;

import hello.litiaotiao.app.util.Prefs;

/** 全局设置，键名与原版一致，立即保存。 */
public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_settings);

        bind(R.id.switchMain, Prefs.K_MAIN, true);
        bind(R.id.switchHideToast, Prefs.K_HIDE_TOAST, false);
        bind(R.id.switchShowAll, Prefs.K_SHOW_ALL, true);
        bind(R.id.switchWhitelistAll, Prefs.K_WL_ALL, false);
        bind(R.id.switchFilterInvisible, Prefs.K_FILTER_INVISIBLE, true);
        bind(R.id.switchFilterYoung, Prefs.K_FILTER_YOUNG, true);
        bind(R.id.switchReverse, Prefs.K_REVERSE, false);
        bind(R.id.switchStrictDedupe, Prefs.K_STRICT_DEDUPE, true);

        findViewById(R.id.btnSaveSettings).setOnClickListener(v -> finish());
    }

    private void bind(int viewId, String key, boolean def) {
        Switch sw = findViewById(viewId);
        sw.setChecked(Prefs.getBool(this, key, def));
        sw.setOnCheckedChangeListener((v, c) -> Prefs.putBool(this, key, c));
    }
}
