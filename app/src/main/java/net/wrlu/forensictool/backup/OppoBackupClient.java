package net.wrlu.forensictool.backup;

import android.content.Context;

public class OppoBackupClient extends TransferSdkBackupClient {

    public OppoBackupClient(Context context) {
        super(context);
    }

    @Override
    protected String getAction() {
        return "com.mov.action.backupmanager.impl";
    }

    @Override
    protected String getTargetPackage() {
        return "com.coloros.backuprestore";
    }
}