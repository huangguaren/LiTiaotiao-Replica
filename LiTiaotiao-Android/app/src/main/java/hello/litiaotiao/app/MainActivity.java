package hello.litiaotiao.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import hello.litiaotiao.app.data.Repository;
import hello.litiaotiao.app.service.LttAccessibilityService;
import hello.litiaotiao.app.util.Prefs;

/** 首页：服务开关、跳过计数、功能入口。 */
public class MainActivity extends AppCompatActivity {
    private Switch switchService;
    private TextView textServiceStatus;
    private TextView textSkipCount;
    private Repository repo;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        repo = new Repository(this);
        seedIfNeeded();

        switchService = findViewById(R.id.switchService);
        textServiceStatus = findViewById(R.id.textServiceStatus);
        textSkipCount = findViewById(R.id.textSkipCount);

        switchService.setOnClickListener(v -> openAccessibilitySettings());
        findViewById(R.id.btnOpenAccessibility).setOnClickListener(v -> openAccessibilitySettings());

        Button btnApps = findViewById(R.id.btnApps);
        Button btnImport = findViewById(R.id.btnImport);
        Button btnGlobal = findViewById(R.id.btnGlobal);
        Button btnSettings = findViewById(R.id.btnSettings);
        Button btnAbout = findViewById(R.id.btnAbout);
        btnApps.setOnClickListener(v -> startActivity(new Intent(this, AppListActivity.class)));
        btnImport.setOnClickListener(v -> startActivity(new Intent(this, ImportActivity.class)));
        btnGlobal.setOnClickListener(v -> startActivity(new Intent(this, GlobalRulesActivity.class)));
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        btnAbout.setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));
    }

    private void seedIfNeeded() {
        new Thread(() -> {
            boolean seeded = repo.seedFromAssets();
            if (seeded) runOnUiThread(() -> Toast.makeText(this, "已导入内置社区规则", Toast.LENGTH_SHORT).show());
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean on = isServiceEnabled();
        switchService.setChecked(on);
        textServiceStatus.setText(on ? R.string.service_on : R.string.service_off);
        textSkipCount.setText(String.valueOf(Prefs.getSkipCount(this)));
    }

    private boolean isServiceEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        String cls = LttAccessibilityService.class.getName();
        String full = getPackageName() + "/" + cls;
        String rel = getPackageName() + "/" + cls.substring(getPackageName().length());
        for (String entry : enabled.split(":")) {
            String e = entry.trim();
            if (e.equalsIgnoreCase(full) || e.equalsIgnoreCase(rel)) return true;
        }
        return false;
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            Toast.makeText(this, "请在列表中找到并启用「李跳跳」", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "无法打开无障碍设置", Toast.LENGTH_SHORT).show();
        }
    }
}
