package net.wrlu.forensictool;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SearchView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;

import net.wrlu.forensictool.backup.BackupService;
import net.wrlu.forensictool.backup.PlatformSignatureChecker;
import net.wrlu.forensictool.backup.VendorRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private AppListAdapter adapter;
    private LinearLayout loading;
    private BroadcastReceiver acquireReceiver;
    private BroadcastReceiver progressReceiver;
    private BroadcastReceiver completedReceiver;
    private BroadcastReceiver acquireDeniedReceiver;
    private boolean compatible = true;
    private SwitchMaterial switchAcquire;
    private SwitchMaterial switchSystemApps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String vendor = VendorRegistry.detectVendorByBrand();
        if (vendor == null) {
            compatible = false;
            showIncompatibleDialog();
        } else {
            String serverPackage = VendorRegistry.getServerPackage(vendor);
            try {
                getPackageManager().getPackageInfo(serverPackage, 0);
            } catch (PackageManager.NameNotFoundException e) {
                compatible = false;
                showMissingAppDialog();
            }
            if (compatible && VendorRegistry.VIVO.equals(vendor) && !PlatformSignatureChecker.isPlatformSigned(this)) {
                compatible = false;
                showIncompatibleDialog();
            }
        }

        adapter = new AppListAdapter();
        adapter.setOnItemClickListener(this::showConfirmDialog);

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        loading = findViewById(R.id.loading);

        SearchView searchView = findViewById(R.id.search_view);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                adapter.filter(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.filter(newText);
                return true;
            }
        });

        switchAcquire = findViewById(R.id.switch_acquire);
        switchAcquire.setChecked(true);

        switchSystemApps = findViewById(R.id.switch_system_apps);
        switchSystemApps.setOnCheckedChangeListener((buttonView, isChecked) -> adapter.setShowSystemApps(isChecked));

        acquireReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String serverPackage = intent.getStringExtra(BackupService.EXTRA_SERVER_PACKAGE);
                launchAcquire(serverPackage);
            }
        };
        IntentFilter acquireFilter = new IntentFilter(BackupService.ACTION_ACQUIRE_REQUIRED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(acquireReceiver, acquireFilter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(acquireReceiver, acquireFilter);
        }

        progressReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String pkg = intent.getStringExtra(BackupService.EXTRA_PKG_NAME);
                int percent = intent.getIntExtra(BackupService.EXTRA_PERCENT, 0);
                adapter.updateProgress(pkg, percent);
            }
        };
        IntentFilter progressFilter = new IntentFilter(BackupService.ACTION_PROGRESS);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(progressReceiver, progressFilter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(progressReceiver, progressFilter);
        }

        completedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String pkg = intent.getStringExtra(BackupService.EXTRA_PKG_NAME);
                boolean success = intent.getBooleanExtra(BackupService.EXTRA_SUCCESS, false);
                String errorDesc = intent.getStringExtra(BackupService.EXTRA_ERROR_DESC);
                String filePath = intent.getStringExtra(BackupService.EXTRA_FILE_PATH);
                adapter.updateCompleted(pkg, success, errorDesc);
                showCompletedDialog(pkg, success, errorDesc, filePath);
            }
        };
        IntentFilter completedFilter = new IntentFilter(BackupService.ACTION_COMPLETED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(completedReceiver, completedFilter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(completedReceiver, completedFilter);
        }

        acquireDeniedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String pkg = intent.getStringExtra(BackupService.EXTRA_PKG_NAME);
                adapter.resetProgress(pkg);
                new AlertDialog.Builder(context)
                        .setTitle(R.string.acquire_denied_title)
                        .setMessage(R.string.acquire_denied_message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        };
        IntentFilter deniedFilter = new IntentFilter(BackupService.ACTION_ACQUIRE_DENIED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(acquireDeniedReceiver, deniedFilter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(acquireDeniedReceiver, deniedFilter);
        }

        loadAppList();
    }

    @Override
    protected void onDestroy() {
        if (acquireReceiver != null) unregisterReceiver(acquireReceiver);
        if (progressReceiver != null) unregisterReceiver(progressReceiver);
        if (completedReceiver != null) unregisterReceiver(completedReceiver);
        if (acquireDeniedReceiver != null) unregisterReceiver(acquireDeniedReceiver);
        super.onDestroy();
    }

    @SuppressLint("QueryPermissionsNeeded")
    private void loadAppList() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> installed = pm.getInstalledApplications(0);
            List<AppInfo> apps = new ArrayList<>();

            for (ApplicationInfo info : installed) {
                String pkgName = info.packageName;
                String appName = info.loadLabel(pm).toString();
                boolean systemApp = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                apps.add(new AppInfo(pkgName, appName, info.loadIcon(pm), systemApp));
            }

            Collections.sort(apps, (a, b) -> a.appName.compareToIgnoreCase(b.appName));
            runOnUiThread(() -> {
                adapter.setApps(apps);
                loading.setVisibility(View.GONE);
            });
        }).start();
    }

    private void showIncompatibleDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.incompatible_title)
                .setMessage(R.string.incompatible_message)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showMissingAppDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.missing_app_title)
                .setMessage(R.string.missing_app_message)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showConfirmDialog(AppInfo app) {
        if (app.progress >= 0 && !app.completed) {
            Toast.makeText(this, R.string.backing_up, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_backup_title)
                .setMessage(getString(R.string.confirm_backup_message, app.appName, app.packageName))
                .setPositiveButton(android.R.string.ok, (d, w) -> startBackup(app.packageName))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showCompletedDialog(String pkgName, boolean success, String errorDesc, String filePath) {
        if (success) {
            String msg = filePath != null && !filePath.isEmpty()
                    ? getString(R.string.backup_completed_message, filePath)
                    : getString(R.string.backup_completed_message, pkgName + ".tar");
            new AlertDialog.Builder(this)
                    .setTitle(R.string.backup_completed_title)
                    .setMessage(msg)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.backup_failed_title)
                    .setMessage(getString(R.string.backup_failed_message, pkgName, errorDesc))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }
    }

    private void startBackup(String targetPackage) {
        Intent intent = new Intent(this, BackupService.class);
        intent.putExtra(BackupService.EXTRA_PKG_NAME, targetPackage);
        intent.putExtra(BackupService.EXTRA_ENABLE_ACQUIRE, switchAcquire.isChecked());
        startService(intent);
        adapter.updateProgress(targetPackage, 0);
    }

    private void launchAcquire(String serverPackage) {
        Intent backIntent = new Intent(this, MainActivity.class);
        backIntent.setAction(BackupService.ACTION_ACQUIRE_RESULT);
        backIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        Intent acquireIntent = new Intent("com.mov.action.acquire");
        acquireIntent.setPackage(serverPackage);
        acquireIntent.putExtra("device_title", " ");
        acquireIntent.putExtra("app_title", getString(R.string.app_name));
        acquireIntent.putExtra("param_acquire_intent", backIntent);
        try {
            startActivity(acquireIntent);
        } catch (Exception e) {
            Toast.makeText(this, "Acquire failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && BackupService.ACTION_ACQUIRE_RESULT.equals(intent.getAction())) {
            int acquireResult = intent.getIntExtra(BackupService.EXTRA_ACQUIRE_RESULT, 1);
            boolean granted = acquireResult == 2;
            Intent serviceIntent = new Intent(this, BackupService.class);
            serviceIntent.setAction(BackupService.ACTION_ACQUIRE_RESULT);
            serviceIntent.putExtra(BackupService.EXTRA_ACQUIRE_RESULT, granted);
            startService(serviceIntent);
        }
    }
}