/*
 * Copyright (C) 2025 Android Override Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.android.override;

import android.content.Context;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central controller for the Override spoofing framework.
 *
 * Manages all configuration: fingerprint, keybox, per-app profiles,
 * anti-detection, auto-fallback, and profile saving/loading.
 *
 * Config stored in /data/system/override/ (OTA-safe).
 *
 * Usage:
 *   OverrideController controller = OverrideController.getInstance();
 *   controller.setFingerprint("google/husky/husky:15/...");
 *   controller.setEnabled(true);
 */
public class OverrideController {

    private static final String TAG = "OverrideController";
    private static final String CONFIG_DIR = "/data/system/override";
    private static final String CONFIG_FILE = CONFIG_DIR + "/config.json";
    private static final String PROFILES_DIR = CONFIG_DIR + "/profiles";
    private static final String KEYBOX_DIR = CONFIG_DIR + "/keybox";
    private static final String PROPS_DB_FILE = CONFIG_DIR + "/props_database.json";

    private static volatile OverrideController sInstance;
    private Context mContext;

    // Configuration state
    private boolean mEnabled = true;
    private String mFingerprint = "";
    private String mModel = "";
    private String mManufacturer = "";
    private String mProduct = "";
    private String mDevice = "";
    private String mSecurityPatch = "";

    // Keybox
    private boolean mKeyboxEnabled = false;
    private String mActiveKeyboxSlot = "default";

    // TEE / Anti-detection
    private boolean mSpoofTEE = true;
    private boolean mAntiDetection = true;
    private boolean mHideApps = false;
    private boolean mAutoFallback = false;
    private String mBootloaderState = "locked";

    // Per-app configs
    private Map<String, PerAppConfig> mPerAppConfigs = new HashMap<>();

    // Hidden apps (from anti-detection)
    private Map<String, Boolean> mHiddenApps = new HashMap<>();

    // Profiles
    private String mActiveProfile = "default";

    // Props database
    private Map<String, PropsEntry> mPropsDatabase = new LinkedHashMap<>();

    private OverrideController() {}

    public static OverrideController getInstance() {
        if (sInstance == null) {
            synchronized (OverrideController.class) {
                if (sInstance == null) {
                    sInstance = new OverrideController();
                }
            }
        }
        return sInstance;
    }

    /**
     * Initialize with context (called from BootReceiver or system server).
     */
    public static void init(Context context) {
        OverrideController instance = getInstance();
        instance.mContext = context;
        instance.ensureDirectories();
        instance.loadConfig();
        instance.loadPropsDatabase();
        Log.i(TAG, "OverrideController initialized"
                + " enabled=" + instance.mEnabled
                + " fp=" + (instance.mFingerprint.length() > 20
                    ? instance.mFingerprint.substring(0, 20) + "..." : instance.mFingerprint));
    }

    // ========== Directories ==========

    private void ensureDirectories() {
        new File(CONFIG_DIR).mkdirs();
        new File(PROFILES_DIR).mkdirs();
        new File(KEYBOX_DIR).mkdirs();
    }

    // ========== Config Load/Save ==========

    private void loadConfig() {
        File configFile = new File(CONFIG_FILE);
        if (!configFile.exists()) {
            Log.d(TAG, "No config file, using defaults");
            return;
        }

        try {
            StringBuilder sb = new StringBuilder();
            FileReader reader = new FileReader(configFile);
            char[] buf = new char[4096];
            int len;
            while ((len = reader.read(buf)) > 0) sb.append(buf, 0, len);
            reader.close();

            JSONObject json = new JSONObject(sb.toString());

            mEnabled = json.optBoolean("enabled", false);
            mFingerprint = json.optString("fingerprint", "");
            mModel = json.optString("model", "");
            mManufacturer = json.optString("manufacturer", "");
            mProduct = json.optString("product", "");
            mDevice = json.optString("device", "");
            mSecurityPatch = json.optString("security_patch", "");
            mKeyboxEnabled = json.optBoolean("keybox_enabled", false);
            mActiveKeyboxSlot = json.optString("active_keybox_slot", "default");
            mSpoofTEE = json.optBoolean("spoof_tee", false);
            mAntiDetection = json.optBoolean("anti_detection", false);
            mHideApps = json.optBoolean("hide_apps", false);
            mAutoFallback = json.optBoolean("auto_fallback", false);
            mBootloaderState = json.optString("bootloader_state", "locked");
            mActiveProfile = json.optString("active_profile", "default");

            // Per-app configs
            JSONObject perApp = json.optJSONObject("per_app");
            if (perApp != null) {
                for (java.util.Iterator<String> it = perApp.keys(); it.hasNext();) {
                    String pkg = it.next();
                    JSONObject appJson = perApp.getJSONObject(pkg);
                    PerAppConfig config = new PerAppConfig(pkg);
                    config.fingerprint = appJson.optString("fingerprint", "");
                    config.model = appJson.optString("model", "");
                    config.manufacturer = appJson.optString("manufacturer", "");
                    config.product = appJson.optString("product", "");
                    config.device = appJson.optString("device", "");
                    config.spoofingEnabled = appJson.optBoolean("enabled", true);
                    mPerAppConfigs.put(pkg, config);
                }
            }

            // Hidden apps
            JSONArray hidden = json.optJSONArray("hidden_apps");
            if (hidden != null) {
                for (int i = 0; i < hidden.length(); i++) {
                    mHiddenApps.put(hidden.getString(i), true);
                }
            }

            Log.d(TAG, "Config loaded successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load config", e);
        }
    }

    public synchronized void saveConfig() {
        try {
            JSONObject json = new JSONObject();
            json.put("enabled", mEnabled);
            json.put("fingerprint", mFingerprint);
            json.put("model", mModel);
            json.put("manufacturer", mManufacturer);
            json.put("product", mProduct);
            json.put("device", mDevice);
            json.put("security_patch", mSecurityPatch);
            json.put("keybox_enabled", mKeyboxEnabled);
            json.put("active_keybox_slot", mActiveKeyboxSlot);
            json.put("spoof_tee", mSpoofTEE);
            json.put("anti_detection", mAntiDetection);
            json.put("hide_apps", mHideApps);
            json.put("auto_fallback", mAutoFallback);
            json.put("bootloader_state", mBootloaderState);
            json.put("active_profile", mActiveProfile);

            // Per-app
            JSONObject perApp = new JSONObject();
            for (Map.Entry<String, PerAppConfig> entry : mPerAppConfigs.entrySet()) {
                PerAppConfig c = entry.getValue();
                JSONObject appJson = new JSONObject();
                appJson.put("fingerprint", c.fingerprint);
                appJson.put("model", c.model);
                appJson.put("manufacturer", c.manufacturer);
                appJson.put("product", c.product);
                appJson.put("device", c.device);
                appJson.put("enabled", c.spoofingEnabled);
                perApp.put(entry.getKey(), appJson);
            }
            json.put("per_app", perApp);

            // Hidden apps
            JSONArray hidden = new JSONArray();
            for (String pkg : mHiddenApps.keySet()) {
                hidden.put(pkg);
            }
            json.put("hidden_apps", hidden);

            // Ensure config directory exists
            File configDir = new File(CONFIG_DIR);
            if (!configDir.exists()) {
                configDir.mkdirs();
            }

            FileWriter writer = new FileWriter(CONFIG_FILE);
            writer.write(json.toString(2));
            writer.close();

            Log.d(TAG, "Config saved");
        } catch (Exception e) {
            Log.e(TAG, "Failed to save config", e);
        }
    }

    // ========== Getters/Setters ==========

    public boolean isEnabled() { return true; }
    public void setEnabled(boolean enabled) { /* always on */ }

    public String getFingerprint() { return mFingerprint; }
    public void setFingerprint(String fp) { mFingerprint = fp; saveConfig(); }

    public String getModel() { return mModel; }
    public void setModel(String model) { mModel = model; saveConfig(); }

    public String getManufacturer() { return mManufacturer; }
    public void setManufacturer(String mfr) { mManufacturer = mfr; saveConfig(); }

    public String getProduct() { return mProduct; }
    public void setProduct(String product) { mProduct = product; saveConfig(); }

    public String getDevice() { return mDevice; }
    public void setDevice(String device) { mDevice = device; saveConfig(); }

    public String getSecurityPatch() { return mSecurityPatch; }
    public void setSecurityPatch(String patch) { mSecurityPatch = patch; saveConfig(); }

    // Keybox
    public boolean isKeyboxEnabled() { return mKeyboxEnabled; }
    public void setKeyboxEnabled(boolean enabled) { mKeyboxEnabled = enabled; saveConfig(); }

    public String getActiveKeyboxPath() {
        return KEYBOX_DIR + "/" + mActiveKeyboxSlot + ".xml";
    }

    // TEE
    public boolean isSpoofTEEEnabled() { return true; }
    public void setSpoofTEE(boolean enabled) { /* always on */ }

    // Anti-detection
    public boolean isAntiDetectionEnabled() { return true; }
    public void setAntiDetection(boolean enabled) { /* always on */ }

    public boolean isHideAppsEnabled() { return mHideApps; }
    public void setHideApps(boolean enabled) { mHideApps = enabled; saveConfig(); }

    // Auto-fallback
    public boolean isAutoFallbackEnabled() { return false; }
    public void setAutoFallback(boolean enabled) { mAutoFallback = enabled; saveConfig(); }

    // Bootloader
    public String getBootloaderState() { return "locked"; }
    public void setBootloaderState(String state) { /* always locked */ }

    // ========== Per-App ==========

    public PerAppConfig getPerAppConfig(String packageName) {
        return mPerAppConfigs.get(packageName);
    }

    public void setPerAppConfig(PerAppConfig config) {
        mPerAppConfigs.put(config.packageName, config);
        saveConfig();
    }

    public void removePerAppConfig(String packageName) {
        mPerAppConfigs.remove(packageName);
        saveConfig();
    }

    public Map<String, PerAppConfig> getAllPerAppConfigs() {
        return new HashMap<>(mPerAppConfigs);
    }

    /**
     * Get effective fingerprint for a process (per-app > global).
     */
    public String getEffectiveFingerprint(String processName) {
        PerAppConfig perApp = getPerAppConfig(processName);
        if (perApp != null && !TextUtils.isEmpty(perApp.fingerprint)) {
            return perApp.fingerprint;
        }
        return mFingerprint;
    }

    /**
     * Get effective model for a process (per-app > global).
     */
    public String getEffectiveModel(String processName) {
        PerAppConfig perApp = getPerAppConfig(processName);
        if (perApp != null && !TextUtils.isEmpty(perApp.model)) {
            return perApp.model;
        }
        return mModel;
    }

    // ========== Hidden Apps ==========

    public boolean isAppHidden(String packageName) {
        return mHiddenApps.containsKey(packageName);
    }

    public void addHiddenApp(String packageName) {
        mHiddenApps.put(packageName, true);
        saveConfig();
    }

    public void removeHiddenApp(String packageName) {
        mHiddenApps.remove(packageName);
        saveConfig();
    }

    public Map<String, Boolean> getHiddenApps() {
        return new HashMap<>(mHiddenApps);
    }

    // ========== Keybox Import ==========

    /**
     * Import a keybox XML to the default slot.
     */
    public boolean importKeybox(String sourcePath) {
        return importKeybox(sourcePath, mActiveKeyboxSlot);
    }

    /**
     * Import a keybox XML to a named slot.
     */
    public boolean importKeybox(String sourcePath, String slotName) {
        try {
            File source = new File(sourcePath);
            File dest = new File(KEYBOX_DIR + "/" + slotName + ".xml");

            // Validate XML format before copying
            if (!validateKeyboxXml(source)) {
                Log.e(TAG, "Invalid keybox XML format");
                return false;
            }

            // Copy file
            FileInputStream fis = new FileInputStream(source);
            FileOutputStream fos = new FileOutputStream(dest);
            byte[] buf = new byte[8192];
            int len;
            while ((len = fis.read(buf)) > 0) fos.write(buf, 0, len);
            fis.close();
            fos.close();

            // Set permissions
            dest.setReadable(true, false);
            dest.setWritable(true, true);

            mActiveKeyboxSlot = slotName;
            saveConfig();
            Log.i(TAG, "Keybox imported to slot: " + slotName);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to import keybox", e);
            return false;
        }
    }

    /**
     * Validate keybox XML has required structure.
     */
    private boolean validateKeyboxXml(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(fis, "UTF-8");
            int eventType = parser.getEventType();

            boolean hasKeybox = false;
            boolean hasPrivateKey = false;
            boolean hasCertificate = false;

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String name = parser.getName();
                    if ("Keybox".equals(name)) hasKeybox = true;
                    else if ("PrivateKey".equals(name)) hasPrivateKey = true;
                    else if ("Certificate".equals(name)) hasCertificate = true;
                }
                eventType = parser.next();
            }

            return hasKeybox && hasPrivateKey && hasCertificate;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * List available keybox slots.
     */
    public String[] listKeyboxSlots() {
        File dir = new File(KEYBOX_DIR);
        if (!dir.exists()) return new String[0];

        File[] files = dir.listFiles((d, name) -> name.endsWith(".xml"));
        if (files == null) return new String[0];

        String[] slots = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            slots[i] = files[i].getName().replace(".xml", "");
        }
        return slots;
    }

    // ========== Profiles ==========

    public String getActiveProfile() { return mActiveProfile; }

    /**
     * Save current config as a named profile.
     */
    public boolean saveProfile(String name) {
        try {
            // Read current config and save as profile
            File configFile = new File(CONFIG_FILE);
            File profileFile = new File(PROFILES_DIR + "/" + name + ".json");

            if (configFile.exists()) {
                FileInputStream fis = new FileInputStream(configFile);
                FileOutputStream fos = new FileOutputStream(profileFile);
                byte[] buf = new byte[8192];
                int len;
                while ((len = fis.read(buf)) > 0) fos.write(buf, 0, len);
                fis.close();
                fos.close();
            }

            mActiveProfile = name;
            saveConfig();
            Log.i(TAG, "Profile saved: " + name);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to save profile: " + name, e);
            return false;
        }
    }

    /**
     * Load a named profile.
     */
    public boolean loadProfile(String name) {
        File profileFile = new File(PROFILES_DIR + "/" + name + ".json");
        if (!profileFile.exists()) {
            Log.w(TAG, "Profile not found: " + name);
            return false;
        }

        try {
            // Copy profile to config
            FileInputStream fis = new FileInputStream(profileFile);
            FileOutputStream fos = new FileOutputStream(CONFIG_FILE);
            byte[] buf = new byte[8192];
            int len;
            while ((len = fis.read(buf)) > 0) fos.write(buf, 0, len);
            fis.close();
            fos.close();

            // Reload
            loadConfig();
            mActiveProfile = name;
            saveConfig();
            Log.i(TAG, "Profile loaded: " + name);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to load profile: " + name, e);
            return false;
        }
    }

    /**
     * Delete a profile.
     */
    public void deleteProfile(String name) {
        new File(PROFILES_DIR + "/" + name + ".json").delete();
    }

    /**
     * List all saved profiles.
     */
    public String[] listProfiles() {
        File dir = new File(PROFILES_DIR);
        if (!dir.exists()) return new String[0];

        File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
        if (files == null) return new String[0];

        String[] names = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            names[i] = files[i].getName().replace(".json", "");
        }
        return names;
    }

    // ========== Props Database ==========

    private void loadPropsDatabase() {
        // Built-in database of known working device fingerprints
        mPropsDatabase.put("Pixel 9 Pro (husky)", new PropsEntry(
                "google/husky/husky:15/AP4A.250205.002/12650890:user/release-keys",
                "Pixel 9 Pro", "Google", "husky", "husky", "2025-02-05"));

        mPropsDatabase.put("Pixel 8 (shiba)", new PropsEntry(
                "google/shiba/shiba:15/AP4A.250205.002/12650890:user/release-keys",
                "Pixel 8", "Google", "shiba", "shiba", "2025-02-05"));

        mPropsDatabase.put("Pixel 8a (akita)", new PropsEntry(
                "google/akita/akita:15/AP4A.250205.002/12650890:user/release-keys",
                "Pixel 8a", "Google", "akita", "akita", "2025-02-05"));

        mPropsDatabase.put("Pixel 7 Pro (cheetah)", new PropsEntry(
                "google/cheetah/cheetah:15/AP4A.250205.002/12650890:user/release-keys",
                "Pixel 7 Pro", "Google", "cheetah", "cheetah", "2025-02-05"));

        mPropsDatabase.put("Samsung S24 Ultra", new PropsEntry(
                "samsung/dm3q/dm3q:15/AP4A.250105.002/S928BXXS6CXL3:user/release-keys",
                "SM-S928B", "samsung", "dm3q", "dm3q", "2025-01-05"));

        mPropsDatabase.put("Samsung S23 Ultra", new PropsEntry(
                "samsung/dm1q/dm1q:15/AP4A.250105.002/S918BXXS8DXK2:user/release-keys",
                "SM-S918B", "samsung", "dm1q", "dm1q", "2025-01-05"));

        mPropsDatabase.put("OnePlus 12", new PropsEntry(
                "oneplus/CPH2583/OP5913L1:15/AP4A.250105.002/U.16.0.3:user/release-keys",
                "CPH2583", "OnePlus", "CPH2583", "OP5913L1", "2025-01-05"));

        mPropsDatabase.put("Xiaomi 14", new PropsEntry(
                "xiaomi/houji/houji:15/AP4A.250105.002/OS2.0.6.0.VNCCNXM:user/release-keys",
                "24090RA29C", "Xiaomi", "houji", "houji", "2025-01-05"));

        // Load custom database from file if exists
        File dbFile = new File(PROPS_DB_FILE);
        if (dbFile.exists()) {
            try {
                StringBuilder sb = new StringBuilder();
                FileReader reader = new FileReader(dbFile);
                char[] buf = new char[4096];
                int len;
                while ((len = reader.read(buf)) > 0) sb.append(buf, 0, len);
                reader.close();

                JSONObject dbJson = new JSONObject(sb.toString());
                for (java.util.Iterator<String> it = dbJson.keys(); it.hasNext();) {
                    String key = it.next();
                    JSONObject entry = dbJson.getJSONObject(key);
                    mPropsDatabase.put(key, new PropsEntry(
                            entry.optString("fingerprint"),
                            entry.optString("model"),
                            entry.optString("manufacturer"),
                            entry.optString("product"),
                            entry.optString("device"),
                            entry.optString("security_patch")));
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to load custom props database", e);
            }
        }
    }

    public Map<String, PropsEntry> getPropsDatabase() {
        return new LinkedHashMap<>(mPropsDatabase);
    }

    /**
     * Apply a props database entry to current config.
     */
    public void applyPropsEntry(String label) {
        PropsEntry entry = mPropsDatabase.get(label);
        if (entry != null) {
            mFingerprint = entry.fingerprint;
            mModel = entry.model;
            mManufacturer = entry.manufacturer;
            mProduct = entry.product;
            mDevice = entry.device;
            mSecurityPatch = entry.securityPatch;
            saveConfig();
            Log.i(TAG, "Applied props entry: " + label);
        }
    }

    // ========== Auto-Fallback ==========

    /**
     * Try next keybox slot or fingerprint when current fails.
     */
    public void tryFallback() {
        String[] slots = listKeyboxSlots();
        if (slots.length <= 1) {
            Log.w(TAG, "No fallback keybox available");
            return;
        }

        // Find current slot index and try next
        for (int i = 0; i < slots.length; i++) {
            if (slots[i].equals(mActiveKeyboxSlot)) {
                int nextIndex = (i + 1) % slots.length;
                mActiveKeyboxSlot = slots[nextIndex];
                saveConfig();
                Log.i(TAG, "Fallback to keybox slot: " + mActiveKeyboxSlot);

                // Reload keybox
                KeyboxManager.getInstance().reload();
                return;
            }
        }
    }

    // ========== Data Classes ==========

    /**
     * Per-app spoofing configuration.
     */
    public static class PerAppConfig {
        public String packageName;
        public String fingerprint = "";
        public String model = "";
        public String manufacturer = "";
        public String product = "";
        public String device = "";
        public boolean spoofingEnabled = true;

        public PerAppConfig(String packageName) {
            this.packageName = packageName;
        }
    }

    /**
     * Props database entry (known working fingerprints).
     */
    public static class PropsEntry {
        public String fingerprint;
        public String model;
        public String manufacturer;
        public String product;
        public String device;
        public String securityPatch;

        public PropsEntry(String fp, String model, String mfr,
                          String product, String device, String secPatch) {
            this.fingerprint = fp;
            this.model = model;
            this.manufacturer = mfr;
            this.product = product;
            this.device = device;
            this.securityPatch = secPatch;
        }
    }
}
