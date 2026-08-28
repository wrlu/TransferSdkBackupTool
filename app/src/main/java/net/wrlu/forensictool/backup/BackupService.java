package net.wrlu.forensictool.backup;

import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BackupService extends Service implements BackupCallback {

    private static final String TAG = "BackupService";

    public static final String EXTRA_PKG_NAME = "pkg_name";
    public static final String EXTRA_SERVER_PACKAGE = "server_package";

    public static final String ACTION_ACQUIRE_REQUIRED = "net.wrlu.forensictool.action.ACQUIRE_REQUIRED";
    public static final String ACTION_ACQUIRE_RESULT = "net.wrlu.forensictool.action.ACQUIRE_RESULT";
    public static final String ACTION_ACQUIRE_DENIED = "net.wrlu.forensictool.action.ACQUIRE_DENIED";
    public static final String ACTION_PROGRESS = "net.wrlu.forensictool.action.PROGRESS";
    public static final String ACTION_COMPLETED = "net.wrlu.forensictool.action.COMPLETED";
    public static final String EXTRA_ACQUIRE_RESULT = "acquire_result";
    public static final String EXTRA_PERCENT = "percent";
    public static final String EXTRA_SUCCESS = "success";
    public static final String EXTRA_ERROR_DESC = "error_desc";
    public static final String EXTRA_FILE_PATH = "file_path";
    public static final String EXTRA_ENABLE_ACQUIRE = "enable_acquire";

    private String detectedVendor;
    private boolean vivoPlatformSigned;
    private String pendingPkgName;
    private final Map<String, VendorBackupClient> clients = new ConcurrentHashMap<>();
    private HandlerThread workerThread;
    private Handler workerHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        workerThread = new HandlerThread("backup_worker");
        workerThread.start();
        workerHandler = new Handler(workerThread.getLooper());

        detectedVendor = VendorRegistry.detectVendorByBrand();
        if (VendorRegistry.VIVO.equals(detectedVendor)) {
            vivoPlatformSigned = PlatformSignatureChecker.isPlatformSigned(this);
            if (!vivoPlatformSigned) {
                Log.w(TAG, "vivo device but not platform signed, backup unavailable");
            }
        }
        Log.i(TAG, "onCreate vendor=" + detectedVendor + " platformSigned=" + vivoPlatformSigned);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        if (ACTION_ACQUIRE_RESULT.equals(action)) {
            handleAcquireResult(intent);
            return START_NOT_STICKY;
        }

        String pkgName = intent.getStringExtra(EXTRA_PKG_NAME);
        if (pkgName == null) {
            Log.e(TAG, "Missing required extra: pkg_name");
            return START_NOT_STICKY;
        }

        String vendor = ensureVendor();
        if (vendor == null) {
            Log.e(TAG, "No compatible vendor found on this device");
            return START_NOT_STICKY;
        }

        if (VendorRegistry.VIVO.equals(vendor) && !vivoPlatformSigned) {
            Log.e(TAG, "vivo backup requires platform signature");
            return START_NOT_STICKY;
        }

        Log.i(TAG, "onStartCommand pkg=" + pkgName + " vendor=" + vendor);
        pendingPkgName = pkgName;
        boolean enableAcquire = intent.getBooleanExtra(EXTRA_ENABLE_ACQUIRE, false);

        workerHandler.post(() -> {
            VendorBackupClient client = getOrCreateClient(vendor);
            if (client == null) {
                Log.e(TAG, "No client implementation for vendor: " + vendor);
                return;
            }
            if (enableAcquire) {
                client.setAcquireEnabled(true);
            } else {
                client.setAcquireEnabled(false);
            }
            client.backupPackage(pkgName, 0);
        });

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        for (VendorBackupClient client : clients.values()) {
            client.disconnect();
        }
        clients.clear();
        if (workerThread != null) {
            workerThread.quitSafely();
        }
        super.onDestroy();
    }

    private String ensureVendor() {
        if (detectedVendor != null) {
            return detectedVendor;
        }
        detectedVendor = VendorRegistry.detectVendorByBrand();
        if (VendorRegistry.VIVO.equals(detectedVendor)) {
            vivoPlatformSigned = PlatformSignatureChecker.isPlatformSigned(this);
        }
        return detectedVendor;
    }

    private void handleAcquireResult(Intent intent) {
        boolean granted = intent.getBooleanExtra(EXTRA_ACQUIRE_RESULT, false);
        Log.i(TAG, "Acquire result: granted=" + granted);
        if (granted) {
            VendorBackupClient client = clients.get(detectedVendor);
            if (client != null) {
                client.setAcquired(true);
            }
            if (pendingPkgName != null) {
                String pkgName = pendingPkgName;
                pendingPkgName = null;
                String vendor = detectedVendor;
                workerHandler.post(() -> {
                    VendorBackupClient c = clients.get(vendor);
                    if (c != null) {
                        c.backupPackage(pkgName, 0);
                    }
                });
            }
        } else {
            String pkgName = pendingPkgName;
            pendingPkgName = null;
            if (pkgName != null) {
                Intent denied = new Intent(ACTION_ACQUIRE_DENIED);
                denied.setPackage(getPackageName());
                denied.putExtra(EXTRA_PKG_NAME, pkgName);
                sendBroadcast(denied);
            }
        }
    }

    private VendorBackupClient getOrCreateClient(String vendor) {
        VendorBackupClient client = clients.get(vendor);
        if (client != null) {
            return client;
        }
        client = createClient(vendor);
        if (client == null) {
            return null;
        }
        client.setCallback(this);
        client.connect();
        clients.put(vendor, client);
        return client;
    }

    private VendorBackupClient createClient(String vendor) {
        switch (vendor) {
            case VendorRegistry.HONOR:
                return new HonorBackupClient(this);
            case VendorRegistry.OPPO:
                return new OppoBackupClient(this);
            case VendorRegistry.XIAOMI:
                return new XiaomiBackupClient(this);
            case VendorRegistry.VIVO:
                return new VivoBackupClient(this);
            default:
                Log.e(TAG, "No client implementation for vendor: " + vendor);
                return null;
        }
    }

    private String copyToDownloads(File source, String name) {
        String relativePath = "Download";
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);

        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            Log.e(TAG, "Failed to create MediaStore entry");
            source.delete();
            return null;
        }
        String absolutePath = null;
        try (FileInputStream fis = new FileInputStream(source);
             OutputStream os = getContentResolver().openOutputStream(uri)) {
            if (os == null) {
                Log.e(TAG, "openOutputStream failed");
                source.delete();
                return null;
            }
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) != -1) {
                os.write(buf, 0, n);
            }
            os.flush();
            Log.i(TAG, "copyToDownloads done: " + name);
        } catch (IOException e) {
            Log.e(TAG, "copyToDownloads failed: " + e.getMessage());
            source.delete();
            return null;
        } finally {
            source.delete();
        }
        try {
            android.database.Cursor cursor = getContentResolver().query(uri,
                    new String[]{MediaStore.MediaColumns.DATA}, null, null, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    absolutePath = cursor.getString(0);
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to query absolute path: " + e.getMessage());
        }
        if (absolutePath == null) {
            absolutePath = "/storage/emulated/0/" + relativePath + "/" + name;
        }
        return absolutePath;
    }

    @Override
    public void onStarted(String pkgName) {
        Log.i(TAG, "Backup started: pkg=" + pkgName);
    }

    @Override
    public void onProgress(String pkgName, int percent) {
        Log.d(TAG, "Backup progress: pkg=" + pkgName + " " + percent + "%");
        Intent intent = new Intent(ACTION_PROGRESS);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_PKG_NAME, pkgName);
        intent.putExtra(EXTRA_PERCENT, percent);
        sendBroadcast(intent);
    }

    @Override
    public void onCompleted(String pkgName, boolean success, String errorDesc, File tempFile) {
        Log.i(TAG, "Backup complete: pkg=" + pkgName + " success=" + success + " desc=" + errorDesc);
        String filePath = null;
        if (success && tempFile != null && tempFile.exists()) {
            filePath = copyToDownloads(tempFile, pkgName + ".tar");
        } else if (tempFile != null) {
            tempFile.delete();
        }
        Intent intent = new Intent(ACTION_COMPLETED);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_PKG_NAME, pkgName);
        intent.putExtra(EXTRA_SUCCESS, success);
        intent.putExtra(EXTRA_ERROR_DESC, errorDesc != null ? errorDesc : "");
        intent.putExtra(EXTRA_FILE_PATH, filePath != null ? filePath : "");
        sendBroadcast(intent);
    }

    @Override
    public void onAcquireRequired(String vendor, String serverPackage) {
        Log.i(TAG, "Acquire required for vendor=" + vendor + " package=" + serverPackage);
        Intent broadcast = new Intent(ACTION_ACQUIRE_REQUIRED);
        broadcast.setPackage(getPackageName());
        broadcast.putExtra(EXTRA_SERVER_PACKAGE, serverPackage);
        sendBroadcast(broadcast);
    }
}