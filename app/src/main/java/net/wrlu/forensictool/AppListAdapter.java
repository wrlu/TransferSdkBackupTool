package net.wrlu.forensictool;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {

    private static final String PAYLOAD_PROGRESS = "progress";

    private final List<AppInfo> allApps = new ArrayList<>();
    private final List<AppInfo> filteredApps = new ArrayList<>();
    private OnItemClickListener onItemClickListener;
    private boolean showSystemApps = false;
    private String currentQuery = null;

    interface OnItemClickListener {
        void onItemClick(AppInfo appInfo);
    }

    void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    void setShowSystemApps(boolean show) {
        this.showSystemApps = show;
        applyFilter();
    }

    void setApps(List<AppInfo> apps) {
        allApps.clear();
        allApps.addAll(apps);
        applyFilter();
    }

    void filter(String query) {
        this.currentQuery = query;
        applyFilter();
    }

    private void applyFilter() {
        filteredApps.clear();
        String q = currentQuery != null ? currentQuery.toLowerCase() : null;
        for (AppInfo app : allApps) {
            if (!showSystemApps && app.systemApp) {
                continue;
            }
            if (q != null && !q.isEmpty()) {
                if (!app.appName.toLowerCase().contains(q)
                        && !app.packageName.toLowerCase().contains(q)) {
                    continue;
                }
            }
            filteredApps.add(app);
        }
        notifyDataSetChanged();
    }

    void updateProgress(String pkgName, int percent) {
        for (int i = 0; i < allApps.size(); i++) {
            if (allApps.get(i).packageName.equals(pkgName)) {
                allApps.get(i).progress = percent;
                allApps.get(i).completed = false;
                notifyItemChangedFiltered(pkgName, PAYLOAD_PROGRESS);
                return;
            }
        }
    }

    void resetProgress(String pkgName) {
        for (int i = 0; i < allApps.size(); i++) {
            if (allApps.get(i).packageName.equals(pkgName)) {
                allApps.get(i).progress = -1;
                allApps.get(i).completed = false;
                allApps.get(i).success = false;
                allApps.get(i).errorDesc = "";
                notifyItemChangedFiltered(pkgName, PAYLOAD_PROGRESS);
                return;
            }
        }
    }

    void updateCompleted(String pkgName, boolean success, String errorDesc) {
        for (int i = 0; i < allApps.size(); i++) {
            if (allApps.get(i).packageName.equals(pkgName)) {
                AppInfo app = allApps.get(i);
                app.completed = true;
                app.success = success;
                app.errorDesc = errorDesc != null ? errorDesc : "";
                app.progress = success ? 100 : app.progress;
                notifyItemChangedFiltered(pkgName, PAYLOAD_PROGRESS);
                return;
            }
        }
    }

    private void notifyItemChangedFiltered(String pkgName, String payload) {
        for (int i = 0; i < filteredApps.size(); i++) {
            if (filteredApps.get(i).packageName.equals(pkgName)) {
                notifyItemChanged(i, payload);
                return;
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            bindProgress(holder, filteredApps.get(position));
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppInfo app = filteredApps.get(position);
        holder.icon.setImageDrawable(app.icon);
        holder.name.setText(app.appName);
        holder.pkg.setText(app.packageName);
        bindProgress(holder, app);
        holder.itemView.setOnClickListener(v -> {
            if (onItemClickListener != null) {
                onItemClickListener.onItemClick(app);
            }
        });
    }

    private void bindProgress(ViewHolder holder, AppInfo app) {
        if (app.progress >= 0 && !app.completed) {
            holder.progressBar.setVisibility(View.VISIBLE);
            holder.progressBar.setProgress(app.progress);
            holder.progressText.setVisibility(View.VISIBLE);
            holder.progressText.setText(app.progress + "%");
        } else if (app.completed) {
            holder.progressBar.setVisibility(View.VISIBLE);
            holder.progressBar.setProgress(app.success ? 100 : app.progress);
            holder.progressText.setVisibility(View.VISIBLE);
            holder.progressText.setText(app.success ? "✓" : "✗ " + app.errorDesc);
        } else {
            holder.progressBar.setVisibility(View.GONE);
            holder.progressText.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return filteredApps.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;
        final TextView pkg;
        final ProgressBar progressBar;
        final TextView progressText;

        ViewHolder(View view) {
            super(view);
            icon = view.findViewById(R.id.app_icon);
            name = view.findViewById(R.id.app_name);
            pkg = view.findViewById(R.id.app_package);
            progressBar = view.findViewById(R.id.progress_bar);
            progressText = view.findViewById(R.id.progress_text);
        }
    }
}
