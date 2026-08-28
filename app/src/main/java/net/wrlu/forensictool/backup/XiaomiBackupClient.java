package net.wrlu.forensictool.backup;

import android.content.Context;

public class XiaomiBackupClient extends TransferSdkBackupClient {

    public XiaomiBackupClient(Context context) {
        super(context);
    }

    @Override
    protected String getAction() {
        return "com.mov.action.backupmanager.impl";
    }

    @Override
    protected String getTargetPackage() {
        return "com.miui.huanji";
    }
}
