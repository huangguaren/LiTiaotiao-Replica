package hello.litiaotiao.app.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

import hello.litiaotiao.app.R;
import hello.litiaotiao.app.data.Repository;

public class RuleListAdapter extends BaseAdapter {
    public interface Listener {
        void onEdit(Repository.RuleRow row);
        void onDelete(Repository.RuleRow row);
    }

    private final LayoutInflater inflater;
    private final List<Repository.RuleRow> data;
    private final Listener listener;

    public RuleListAdapter(Context ctx, List<Repository.RuleRow> data, Listener listener) {
        this.inflater = LayoutInflater.from(ctx);
        this.data = data;
        this.listener = listener;
    }

    @Override public int getCount() { return data.size(); }
    @Override public Object getItem(int i) { return data.get(i); }
    @Override public long getItemId(int i) { return data.get(i).id; }

    @Override
    public View getView(int i, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_rule, parent, false);
        }
        Repository.RuleRow r = data.get(i);
        TextView tag = convertView.findViewById(R.id.ruleTag);
        TextView match = convertView.findViewById(R.id.ruleMatch);
        TextView action = convertView.findViewById(R.id.ruleAction);
        TextView times = convertView.findViewById(R.id.ruleTimes);

        String typeLabel = "keyword".equals(r.ruleType) ? "关键词"
                : "keyword_append".equals(r.ruleType) ? "追加" : "弹窗";
        tag.setText(typeLabel);
        match.setText(r.matchId);
        action.setText(r.action);
        if (r.times != 1) {
            times.setVisibility(View.VISIBLE);
            times.setText("×" + r.times);
        } else {
            times.setVisibility(View.GONE);
        }
        convertView.findViewById(R.id.btnEditRule).setOnClickListener(v -> listener.onEdit(r));
        convertView.findViewById(R.id.btnDeleteRule).setOnClickListener(v -> listener.onDelete(r));
        return convertView;
    }
}
