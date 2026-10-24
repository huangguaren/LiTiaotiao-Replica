package hello.litiaotiao.testapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.Button;

/**
 * 测试专用 App：模拟「开屏广告 + 更新弹窗」，用于在真机上验证李跳跳的跳过效果。
 *  - 开屏广告区有一个「跳过广告」按钮（命中李跳跳的全局关键词）
 *  - 点击「打开更新弹窗」会弹出「检测到更新」对话框（命中注入的弹窗规则）
 */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        Button popup = findViewById(R.id.btnPopup);
        popup.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("检测到更新")
                .setMessage("发现有新版本，是否立即更新？")
                .setPositiveButton("立即更新", (d, w) -> {})
                .setNegativeButton("以后再说", (d, w) -> {})
                .show());
    }
}
