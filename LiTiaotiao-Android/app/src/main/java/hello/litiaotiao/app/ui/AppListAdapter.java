package hello.litiaotiao.app.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;

import hello.litiaotiao.app.R;
import hello.litiaotiao.app.data.Repository;

public class AppListAdapter extends BaseAdapter {
    public interface Listener {
        void onToggle(Repository.AppRow row, boolean enabled);
        void onOpen(Repository.AppRow row);
    }

    private final LayoutInflater inflater;
    private List<Repository.AppRow> data;
    private final Listener listener;

    public AppListAdapter(Context ctx, List<Repository.AppRow> data, Listener listener) {
        this.inflater = LayoutInflater.from(ctx);
        this.data = data;
        this.listener = listener;
    }

    public void setData(List<Repository.AppRow> data) {
        this.data = data;
        notifyDataSetChanged();
    }

    @Override public int getCount() { return data.size(); }
    @Override public Object getItem(int i) { return data.get(i); }
    @Override public long getItemId(int i) { return data.get(i).hash; }

    @Override
    public View getView(int i, View convertView, ViewGroup parent) {
        ViewHolder h;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_app, parent, false);
            h = new ViewHolder();
            h.icon = convertView.findViewById(R.id.appIcon);
            h.name = convertView.findViewById(R.id.appName);
            h.pkg = convertView.findViewById(R.id.appPkg);
            h.badge = convertView.findViewById(R.id.appBadge);
            h.sw = convertView.findViewById(R.id.switchEnabled);
            convertView.setTag(h);
        } else {
            h = (ViewHolder) convertView.getTag();
        }
        final Repository.AppRow row = data.get(i);
        String title = (row.displayName == null || row.displayName.isEmpty())
                ? (row.packageName == null ? "" : row.packageName) : row.displayName;
        h.icon.setText(title.isEmpty() ? "?" : title.substring(0, 1));
        h.name.setText(title);
        h.pkg.setText(row.packageName + " · " + row.ruleCount + " 条规则");
        h.badge.setVisibility(row.whitelisted ? View.VISIBLE : View.GONE);
        h.sw.setOnClickListener(null);
        h.sw.setChecked(row.enabled);
        h.sw.setOnClickListener(v -> listener.onToggle(row, h.sw.isChecked()));
        convertView.setOnClickListener(v -> listener.onOpen(row));
        return convertView;
    }

    static class ViewHolder {
        TextView icon, name, pkg, badge;
        Switch sw;
    }
}
