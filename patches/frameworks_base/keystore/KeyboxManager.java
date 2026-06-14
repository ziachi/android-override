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
import java.util.regex.Pattern;

/**
 * Manages keybox loading and injection for hardware attestation spoofing.
 *
 * Supports keybox XML with:
 * - Multiple Key elements (ECDSA + RSA) — uses first key found
 * - HTML comments embedded in PEM data (stripped automatically)
 * - Standard AndroidAttestation format
 */
public class KeyboxManager {

    private static final String TAG = "KeyboxManager";
    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->");

    private static volatile KeyboxManager sInstance;

    private PrivateKey mPrivateKey;
    private List<X509Certificate> mCertChain;
    private String mDeviceId;
    private String mAlgorithm;
    private boolean mLoaded = false;
    private boolean mRevoked = false;
    private long mLastHealthCheck = 0;
    private static final long HEALTH_CHECK_INTERVAL = 24 * 60 * 60 * 1000;

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
                    + " deviceId=" + (mDeviceId != null ?
                        mDeviceId.substring(0, Math.min(8, mDeviceId.length())) + "..." : "null"));
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to load keybox from: " + path, e);
            mLoaded = false;
            return false;
        }
    }

    private void parseKeyboxXml(File file) throws Exception {
        mCertChain = new ArrayList<>();
        mPrivateKey = null;
        mDeviceId = null;
        mAlgorithm = "ecdsa";

        boolean firstKeyParsed = false;
        boolean insideFirstKey = false;

        try (FileInputStream fis = new FileInputStream(file)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(fis, "UTF-8");
            int eventType = parser.getEventType();

            StringBuilder textBuilder = new StringBuilder();

            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        textBuilder.setLength(0);
                        String startTag = parser.getName();

                        if ("Keybox".equals(startTag)) {
                            mDeviceId = parser.getAttributeValue(null, "DeviceID");
                        } else if ("Key".equals(startTag)) {
                            if (!firstKeyParsed) {
                                insideFirstKey = true;
                                String algo = parser.getAttributeValue(null, "algorithm");
                                if (!TextUtils.isEmpty(algo)) {
                                    mAlgorithm = algo.toLowerCase();
                                }
                            }
                        }
                        break;

                    case XmlPullParser.TEXT:
                        if (insideFirstKey || !firstKeyParsed) {
                            textBuilder.append(parser.getText());
                        }
                        break;

                    case XmlPullParser.END_TAG:
                        String endTag = parser.getName();
                        String text = textBuilder.toString().trim();

                        if ("Key".equals(endTag)) {
                            if (insideFirstKey) {
                                firstKeyParsed = true;
                                insideFirstKey = false;
                            }
                        } else if (insideFirstKey) {
                            if ("PrivateKey".equals(endTag) && !TextUtils.isEmpty(text)) {
                                mPrivateKey = parsePrivateKey(
                                        stripHtmlComments(text), mAlgorithm);
                            } else if ("Certificate".equals(endTag) && !TextUtils.isEmpty(text)) {
                                X509Certificate cert = parseCertificate(
                                        stripHtmlComments(text));
                                if (cert != null) {
                                    mCertChain.add(cert);
                                }
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
     * Strip HTML comments from PEM data.
     * Keybox files from some sources embed comments like
     * &lt;!--https://t.me/example--&gt; inside PEM blocks.
     */
    private String stripHtmlComments(String text) {
        return HTML_COMMENT.matcher(text).replaceAll("");
    }

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

        // Try specified algorithm first, then fallback
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(keyAlgo);
            return keyFactory.generatePrivate(keySpec);
        } catch (Exception e) {
            // Fallback: try the other algorithm
            String fallback = "EC".equals(keyAlgo) ? "RSA" : "EC";
            try {
                KeyFactory keyFactory = KeyFactory.getInstance(fallback);
                PrivateKey key = keyFactory.generatePrivate(keySpec);
                mAlgorithm = fallback.toLowerCase();
                return key;
            } catch (Exception e2) {
                throw new Exception("Failed to parse key as " + keyAlgo + " or " + fallback, e2);
            }
        }
    }

    private X509Certificate parseCertificate(String pem) {
        try {
            String cleaned = pem.trim();
            if (!cleaned.startsWith("-----BEGIN")) {
                cleaned = "-----BEGIN CERTIFICATE-----\n" + cleaned
                        + "\n-----END CERTIFICATE-----";
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

        sb.append("Status: ").append(mRevoked ? "REVOKED" : "Active");
        return sb.toString();
    }

    // ========== Health Check ==========
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
            X509Certificate leaf = mCertChain.get(0);
            leaf.checkValidity();

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

            OverrideController controller = OverrideController.getInstance();
            if (controller.isAutoFallbackEnabled()) {
                Log.i(TAG, "Keybox revoked, attempting auto-fallback...");
                controller.tryFallback();
            }

            return false;
        }
    }

    public void markRevoked() {
        mRevoked = true;
        Log.w(TAG, "Keybox marked as revoked");
    }

    public void reload() {
        sInstance = null;
        getInstance();
    }
}
