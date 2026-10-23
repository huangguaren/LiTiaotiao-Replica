package hello.litiaotiao.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import hello.litiaotiao.app.data.Repository;

/** 规则导入 / 导出（官方 AllRules.json 格式）。 */
public class ImportActivity extends AppCompatActivity {
    private static final String SAMPLE =
            "[{\"-330301876\":\"{\\\"popup_rules\\\":[{\\\"id\\\":\\\"native_ad_container\\\",\\\"action\\\":\\\"topon_btn_close\\\"},{\\\"id\\\":\\\"| 跳过\\\",\\\"action\\\":\\\"| 跳过\\\"}],\\\"click_way_popup\\\":1}\"},"
          + "{\"1994036591\":\"{\\\"popup_rules\\\":[{\\\"id\\\":\\\"青少年模式\\\",\\\"action\\\":\\\"我知道了\\\"}]}\"}]";

    private EditText editRules;
    private TextView textResult;
    private Repository repo;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_import);
        repo = new Repository(this);
        editRules = findViewById(R.id.editRules);
        textResult = findViewById(R.id.textResult);

        findViewById(R.id.btnLoadSample).setOnClickListener(v -> editRules.setText(SAMPLE));

        findViewById(R.id.btnDoImport).setOnClickListener(v -> {
            String content = editRules.getText().toString();
            if (content.trim().isEmpty()) { Toast.makeText(this, "请先粘贴规则内容", Toast.LENGTH_SHORT).show(); return; }
            new Thread(() -> {
                final Repository.ImportResult r = repo.importRules(content);
                runOnUiThread(() -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("导入完成：").append(r.importedApps).append(" 个应用，")
                      .append(r.importedRules).append(" 条规则。");
                    if (!r.errors.isEmpty()) {
                        sb.append("\n错误：\n");
                        for (String e : r.errors) sb.append("· ").append(e).append("\n");
                    }
                    textResult.setText(sb.toString());
                    Toast.makeText(this, r.errors.isEmpty() ? "导入成功" : "导入存在错误", Toast.LENGTH_SHORT).show();
                });
            }).start();
        });

        findViewById(R.id.btnExport).setOnClickListener(v -> {
            new Thread(() -> {
                final String json = repo.exportApps("all");
                runOnUiThread(() -> {
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("text/plain");
                    send.putExtra(Intent.EXTRA_TEXT, json);
                    send.putExtra(Intent.EXTRA_SUBJECT, "AllRules.json");
                    startActivity(Intent.createChooser(send, "导出规则"));
                });
            }).start();
        });
    }
}
