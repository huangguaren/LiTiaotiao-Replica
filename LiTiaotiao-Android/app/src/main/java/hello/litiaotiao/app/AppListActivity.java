package hello.litiaotiao.app;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import hello.litiaotiao.app.data.Repository;
import hello.litiaotiao.app.ui.AppListAdapter;

/** 应用管理：搜索、筛选、启用/禁用、白名单。 */
public class AppListActivity extends AppCompatActivity implements AppListAdapter.Listener {
    private static final int PAGE = 50;

    private Repository repo;
    private AppListAdapter adapter;
    private List<Repository.AppRow> rows = new ArrayList<>();

    private EditText editSearch;
    private ListView listApps;
    private ProgressBar progress;
    private TextView textEmpty;
    private Button chipAll, chipEnabled, chipDisabled, chipWhitelisted;

    private String query = "";
    private int filter = 0;      // 0 全部 1 启用 2 禁用 3 白名单
    private int offset = 0;
    private int total = 0;
    private boolean loading = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_app_list);
        repo = new Repository(this);

        editSearch = findViewById(R.id.editSearch);
        listApps = findViewById(R.id.listApps);
        progress = findViewById(R.id.progress);
        textEmpty = findViewById(R.id.textEmpty);
        chipAll = findViewById(R.id.chipAll);
        chipEnabled = findViewById(R.id.chipEnabled);
        chipDisabled = findViewById(R.id.chipDisabled);
        chipWhitelisted = findViewById(R.id.chipWhitelisted);

        adapter = new AppListAdapter(this, rows, this);
        listApps.setAdapter(adapter);

        editSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                query = s.toString();
                reload();
            }
        });

        View.OnClickListener chip = v -> {
            if (v == chipAll) filter = 0;
            else if (v == chipEnabled) filter = 1;
            else if (v == chipDisabled) filter = 2;
            else filter = 3;
            highlightChips();
            reload();
        };
        chipAll.setOnClickListener(chip);
        chipEnabled.setOnClickListener(chip);
        chipDisabled.setOnClickListener(chip);
        chipWhitelisted.setOnClickListener(chip);

        listApps.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView v, int s) {}
            @Override public void onScroll(AbsListView v, int first, int visible, int count) {
                if (!loading && rows.size() < total && first + visible >= count - 2) {
                    loadPage(false);
                }
            }
        });

        reload();
    }

    private void highlightChips() {
        chipAll.setAlpha(filter == 0 ? 1f : 0.5f);
        chipEnabled.setAlpha(filter == 1 ? 1f : 0.5f);
        chipDisabled.setAlpha(filter == 2 ? 1f : 0.5f);
        chipWhitelisted.setAlpha(filter == 3 ? 1f : 0.5f);
    }

    private void reload() {
        offset = 0;
        rows.clear();
        adapter.setData(rows);
        loadPage(true);
    }

    private void loadPage(boolean first) {
        loading = true;
        progress.setVisibility(first ? View.VISIBLE : View.GONE);
        final String q = query;
        final int f = filter;
        final int off = offset;
        new Thread(() -> {
            final List<Repository.AppRow> page = repo.listApps(q, f, PAGE, off);
            final int t = repo.countApps(q, f);
            runOnUiThread(() -> {
                total = t;
                rows.addAll(page);
                offset = rows.size();
                adapter.setData(rows);
                progress.setVisibility(View.GONE);
                textEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
                loading = false;
            });
        }).start();
    }

    @Override
    public void onToggle(Repository.AppRow row, boolean enabled) {
        repo.setEnabled(row.hash, enabled);
        row.enabled = enabled;
        adapter.notifyDataSetChanged();
        Toast.makeText(this, enabled ? "已启用" : "已禁用", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onOpen(Repository.AppRow row) {
        android.content.Intent it = new android.content.Intent(this, RuleDetailActivity.class);
        it.putExtra("hash", row.hash);
        startActivity(it);
    }
}
