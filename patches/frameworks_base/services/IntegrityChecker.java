/*
 * Copyright (C) 2025 Android Override Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.android.override;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Built-in Play Integrity status checker.
 *
 * Provides diagnostic information about the current
 * Play Integrity state without requiring external apps.
 *
 * Checks:
 * - Build fingerprint validity
 * - Keybox status
 * - TEE attestation readiness
 * - GMS compatibility
 * - Known failure indicators
 */
public class IntegrityChecker {

    private static final String TAG = "IntegrityChecker";

    /**
     * Result of integrity check.
     */
    public static class CheckResult {
        public boolean fingerprintValid;
        public boolean keyboxLoaded;
        public boolean keyboxHealthy;
        public boolean teeSpoof;
        public boolean gmsInstalled;
        public boolean buildPropsConsistent;
        public String fingerprintValue;
        public String keyboxInfo;
        public String diagnostics;

        // Predicted PI levels
        public boolean predictBasic;
        public boolean predictDevice;
        public boolean predictStrong;

        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Play Integrity Prediction ===\n");
            sb.append("BASIC:  ").append(predictBasic ? "✅ PASS" : "❌ FAIL").append("\n");
            sb.append("DEVICE: ").append(predictDevice ? "✅ PASS" : "❌ FAIL").append("\n");
            sb.append("STRONG: ").append(predictStrong ? "✅ PASS" : "❌ FAIL").append("\n");
            sb.append("\n=== Component Status ===\n");
            sb.append("Fingerprint: ").append(fingerprintValid ? "✅" : "❌").append(" ").append(fingerprintValue).append("\n");
            sb.append("Keybox:      ").append(keyboxLoaded ? "✅ Loaded" : "❌ Not loaded");
            if (keyboxLoaded) {
                sb.append(keyboxHealthy ? " (Healthy)" : " (⚠️ Revoked)");
            }
            sb.append("\n");
            sb.append("TEE Spoof:   ").append(teeSpoof ? "✅ Enabled" : "❌ Disabled").append("\n");
            sb.append("GMS:         ").append(gmsInstalled ? "✅ Installed" : "❌ Not found").append("\n");
            sb.append("Build Props: ").append(buildPropsConsistent ? "✅ Consistent" : "⚠️ Inconsistent").append("\n");

            if (diagnostics != null && !diagnostics.isEmpty()) {
                sb.append("\n=== Diagnostics ===\n").append(diagnostics);
            }

            return sb.toString();
        }
    }

    /**
     * Run full integrity check.
     */
    public static CheckResult runCheck(Context context) {
        CheckResult result = new CheckResult();
        StringBuilder diag = new StringBuilder();

        OverrideController controller = OverrideController.getInstance();
        KeyboxManager keybox = KeyboxManager.getInstance();

        // 1. Check fingerprint
        result.fingerprintValue = controller.getFingerprint();
        result.fingerprintValid = isValidFingerprint(result.fingerprintValue);
        if (!result.fingerprintValid) {
            diag.append("⚠️ Fingerprint invalid or empty. Set a valid device fingerprint.\n");
        }

        // 2. Check keybox
        result.keyboxLoaded = keybox.isLoaded();
        result.keyboxHealthy = result.keyboxLoaded && !keybox.isRevoked();
        if (!result.keyboxLoaded) {
            diag.append("⚠️ No keybox loaded. Import a valid keybox XML for DEVICE level.\n");
        } else if (keybox.isRevoked()) {
            diag.append("🔴 Keybox is REVOKED. Replace with a new keybox.\n");
        }
        result.keyboxInfo = keybox.getKeyboxInfo();

        // 3. Check TEE spoofing
        result.teeSpoof = controller.isSpoofTEEEnabled();
        if (!result.teeSpoof) {
            diag.append("⚠️ TEE spoofing disabled. Enable for DEVICE level pass.\n");
        }

        // 4. Check GMS
        result.gmsInstalled = isPackageInstalled(context, "com.google.android.gms");
        if (!result.gmsInstalled) {
            diag.append("🔴 Google Play Services not installed. PI will not work.\n");
        }

        // 5. Check build props consistency
        result.buildPropsConsistent = checkPropsConsistency(controller);
        if (!result.buildPropsConsistent) {
            diag.append("⚠️ Build properties inconsistent. Some apps may detect spoofing.\n");
        }

        // 6. Check for common issues
        checkCommonIssues(context, diag);

        result.diagnostics = diag.toString();

        // Predict PI levels
        result.predictBasic = result.fingerprintValid && result.gmsInstalled;
        result.predictDevice = result.predictBasic && result.keyboxLoaded &&
                               result.keyboxHealthy && result.teeSpoof;
        result.predictStrong = false; // Never pass without real hardware attestation

        Log.i(TAG, "Integrity check: BASIC=" + result.predictBasic
                + " DEVICE=" + result.predictDevice
                + " STRONG=" + result.predictStrong);

        return result;
    }

    /**
     * Quick check — just return predicted PI levels.
     */
    public static String quickCheck() {
        OverrideController controller = OverrideController.getInstance();
        KeyboxManager keybox = KeyboxManager.getInstance();

        boolean basic = isValidFingerprint(controller.getFingerprint());
        boolean device = basic && keybox.isLoaded() && !keybox.isRevoked()
                        && controller.isSpoofTEEEnabled();

        return "BASIC: " + (basic ? "✅" : "❌") +
               " | DEVICE: " + (device ? "✅" : "❌") +
               " | STRONG: ❌";
    }

    private static boolean isValidFingerprint(String fp) {
        if (fp == null || fp.isEmpty()) return false;
        // Valid format: brand/product/device:version/buildId/incremental:type/tags
        return fp.contains("/") && fp.contains(":") && fp.length() > 20;
    }

    private static boolean isPackageInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean checkPropsConsistency(OverrideController controller) {
        String fp = controller.getFingerprint();
        String model = controller.getModel();
        String manufacturer = controller.getManufacturer();

        if (fp == null || fp.isEmpty()) return false;

        // Check fingerprint contains manufacturer brand
        if (manufacturer != null && !manufacturer.isEmpty()) {
            String brand = fp.split("/")[0];
            // Brand should somewhat match manufacturer
            if (!brand.toLowerCase().contains(manufacturer.toLowerCase()) &&
                    !manufacturer.toLowerCase().contains(brand.toLowerCase())) {
                return false;
            }
        }

        return true;
    }

    private static void checkCommonIssues(Context context, StringBuilder diag) {
        // Check if Play Store is installed
        if (!isPackageInstalled(context, "com.android.vending")) {
            diag.append("⚠️ Play Store not installed. Some PI features may not work.\n");
        }

        // Check if GSF is installed
        if (!isPackageInstalled(context, "com.google.android.gsf")) {
            diag.append("⚠️ Google Services Framework not installed.\n");
        }

        // Check SE Linux status
        try {
            Process process = Runtime.getRuntime().exec("getenforce");
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            String status = reader.readLine();
            if (!"Enforcing".equals(status)) {
                diag.append("🔴 SELinux is " + status + ". Must be Enforcing for PI.\n");
            }
        } catch (Exception e) {
            // Ignore
        }
    }
}
