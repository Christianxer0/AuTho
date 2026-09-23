package com.yourteam.autho.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Heuristic bloatware detector.
 * Uses a known list of vendor packages and protected system packages.
 */
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
            "com.android.nfc",
            "com.android.wallpaper"
    ));


    private static final String[] BLOATWARE_PREFIXES = {
            "com.facebook.",
            "com.instagram.",
            "com.whatsapp.",              // may be user-installed; still flagged
            "com.samsung.android.game",   // Samsung games
            "com.samsung.android.bixby",  // Bixby
            "com.samsung.android.app.",   // Samsung apps
            "com.samsung.android.voc",
            "com.samsung.android.oneconnect",
            "com.sec.android.app.",       // Samsung system apps
            "com.miui.player",
            "com.miui.video",
            "com.miui.miservice",
            "com.mi.globalbrowser",
            "com.xiaomi.mipicks",
            "com.xiaomi.glgm",
            "com.huawei.",
            "com.hihonor.",
            "com.oppo.",
            "com.coloros.",
            "com.realme.",
            "com.vivo.",
            "com.transsion.",
            "com.oneplus.",
            "com.lge.",
            "com.motorola.",
            "com.sony.",
            "com.lenovo.",
            "com.asus.",
            "com.sec.android.app.sbrowser",
            "com.sec.android.app.chromecustomizations",
            "com.google.android.apps.tachyon",
            "com.google.android.videos",
            "com.google.android.music",
            "com.google.android.apps.magazines",
            "com.google.android.apps.books",
            "com.google.android.apps.docs",
            "com.google.android.apps.maps",
            "com.google.android.youtube",
            "com.google.android.apps.photos",
            "com.google.android.apps.turbo",
            "com.android.chrome",
            "com.android.vending",
            "com.tencent.",
            "com.qihoo.",
            "com.baidu.",
            "com.alibaba.",
            "com.cleanmaster.",
            "com.cheetahmobile."
    };

    /** Returns true if the given package is a critical system app. */
    public static boolean isProtected(String packageName) {
        if (packageName == null) return false;
        if (PROTECTED_PACKAGES.contains(packageName)) return true;

        return packageName.equals("android");
    }

    /** Returns true if the given package looks like bloatware. */
    public static boolean isBloatware(String packageName) {
        if (packageName == null) return false;
        for (String prefix : BLOATWARE_PREFIXES) {
            if (packageName.startsWith(prefix)) return true;
        }
        return false;
    }

    /**
     * Decide if a package can safely be removed.
     * Rule: system app + not protected + (bloatware OR user-installed)
     */
    public static boolean canBeRemoved(String packageName, boolean isSystemApp) {
        if (isProtected(packageName)) return false;
        if (isSystemApp && !isBloatware(packageName)) return false;
        return true;
    }
}