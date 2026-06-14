/*
 * Copyright (C) 2025 Android Override Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.android.override;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Anti-detection layer to hide spoofing and root traces.
 *
 * Handles:
 * - Hide specified apps from PackageManager queries
 * - Hide root/KSU/Magisk indicators from process/file checks
 * - Clean system properties that leak custom ROM state
 * - Filter logcat output from spoofing artifacts
 *
 * Integration points:
 * - PackageManagerService (app hiding)
 * - SystemProperties (prop filtering)
 * - Process (mount namespace isolation)
 */
public class AntiDetection {

    private static final String TAG = "AntiDetection";

    // Known root/framework indicators to hide
    private static final Set<String> ROOT_PACKAGES = new HashSet<>(Arrays.asList(
            "com.topjohnwu.magisk",
            "io.github.vvb2060.magisk",       // Magisk Delta
            "me.weishu.kernelsu",
            "me.bmax.apatch",
            "com.android.shell",
            "org.lsposed.manager",
            "io.github.lsposed.manager",
            "com.tsng.hidemyapplist",
            "com.ksu.kowsu"                     // KowSU Manager
    ));

    // System properties that reveal custom ROM
    private static final Set<String> SENSITIVE_PROPS = new HashSet<>(Arrays.asList(
            "ro.build.flavor",
            "ro.modversion",
            "ro.lineage.build.version",
            "ro.lineage.version",
            "ro.matrixx.version",
            "ro.matrixx.build.variant",
            "ro.cm.version",
            "persist.sys.override.enabled",
            "persist.sys.override.fingerprint"
    ));

    // Files/paths that reveal root
    private static final Set<String> ROOT_PATHS = new HashSet<>(Arrays.asList(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/app/Superuser.apk",
            "/data/adb/magisk",
            "/data/adb/ksu",
            "/data/adb/ap",
            "/data/adb/modules"
    ));

    /**
     * Check if a package should be hidden from app list queries.
     *
     * Called from PackageManagerService.getInstalledPackages()
     * and PackageManagerService.getPackageInfo()
     *
     * @param packageName The package being queried
     * @param callingPackage The package making the query
     * @return true if package should be hidden from caller
     */
    public static boolean shouldHidePackage(String packageName, String callingPackage) {
        OverrideController controller = OverrideController.getInstance();
        if (!controller.isEnabled() || !controller.isAntiDetectionEnabled()) {
            return false;
        }

        // Never hide from system processes
        if (isSystemCaller(callingPackage)) {
            return false;
        }

        // Check user-configured hidden apps
        if (controller.isHideAppsEnabled() && controller.isAppHidden(packageName)) {
            Log.d(TAG, "Hiding app from " + callingPackage + ": " + packageName);
            return true;
        }

        // Auto-hide known root packages
        if (ROOT_PACKAGES.contains(packageName)) {
            Log.d(TAG, "Hiding root package from " + callingPackage + ": " + packageName);
            return true;
        }

        return false;
    }

    /**
     * Filter installed packages list to remove hidden apps.
     *
     * @param packages Original package list
     * @param callingPackage The package making the query
     * @return Filtered list
     */
    public static List<PackageInfo> filterPackageList(List<PackageInfo> packages,
                                                       String callingPackage) {
        OverrideController controller = OverrideController.getInstance();
        if (!controller.isEnabled() || !controller.isAntiDetectionEnabled()) {
            return packages;
        }

        if (isSystemCaller(callingPackage)) {
            return packages;
        }

        packages.removeIf(pkg -> shouldHidePackage(pkg.packageName, callingPackage));
        return packages;
    }

    /**
     * Filter application info list.
     */
    public static List<ApplicationInfo> filterApplicationList(List<ApplicationInfo> apps,
                                                               String callingPackage) {
        OverrideController controller = OverrideController.getInstance();
        if (!controller.isEnabled() || !controller.isAntiDetectionEnabled()) {
            return apps;
        }

        if (isSystemCaller(callingPackage)) {
            return apps;
        }

        apps.removeIf(app -> shouldHidePackage(app.packageName, callingPackage));
        return apps;
    }

    /**
     * Check if a system property should be filtered.
     *
     * Called from SystemProperties.get() hook.
     *
     * @param key The property key being read
     * @param callingProcess The process reading the property
     * @return replacement value, or null to use real value
     */
    public static String filterSystemProperty(String key, String callingProcess) {
        OverrideController controller = OverrideController.getInstance();
        if (!controller.isEnabled() || !controller.isAntiDetectionEnabled()) {
            return null;
        }

        // Hide override-related properties from non-system processes
        if (SENSITIVE_PROPS.contains(key) && !isSystemProcess(callingProcess)) {
            return ""; // Return empty instead of real value
        }

        // Hide custom ROM indicators
        if (key.startsWith("ro.lineage.") || key.startsWith("ro.matrixx.") ||
                key.startsWith("ro.cm.")) {
            if (!isSystemProcess(callingProcess)) {
                return "";
            }
        }

        return null; // Use real value
    }

    /**
     * Check if a file access should report "not found".
     *
     * Hook point for File.exists() or access() syscall.
     *
     * @param path File path being checked
     * @param callingPackage Calling package
     * @return true if path should appear non-existent
     */
    public static boolean shouldHidePath(String path, String callingPackage) {
        OverrideController controller = OverrideController.getInstance();
        if (!controller.isEnabled() || !controller.isAntiDetectionEnabled()) {
            return false;
        }

        if (isSystemCaller(callingPackage)) {
            return false;
        }

        // Hide root-related paths
        for (String rootPath : ROOT_PATHS) {
            if (path.equals(rootPath) || path.startsWith(rootPath + "/")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if a mount point should be hidden from /proc/mounts.
     *
     * Magisk/KSU add mount entries that detection apps look for.
     *
     * @param mountLine A line from /proc/mounts
     * @return true if this mount should be filtered out
     */
    public static boolean shouldHideMount(String mountLine) {
        OverrideController controller = OverrideController.getInstance();
        if (!controller.isEnabled() || !controller.isAntiDetectionEnabled()) {
            return false;
        }

        if (mountLine == null) return false;

        // Filter known root-related mounts
        return mountLine.contains("/data/adb/modules") ||
               mountLine.contains("magisk") ||
               mountLine.contains("ksu") ||
               mountLine.contains("/sbin/.magisk") ||
               mountLine.contains("tmpfs /system/") ||
               mountLine.contains("tmpfs /vendor/");
    }

    /**
     * Filter logcat output to remove spoofing traces.
     *
     * @param tag Log tag
     * @param message Log message
     * @return true if this log line should be suppressed
     */
    public static boolean shouldSuppressLog(String tag, String message) {
        OverrideController controller = OverrideController.getInstance();
        if (!controller.isEnabled() || !controller.isAntiDetectionEnabled()) {
            return false;
        }

        // Suppress our own logs from logcat
        if ("OverrideController".equals(tag) ||
                "PropsHooks".equals(tag) ||
                "KeyboxManager".equals(tag) ||
                "AttestationHooks".equals(tag) ||
                "AntiDetection".equals(tag)) {
            return true;
        }

        // Suppress spoofing-related messages
        if (message != null) {
            if (message.contains("spoofing") ||
                    message.contains("keybox") ||
                    message.contains("PropsHooks") ||
                    message.contains("Override")) {
                return true;
            }
        }

        return false;
    }

    // ========== Internal Helpers ==========

    private static boolean isSystemCaller(String callingPackage) {
        if (TextUtils.isEmpty(callingPackage)) return true;
        return "android".equals(callingPackage) ||
               "com.android.settings".equals(callingPackage) ||
               "com.android.systemui".equals(callingPackage) ||
               "com.android.shell".equals(callingPackage) ||
               callingPackage.startsWith("com.android.providers.");
    }

    private static boolean isSystemProcess(String processName) {
        if (TextUtils.isEmpty(processName)) return true;
        return "system_server".equals(processName) ||
               "android".equals(processName) ||
               processName.startsWith("com.android.");
    }
}
