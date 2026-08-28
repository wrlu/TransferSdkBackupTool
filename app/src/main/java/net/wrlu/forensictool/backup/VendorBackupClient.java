package net.wrlu.forensictool.backup;

import android.content.Context;

public abstract class VendorBackupClient {

    protected final Context context;
    protected BackupCallback callback;
    protected boolean acquired;
    protected boolean acquireEnabled;

    protected VendorBackupClient(Context context) {
        this.context = context.getApplicationContext();
    }

    public void setCallback(BackupCallback callback) {
        this.callback = callback;
    }

    public void setAcquired(boolean acquired) {
        this.acquired = acquired;
    }

    public void setAcquireEnabled(boolean enabled) {
        this.acquireEnabled = enabled;
    }

    public abstract boolean connect();

    public abstract void disconnect();

    public abstract void backupPackage(String pkgName, int userId);

    public final String getVendorName() {
        return getClass().getSimpleName();
    }
}
