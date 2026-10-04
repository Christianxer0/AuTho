package com.yourteam.autho.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class BloatwareDetector {

    private static final Set<String> PROTECTED_PACKAGES = new HashSet<>(Arrays.asList(
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.phone",
            "com.android.providers.settings",
            "com.android.providers.contacts",
            "com.android.providers.telephony",
            "com.android.providers.media",
            "com.android.server.telecom",
            "com.android.launcher",
            "com.android.launcher3",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.google.android.packageinstaller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.android.shell",
            "com.android.keychain",
            "com.android.certinstaller",
            "com.android.bluetooth",
            "com.android.nfc"
    ));

    private static final String[] BLOATWARE_PREFIXES = {
            "com.facebook.", "com.instagram.", "com.whatsapp.",
            "com.samsung.android.game", "com.samsung.android.bixby",
            "com.samsung.android.app.", "com.samsung.android.voc",
            "com.samsung.android.oneconnect", "com.sec.android.app.",
            "com.miui.player", "com.miui.video", "com.miui.miservice",
            "com.mi.globalbrowser", "com.xiaomi.mipicks", "com.xiaomi.glgm",
            "com.huawei.", "com.hihonor.", "com.oppo.", "com.coloros.",
            "com.realme.", "com.vivo.", "com.transsion.", "com.oneplus.",
            "com.lge.", "com.motorola.", "com.sony.", "com.lenovo.",
            "com.asus.", "com.sec.android.app.sbrowser",
            "com.sec.android.app.chromecustomizations",
            "com.google.android.apps.tachyon", "com.google.android.videos",
            "com.google.android.music", "com.google.android.apps.magazines",
            "com.google.android.apps.books", "com.google.android.apps.docs",
            "com.google.android.apps.maps", "com.google.android.youtube",
            "com.google.android.apps.photos", "com.google.android.apps.turbo",
            "com.android.chrome", "com.tencent.", "com.qihoo.",
            "com.baidu.", "com.alibaba.", "com.cleanmaster.",
            "com.cheetahmobile."
    };

    public static boolean isProtected(String packageName) {
        if (packageName == null) return false;
        if (PROTECTED_PACKAGES.contains(packageName)) return true;
        return packageName.equals("android");
    }

    public static boolean isBloatware(String packageName) {
        if (packageName == null) return false;
        for (String prefix : BLOATWARE_PREFIXES) {
            if (packageName.startsWith(prefix)) return true;
        }
        return false;
    }

    public static boolean canBeRemoved(String packageName, boolean isSystemApp) {
        if (isProtected(packageName)) return false;
        if (isSystemApp && !isBloatware(packageName)) return false;
        return true;
    }
}