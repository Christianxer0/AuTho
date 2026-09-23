package com.yourteam.autho.models;

import android.graphics.drawable.Drawable;

public class AppInfo {
    public String appName;
    public String packageName;
    public Drawable icon;
    public boolean isSystemApp;
    public boolean isBloatware;
    public boolean isRemoved;
    public boolean isProtected;

    public AppInfo(String appName, String packageName, Drawable icon,
                   boolean isSystemApp, boolean isBloatware, boolean isProtected) {
        this.appName = appName;
        this.packageName = packageName;
        this.icon = icon;
        this.isSystemApp = isSystemApp;
        this.isBloatware = isBloatware;
        this.isProtected = isProtected;
        this.isRemoved = false;
    }
}