package hello.litiaotiao.app;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import hello.litiaotiao.app.data.Repository;
import hello.litiaotiao.app.ui.RuleListAdapter;

/** 单个应用的规则详情：参数、弹窗/关键词规则的增删改。 */
public class RuleDetailActivity extends AppCompatActivity implements RuleListAdapter.Listener {
    private int hash;
    private Repository repo;
    private Repository.AppDetail app;

    private Switch switchEnabled, switchWhitelisted;
    private Switch paramClickWay, paramUnite, paramReverse, paramLtt;
    private EditText paramSearchTimes, paramDelay, paramDelayPopup;

    private ListView listPopup, listKeyword, listAppend;
    private final List<Repository.RuleRow> popup = new ArrayList<>();
    private final List<Repository.RuleRow> keyword = new ArrayList<>();
    private final List<Repository.RuleRow> append = new ArrayList<>();
    private RuleListAdapter adPopup, adKeyword, adAppend;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_rule_detail);
        hash = getIntent().getIntExtra("hash", 0);
        repo = new Repository(this);

        switchEnabled = findViewById(R.id.switchEnabled);
        switchWhitelisted = findViewById(R.id.switchWhitelisted);
        paramClickWay = findViewById(R.id.paramClickWay);
        paramUnite = findViewById(R.id.paramUnite);
        paramReverse = findViewById(R.id.paramReverse);
        paramLtt = findViewById(R.id.paramLtt);
        paramSearchTimes = findViewById(R.id.paramSearchTimes);
        paramDelay = findViewById(R.id.paramDelay);
        paramDelayPopup = findViewById(R.id.paramDelayPopup);
        listPopup = findViewById(R.id.listPopup);
        listKeyword = findViewById(R.id.listKeyword);
        listAppend = findViewById(R.id.listAppend);

        adPopup = new RuleListAdapter(this, popup, this);
        adKeyword = new RuleListAdapter(this, keyword, this);
        adAppend = new RuleListAdapter(this, append, this);
        listPopup.setAdapter(adPopup);
        listKeyword.setAdapter(adKeyword);
        listAppend.setAdapter(adAppend);

        switchEnabled.setOnCheckedChangeListener((v, c) -> { if (app != null) repo.setEnabled(hash, c); });
        switchWhitelisted.setOnCheckedChangeListener((v, c) -> { if (app != null) repo.setWhitelisted(hash, c); });

        bindSwitch(paramClickWay, "click_way_popup");
        bindSwitch(paramUnite, "unite_popup_rules");
        bindSwitch(paramReverse, "reverse_traversal");
        bindSwitch(paramLtt, "ltt_service");
        bindNumber(paramSearchTimes, "search_times_popup", 1);
        bindNumber(paramDelay, "delay", 0);
        bindNumber(paramDelayPopup, "delay_popup", 0);

        findViewById(R.id.btnAddRule).setOnClickListener(v -> showRuleDialog(null));

        load();
    }

    private void bindSwitch(Switch sw, String key) {
        sw.setOnCheckedChangeListener((v, c) -> { if (app != null) repo.setParam(hash, key, c ? "true" : "false"); });
    }

    private void bindNumber(EditText et, String key, int def) {
        et.setOnFocusChangeListener((v, f) -> {
            if (!f && app != null) {
                int val = parseInt(et.getText().toString(), def);
                repo.setParam(hash, key, val);
            }
        });
    }

    private void load() {
        new Thread(() -> {
            final Repository.AppDetail a = repo.getApp(hash);
            runOnUiThread(() -> {
                app = a;
                if (a == null) { Toast.makeText(this, "应用不存在", Toast.LENGTH_SHORT).show(); finish(); return; }
                String title = !TextUtils.isEmpty(a.appName) ? a.appName
                        : (!TextUtils.isEmpty(a.displayName) ? a.displayName : a.packageName);
                ((TextView) findViewById(R.id.detailAppName)).setText(title);
                ((TextView) findViewById(R.id.detailAppPkg)).setText(a.packageName + " · hash " + a.hash);

                switchEnabled.setChecked(a.enabled);
                switchWhitelisted.setChecked(a.whitelisted);

                JSONObject p = a.params;
                paramClickWay.setChecked(boolParam(p, "click_way_popup"));
                paramUnite.setChecked(boolParam(p, "unite_popup_rules"));
                paramReverse.setChecked(boolParam(p, "reverse_traversal"));
                paramLtt.setChecked(boolParam(p, "ltt_service"));
                paramSearchTimes.setText(String.valueOf(intParam(p, "search_times_popup", 1)));
                paramDelay.setText(String.valueOf(intParam(p, "delay", 0)));
                paramDelayPopup.setText(String.valueOf(intParam(p, "delay_popup", 0)));

                popup.clear(); keyword.clear(); append.clear();
                for (Repository.RuleRow r : a.rules) {
                    if ("keyword".equals(r.ruleType)) keyword.add(r);
                    else if ("keyword_append".equals(r.ruleType)) append.add(r);
                    else popup.add(r);
                }
                adPopup.notifyDataSetChanged();
                adKeyword.notifyDataSetChanged();
                adAppend.notifyDataSetChanged();
                setListHeight(listPopup);
                setListHeight(listKeyword);
                setListHeight(listAppend);
            });
        }).start();
    }

    /** ListView 位于 ScrollView 内需要手动撑开高度，否则只显示一行。 */
    private static void setListHeight(ListView lv) {
        if (lv == null || lv.getAdapter() == null) return;
        int total = 0;
        int count = lv.getAdapter().getCount();
        for (int i = 0; i < count; i++) {
            View item = lv.getAdapter().getView(i, null, lv);
            item.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            total += item.getMeasuredHeight();
        }
        ViewGroup.LayoutParams p = lv.getLayoutParams();
        p.height = total + (count > 0 ? 0 : 0);
        lv.setLayoutParams(p);
    }

    private boolean boolParam(JSONObject p, String key) {
        Object v = p.opt(key);
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).intValue() != 0;
        if (v instanceof String) {
            String s = ((String) v).trim();
            return s.equals("1") || s.equalsIgnoreCase("true");
        }
        return false;
    }

    private int intParam(JSONObject p, String key, int def) {
        Object v = p.opt(key);
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) return parseInt((String) v, def);
        return def;
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    // ---------- rule dialog ----------
    private void showRuleDialog(final Repository.RuleRow existing) {
        View view = getLayoutInflater().inflate(R.layout.dialog_rule_edit, null);
        final android.widget.Spinner spinner = view.findViewById(R.id.spinnerType);
        final EditText editMatch = view.findViewById(R.id.editMatch);
        final EditText editAction = view.findViewById(R.id.editAction);
        final EditText editTimes = view.findViewById(R.id.editTimes);

        final String[] types = {"popup", "keyword", "keyword_append"};
        ArrayAdapter<String> sp = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{"弹窗 popup_rules", "开屏关键词 keywords", "追加关键词 keywords_append"});
        sp.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(sp);

        if (existing != null) {
            int pos = existing.ruleType.equals("keyword") ? 1 : existing.ruleType.equals("keyword_append") ? 2 : 0;
            spinner.setSelection(pos);
            editMatch.setText(existing.matchId);
            editAction.setText(existing.action);
            editTimes.setText(String.valueOf(existing.times));
        }

        new AlertDialog.Builder(this)
                .setTitle(existing == null ? "新增规则" : "编辑规则")
                .setView(view)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    String type = types[spinner.getSelectedItemPosition()];
                    String match = editMatch.getText().toString().trim();
                    String action = editAction.getText().toString().trim();
                    int times = parseInt(editTimes.getText().toString(), 1);
                    if (match.isEmpty()) { Toast.makeText(this, "匹配 id 不能为空", Toast.LENGTH_SHORT).show(); return; }
                    new Thread(() -> {
                        if (existing == null) repo.addRule(hash, type, match, action, times);
                        else repo.updateRule(existing.id, type, match, action, times);
                        runOnUiThread(() -> { load(); Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show(); });
                    }).start();
                })
                .show();
    }

    @Override public void onEdit(Repository.RuleRow row) { showRuleDialog(row); }

    @Override public void onDelete(Repository.RuleRow row) {
        new AlertDialog.Builder(this)
                .setTitle("删除规则")
                .setMessage("确认删除「" + row.matchId + "」？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> new Thread(() -> {
                    repo.deleteRule(row.id);
                    runOnUiThread(this::load);
                }).start())
                .show();
    }
}
