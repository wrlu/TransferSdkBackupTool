package net.wrlu.forensictool;

import android.graphics.drawable.Drawable;

public class AppInfo {

    final String packageName;
    final String appName;
    final Drawable icon;
    final boolean systemApp;
    int progress = -1;
    boolean completed = false;
    boolean success = false;
    String errorDesc = "";

    AppInfo(String packageName, String appName, Drawable icon, boolean systemApp) {
        this.packageName = packageName;
        this.appName = appName;
        this.icon = icon;
        this.systemApp = systemApp;
    }
}
