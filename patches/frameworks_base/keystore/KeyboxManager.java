/*
 * Copyright (C) 2025 Android Override Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.android.override;

import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages keybox loading and injection for hardware attestation spoofing.
 *
 * Loads user-provided keybox XML containing:
 * - EC private key for attestation signing
 * - Certificate chain (device → intermediate → root)
 *
 * The keybox is injected into the KeyMint/Keymaster HAL response
 * to make the device appear to have valid hardware attestation.
 *
 * XML Format:
 * <AndroidAttestation>
 *   <NumberOfKeyboxes>1</NumberOfKeyboxes>
 *   <Keybox DeviceID="...">
 *     <Key algorithm="ecdsa|rsa">
 *       <PrivateKey format="pem">-----BEGIN EC PRIVATE KEY-----...-----END EC PRIVATE KEY-----</PrivateKey>
 *       <CertificateChain>
 *         <NumberOfCertificates>N</NumberOfCertificates>
 *         <Certificate format="pem">-----BEGIN CERTIFICATE-----...-----END CERTIFICATE-----</Certificate>
 *         ...
 *       </CertificateChain>
 *     </Key>
 *   </Keybox>
 * </AndroidAttestation>
 */
public class KeyboxManager {

    private static final String TAG = "KeyboxManager";

    private static volatile KeyboxManager sInstance;

    private PrivateKey mPrivateKey;
    private List<X509Certificate> mCertChain;
    private String mDeviceId;
    private String mAlgorithm;
    private boolean mLoaded = false;
    private boolean mRevoked = false;
    private long mLastHealthCheck = 0;
    private static final long HEALTH_CHECK_INTERVAL = 24 * 60 * 60 * 1000; // 24h

    private KeyboxManager() {
        loadActiveKeybox();
    }

    public static KeyboxManager getInstance() {
        if (sInstance == null) {
            synchronized (KeyboxManager.class) {
                if (sInstance == null) {
                    sInstance = new KeyboxManager();
                }
            }
        }
        return sInstance;
    }

    /**
     * Load the active keybox from config directory.
     */
    public synchronized boolean loadActiveKeybox() {
        OverrideController controller = OverrideController.getInstance();
        if (!controller.isKeyboxEnabled()) {
            Log.d(TAG, "Keybox disabled");
            mLoaded = false;
            return false;
        }

        String keyboxPath = controller.getActiveKeyboxPath();
        return loadKeybox(keyboxPath);
    }

    /**
     * Load a keybox from a specific path.
     */
    public synchronized boolean loadKeybox(String path) {
        if (TextUtils.isEmpty(path)) {
            Log.w(TAG, "No keybox path specified");
            return false;
        }

        File keyboxFile = new File(path);
        if (!keyboxFile.exists()) {
            Log.w(TAG, "Keybox file not found: " + path);
            return false;
        }

        try {
            parseKeyboxXml(keyboxFile);
            mLoaded = true;
            mRevoked = false;
            Log.i(TAG, "Keybox loaded successfully"
                    + " algo=" + mAlgorithm
                    + " certs=" + (mCertChain != null ? mCertChain.size() : 0)
                    + " deviceId=" + (mDeviceId != null ? mDeviceId.substring(0, Math.min(8, mDeviceId.length())) + "..." : "null"));
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to load keybox from: " + path, e);
            mLoaded = false;
            return false;
        }
    }

    /**
     * Parse keybox XML file.
     */
    private void parseKeyboxXml(File file) throws Exception {
        mCertChain = new ArrayList<>();
        mPrivateKey = null;
        mDeviceId = null;
        mAlgorithm = "ecdsa";

        try (FileInputStream fis = new FileInputStream(file)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(fis, "UTF-8");
            int eventType = parser.getEventType();

            String currentTag = "";
            StringBuilder textBuilder = new StringBuilder();

            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        currentTag = parser.getName();
                        textBuilder.setLength(0);

                        if ("Keybox".equals(currentTag)) {
                            mDeviceId = parser.getAttributeValue(null, "DeviceID");
                        } else if ("Key".equals(currentTag)) {
                            String algo = parser.getAttributeValue(null, "algorithm");
                            if (!TextUtils.isEmpty(algo)) {
                                mAlgorithm = algo.toLowerCase();
                            }
                        }
                        break;

                    case XmlPullParser.TEXT:
                        textBuilder.append(parser.getText());
                        break;

                    case XmlPullParser.END_TAG:
                        String text = textBuilder.toString().trim();

                        if ("PrivateKey".equals(parser.getName()) && !TextUtils.isEmpty(text)) {
                            mPrivateKey = parsePrivateKey(text, mAlgorithm);
                        } else if ("Certificate".equals(parser.getName()) && !TextUtils.isEmpty(text)) {
                            X509Certificate cert = parseCertificate(text);
                            if (cert != null) {
                                mCertChain.add(cert);
                            }
                        }
                        textBuilder.setLength(0);
                        break;
                }
                eventType = parser.next();
            }
        }

        if (mPrivateKey == null) {
            throw new Exception("No private key found in keybox XML");
        }
        if (mCertChain.isEmpty()) {
            throw new Exception("No certificates found in keybox XML");
        }
    }

    /**
     * Parse PEM-encoded private key.
     */
    private PrivateKey parsePrivateKey(String pem, String algorithm) throws Exception {
        String cleaned = pem
                .replace("-----BEGIN EC PRIVATE KEY-----", "")
                .replace("-----END EC PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");

        byte[] keyBytes = Base64.decode(cleaned, Base64.DEFAULT);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);

        String keyAlgo = "ecdsa".equals(algorithm) || "ec".equals(algorithm) ? "EC" : "RSA";
        KeyFactory keyFactory = KeyFactory.getInstance(keyAlgo);
        return keyFactory.generatePrivate(keySpec);
    }

    /**
     * Parse PEM-encoded X.509 certificate.
     */
    private X509Certificate parseCertificate(String pem) {
        try {
            String cleaned = pem.trim();
            if (!cleaned.startsWith("-----BEGIN")) {
                cleaned = "-----BEGIN CERTIFICATE-----\n" + cleaned + "\n-----END CERTIFICATE-----";
            }
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(
                    new ByteArrayInputStream(cleaned.getBytes("UTF-8")));
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse certificate", e);
            return null;
        }
    }

    // ========== Getters ==========

    public boolean isLoaded() { return mLoaded; }
    public boolean isRevoked() { return mRevoked; }
    public PrivateKey getPrivateKey() { return mPrivateKey; }
    public List<X509Certificate> getCertificateChain() { return mCertChain; }
    public String getDeviceId() { return mDeviceId; }
    public String getAlgorithm() { return mAlgorithm; }

    /**
     * Get certificate chain as byte arrays (for HAL injection).
     */
    public List<byte[]> getCertificateChainEncoded() {
        List<byte[]> encoded = new ArrayList<>();
        if (mCertChain != null) {
            for (X509Certificate cert : mCertChain) {
                try {
                    encoded.add(cert.getEncoded());
                } catch (Exception e) {
                    Log.e(TAG, "Failed to encode certificate", e);
                }
            }
        }
        return encoded;
    }

    /**
     * Get info about the loaded keybox for display in Settings.
     */
    public String getKeyboxInfo() {
        if (!mLoaded) return "No keybox loaded";

        StringBuilder sb = new StringBuilder();
        sb.append("Algorithm: ").append(mAlgorithm.toUpperCase()).append("\n");
        sb.append("Device ID: ").append(mDeviceId != null ? mDeviceId : "N/A").append("\n");
        sb.append("Certificates: ").append(mCertChain != null ? mCertChain.size() : 0).append("\n");

        if (mCertChain != null && !mCertChain.isEmpty()) {
            X509Certificate leaf = mCertChain.get(0);
            sb.append("Subject: ").append(leaf.getSubjectDN().getName()).append("\n");
            sb.append("Issuer: ").append(leaf.getIssuerDN().getName()).append("\n");
            sb.append("Valid until: ").append(leaf.getNotAfter().toString()).append("\n");
        }

        sb.append("Status: ").append(mRevoked ? "⚠️ REVOKED" : "✅ Active");
        return sb.toString();
    }

    // ========== Health Check ==========

    /**
     * Check if keybox is still valid (not revoked by Google).
     * Called periodically or after PI check failure.
     *
     * @return true if healthy, false if revoked
     */
    public boolean checkHealth() {
        long now = System.currentTimeMillis();
        if (now - mLastHealthCheck < HEALTH_CHECK_INTERVAL) {
            return !mRevoked;
        }
        mLastHealthCheck = now;

        if (!mLoaded || mCertChain == null || mCertChain.isEmpty()) {
            return false;
        }

        try {
            // Check certificate validity
            X509Certificate leaf = mCertChain.get(0);
            leaf.checkValidity();

            // Verify chain if we have multiple certs
            if (mCertChain.size() >= 2) {
                for (int i = 0; i < mCertChain.size() - 1; i++) {
                    mCertChain.get(i).verify(mCertChain.get(i + 1).getPublicKey());
                }
            }

            Log.d(TAG, "Keybox health check: OK");
            mRevoked = false;
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Keybox health check failed: " + e.getMessage());
            mRevoked = true;

            // Try auto-fallback
            OverrideController controller = OverrideController.getInstance();
            if (controller.isAutoFallbackEnabled()) {
                Log.i(TAG, "Keybox revoked, attempting auto-fallback...");
                controller.tryFallback();
            }

            return false;
        }
    }

    /**
     * Force mark keybox as revoked (e.g. after PI check failure).
     */
    public void markRevoked() {
        mRevoked = true;
        Log.w(TAG, "Keybox marked as revoked");
    }

    /**
     * Reload keybox (e.g. after import or fallback).
     */
    public void reload() {
        sInstance = null; // Force re-init
        getInstance();
    }
}
