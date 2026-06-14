/*
 * Copyright (C) 2025 Android Override Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.android.override;

import android.app.Application;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Hooks into android.os.Build fields to spoof device identity.
 *
 * Selective spoofing — only active for targeted processes (GMS, Play Store, etc.)
 * Other apps see real device values.
 *
 * Integration point: called from ActivityThread.handleBindApplication()
 * before the app's Application.onCreate().
 */
public class PropsHooks {

    private static final String TAG = "PropsHooks";

    // Processes that receive spoofed values
    private static final Set<String> GMS_PROCESSES = new HashSet<>(Arrays.asList(
            "com.google.android.gms",
            "com.google.android.gms.persistent",
            "com.google.android.gms.unstable",
            "com.google.android.gms.ui",
            "com.android.vending",               // Play Store
            "com.google.android.gsf"              // Google Services Framework
    ));

    // Processes that should NEVER be spoofed
    private static final Set<String> EXEMPT_PROCESSES = new HashSet<>(Arrays.asList(
            "com.android.settings",
            "com.android.systemui",
            "com.android.shell",
            "android"
    ));

    private static boolean sIsInitialized = false;
    private static String sProcessName = "";

    /**
     * Called from ActivityThread.handleBindApplication() to apply hooks.
     *
     * @param app The application being created
     * @param processName The process name
     */
    public static void onApplicationCreated(Application app, String processName) {
        if (sIsInitialized) return;
        sIsInitialized = true;
        sProcessName = processName != null ? processName : "";

        OverrideController controller = OverrideController.getInstance();
        if (!controller.isEnabled()) {
            Log.d(TAG, "Override disabled, skipping hooks for: " + sProcessName);
            return;
        }

        // Check if this process should be spoofed
        if (EXEMPT_PROCESSES.contains(sProcessName)) {
            Log.d(TAG, "Exempt process, skipping: " + sProcessName);
            return;
        }

        // Check per-app config first
        OverrideController.PerAppConfig perApp = controller.getPerAppConfig(sProcessName);
        if (perApp != null && !perApp.spoofingEnabled) {
            Log.d(TAG, "Per-app spoofing disabled for: " + sProcessName);
            return;
        }

        if (shouldSpoof(sProcessName)) {
            applyBuildHooks(controller, sProcessName);
        }
    }

    /**
     * Determine if a process should receive spoofed values.
     */
    private static boolean shouldSpoof(String processName) {
        if (TextUtils.isEmpty(processName)) return false;

        // Always spoof GMS processes
        if (GMS_PROCESSES.contains(processName)) return true;

        // Check per-app config
        OverrideController controller = OverrideController.getInstance();
        OverrideController.PerAppConfig perApp = controller.getPerAppConfig(processName);
        if (perApp != null && perApp.spoofingEnabled) return true;

        return false;
    }

    /**
     * Apply Build.* field overrides via reflection.
     */
    private static void applyBuildHooks(OverrideController controller, String processName) {
        try {
            // Get effective values (per-app > global)
            String fingerprint = controller.getEffectiveFingerprint(processName);
            String model = controller.getEffectiveModel(processName);
            String manufacturer = resolveValue(controller, processName, "manufacturer");
            String product = resolveValue(controller, processName, "product");
            String device = resolveValue(controller, processName, "device");

            // Apply overrides
            if (!TextUtils.isEmpty(fingerprint)) {
                setBuildField("FINGERPRINT", fingerprint);

                // Parse fingerprint components:
                // brand/product/device:version/id/number:type/tags
                String[] parts = fingerprint.split("/");
                if (parts.length >= 1) {
                    setBuildField("BRAND", parts[0]);
                }
            }

            if (!TextUtils.isEmpty(model)) {
                setBuildField("MODEL", model);
            }

            if (!TextUtils.isEmpty(manufacturer)) {
                setBuildField("MANUFACTURER", manufacturer);
            }

            if (!TextUtils.isEmpty(product)) {
                setBuildField("PRODUCT", product);
            }

            if (!TextUtils.isEmpty(device)) {
                setBuildField("DEVICE", device);
            }

            // Security patch
            String securityPatch = controller.getSecurityPatch();
            if (!TextUtils.isEmpty(securityPatch)) {
                setBuildVersionField("SECURITY_PATCH", securityPatch);
            }

            // Spoof bootloader state if TEE spoofing enabled
            if (controller.isSpoofTEEEnabled()) {
                setBuildField("TAGS", "release-keys");
                setBuildField("TYPE", "user");

                // Spoof verified boot state
                setBuildVersionField("INCREMENTAL", extractIncremental(fingerprint));
            }

            Log.i(TAG, "Build hooks applied for: " + processName
                    + " fp=" + (TextUtils.isEmpty(fingerprint) ? "(none)" : fingerprint.substring(0, Math.min(30, fingerprint.length())) + "..."));

        } catch (Exception e) {
            Log.e(TAG, "Failed to apply build hooks for: " + processName, e);
        }
    }

    private static String resolveValue(OverrideController controller,
                                        String processName, String field) {
        OverrideController.PerAppConfig perApp = controller.getPerAppConfig(processName);
        switch (field) {
            case "manufacturer":
                if (perApp != null && !TextUtils.isEmpty(perApp.manufacturer)) return perApp.manufacturer;
                return controller.getManufacturer();
            case "product":
                if (perApp != null && !TextUtils.isEmpty(perApp.product)) return perApp.product;
                return controller.getProduct();
            case "device":
                if (perApp != null && !TextUtils.isEmpty(perApp.device)) return perApp.device;
                return controller.getDevice();
            default:
                return null;
        }
    }

    /**
     * Set a Build class static field via reflection.
     */
    private static void setBuildField(String fieldName, String value) {
        try {
            Field field = Build.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(null, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.w(TAG, "Cannot set Build." + fieldName, e);
        }
    }

    /**
     * Set a Build.VERSION class static field via reflection.
     */
    private static void setBuildVersionField(String fieldName, String value) {
        try {
            Field field = Build.VERSION.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(null, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.w(TAG, "Cannot set Build.VERSION." + fieldName, e);
        }
    }

    /**
     * Extract INCREMENTAL from fingerprint.
     * Format: brand/product/device:version/buildId/incremental:type/tags
     */
    private static String extractIncremental(String fingerprint) {
        if (TextUtils.isEmpty(fingerprint)) return "";
        try {
            // Split by / and :
            String[] mainParts = fingerprint.split(":");
            if (mainParts.length >= 2) {
                String middle = mainParts[1]; // version/buildId/incremental
                String[] subParts = middle.split("/");
                if (subParts.length >= 3) {
                    return subParts[2]; // incremental
                }
            }
        } catch (Exception e) {
            // fallback
        }
        return "";
    }

    /**
     * Check if current process is being spoofed.
     * Used by other components to know current state.
     */
    public static boolean isCurrentProcessSpoofed() {
        return sIsInitialized && shouldSpoof(sProcessName);
    }

    /**
     * Get the current process name.
     */
    public static String getCurrentProcessName() {
        return sProcessName;
    }
}
