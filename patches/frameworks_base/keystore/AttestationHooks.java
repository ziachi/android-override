/*
 * Copyright (C) 2025 Android Override Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.android.override;

import android.os.Build;
import android.util.Log;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Hooks for hardware attestation spoofing.
 *
 * Intercepts KeyMint/Keymaster attestation requests and replaces
 * the attestation response with data from the loaded keybox.
 *
 * This makes the device appear to have valid hardware-level
 * key attestation, which is required for Play Integrity DEVICE level.
 *
 * Integration points:
 * - android.security.keystore2.AndroidKeyStoreSpi
 * - android.security.keystore.KeyGenParameterSpec
 * - Keystore2 system service
 */
public class AttestationHooks {

    private static final String TAG = "AttestationHooks";

    // Attestation security levels
    public static final int SECURITY_LEVEL_SOFTWARE = 0;
    public static final int SECURITY_LEVEL_TRUSTED_ENVIRONMENT = 1;
    public static final int SECURITY_LEVEL_STRONGBOX = 2;

    // Verified boot states
    public static final int VERIFIED_BOOT_GREEN = 0;    // Locked, verified
    public static final int VERIFIED_BOOT_YELLOW = 1;   // Locked, unverified
    public static final int VERIFIED_BOOT_ORANGE = 2;   // Unlocked

    /**
     * Hook point: called when KeyMint creates attestation extension.
     *
     * Replaces attestation security level and boot state to make
     * the device appear locked and hardware-attested.
     *
     * @return spoofed security level, or -1 to use real value
     */
    public static int getSpoofedSecurityLevel() {
        OverrideController controller = OverrideController.getInstance();
        if (!controller.isEnabled() || !controller.isSpoofTEEEnabled()) {
            return -1; // Use real value
        }

        KeyboxManager keybox = KeyboxManager.getInstance();
        if (!keybox.isLoaded()) {
            return -1;
        }

        Log.d(TAG, "Spoofing security level → TRUSTED_ENVIRONMENT");
        return SECURITY_LEVEL_TRUSTED_ENVIRONMENT;
    }

    /**
     * Hook point: called when building attestation extension.
     *
     * @return spoofed verified boot state (GREEN = locked + verified)
     */
    public static int getSpoofedVerifiedBootState() {
        OverrideController controller = OverrideController.getInstance();
        if (!controller.isEnabled() || !controller.isSpoofTEEEnabled()) {
            return -1;
        }

        String bootloaderState = controller.getBootloaderState();
        if ("locked".equals(bootloaderState)) {
            return VERIFIED_BOOT_GREEN;
        } else if ("unlocked".equals(bootloaderState)) {
            return VERIFIED_BOOT_ORANGE;
        }

        return VERIFIED_BOOT_GREEN; // Default: locked
    }

    /**
     * Hook point: check if attestation key should be replaced.
     *
     * @return true if keybox is loaded and should replace real attestation
     */
    public static boolean shouldReplaceAttestationKey() {
        OverrideController controller = OverrideController.getInstance();
        if (!controller.isEnabled() || !controller.isKeyboxEnabled()) {
            return false;
        }

        KeyboxManager keybox = KeyboxManager.getInstance();
        return keybox.isLoaded() && !keybox.isRevoked();
    }

    /**
     * Get the attestation private key from loaded keybox.
     *
     * @return PrivateKey for signing attestation, or null
     */
    public static PrivateKey getAttestationPrivateKey() {
        KeyboxManager keybox = KeyboxManager.getInstance();
        if (!keybox.isLoaded()) return null;
        return keybox.getPrivateKey();
    }

    /**
     * Get the attestation certificate chain from loaded keybox.
     *
     * @return List of encoded certificates, or null
     */
    public static List<byte[]> getAttestationCertChain() {
        KeyboxManager keybox = KeyboxManager.getInstance();
        if (!keybox.isLoaded()) return null;
        return keybox.getCertificateChainEncoded();
    }

    /**
     * Hook point: modify attestation record fields.
     *
     * Called when building the attestation extension (ASN.1 encoded).
     * Returns modified values for attestation record fields.
     */
    public static AttestationRecord getSpoofedAttestationRecord() {
        OverrideController controller = OverrideController.getInstance();
        if (!controller.isEnabled() || !controller.isSpoofTEEEnabled()) {
            return null;
        }

        AttestationRecord record = new AttestationRecord();

        // Security level
        record.attestationSecurityLevel = SECURITY_LEVEL_TRUSTED_ENVIRONMENT;
        record.keymasterSecurityLevel = SECURITY_LEVEL_TRUSTED_ENVIRONMENT;

        // Version info
        record.attestationVersion = 300; // KeyMint 3.0
        record.keymasterVersion = 300;

        // Root of trust
        record.verifiedBootState = VERIFIED_BOOT_GREEN;
        record.deviceLocked = true;

        // OS version from spoofed build
        String fingerprint = controller.getFingerprint();
        if (fingerprint != null && fingerprint.contains(":")) {
            try {
                String versionPart = fingerprint.split(":")[0];
                String[] parts = versionPart.split("/");
                if (parts.length >= 3) {
                    String version = parts[2]; // e.g., "15"
                    record.osVersion = parseOsVersion(version);
                }
            } catch (Exception e) {
                record.osVersion = 150000; // Default: Android 15
            }
        }

        String secPatch = controller.getSecurityPatch();
        if (secPatch != null) {
            record.osPatchLevel = parsePatchLevel(secPatch);
        }

        return record;
    }

    /**
     * Attestation record data structure.
     */
    public static class AttestationRecord {
        public int attestationVersion;
        public int attestationSecurityLevel;
        public int keymasterVersion;
        public int keymasterSecurityLevel;
        public int verifiedBootState;
        public boolean deviceLocked;
        public int osVersion;
        public int osPatchLevel;
    }

    /**
     * Parse Android version string to attestation format.
     * "15" → 150000, "14" → 140000
     */
    private static int parseOsVersion(String version) {
        try {
            String[] parts = version.split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int sub = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            return major * 10000 + minor * 100 + sub;
        } catch (Exception e) {
            return 150000;
        }
    }

    /**
     * Parse security patch date to attestation format.
     * "2025-02-05" → 20250205
     */
    private static int parsePatchLevel(String patchDate) {
        try {
            return Integer.parseInt(patchDate.replace("-", "").substring(0, 6));
        } catch (Exception e) {
            return 202502;
        }
    }
}
