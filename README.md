# Android Override

> Portable device spoofing framework for Android custom ROMs.
> Open-source logic, no keys included.

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-13%2B-green.svg)]()
[![API](https://img.shields.io/badge/API-33%2B-brightgreen.svg)]()

## What is this?

A modular, ROM-integrated framework for device identity spoofing — designed to pass Play Integrity checks on custom ROMs. Think of it as "Matrixx Override" made portable for **any** AOSP-based ROM.

**No keybox, no leaked fingerprints, no secrets** — just the tool. You provide your own keybox.

## Architecture

```
┌──────────────────────────────────────────────────┐
│                  Settings UI                      │
│        (OverrideSettings system app)              │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌────────┐ │
│  │Finger│ │Keybox│ │PerApp│ │Profi-│ │Anti-   │ │
│  │print │ │Mgr   │ │Config│ │les   │ │Detect  │ │
│  └──┬───┘ └──┬───┘ └──┬───┘ └──┬───┘ └──┬─────┘ │
└─────┼────────┼────────┼────────┼────────┼────────┘
      │        │        │        │        │
┌─────▼────────▼────────▼────────▼────────▼────────┐
│              OverrideController                    │
│         (Central config + state manager)           │
│         /data/system/override/config.json          │
└─────┬────────┬────────────────┬──────────────────┘
      │        │                │
┌─────▼──┐ ┌──▼──────────┐ ┌──▼──────────────────┐
│Props   │ │Keybox       │ │Anti-Detection        │
│Hooks   │ │Manager      │ │                      │
│        │ │             │ │• Hide packages       │
│Build.* │ │• XML parse  │ │• Filter props        │
│field   │ │• PEM keys   │ │• Hide root paths     │
│spoof   │ │• Multi-slot │ │• Clean mounts        │
│via     │ │• Health chk │ │• Suppress logs       │
│reflect │ │• Auto-fallbk│ │                      │
└─────┬──┘ └──┬──────────┘ └──────────────────────┘
      │        │
┌─────▼──┐ ┌──▼──────────┐
│Activity│ │Attestation  │
│Thread  │ │Hooks        │
│hook    │ │             │
│point   │ │• TEE level  │
│        │ │• Boot state │
│        │ │• Cert chain │
└────────┘ └─────────────┘
```

## Features

| Feature | Description |
|---------|-------------|
| 🔑 **Fingerprint Spoofing** | Override `Build.*` fields per-process (GMS-only by default) |
| 📦 **Keybox Loader** | Import user-provided keybox XML, multi-slot, named slots |
| 🛡️ **TEE Attestation** | Spoof security level, boot state, verified boot |
| 📱 **Per-App Profiles** | Different fingerprint/model per app (banking, etc.) |
| 💾 **Profiles** | Save/load/switch entire configurations |
| ✅ **PI Checker** | Built-in BASIC/DEVICE/STRONG prediction + diagnostics |
| 🔍 **Keybox Health** | Detect revocation, auto-fallback to next slot |
| 📋 **Props Database** | Built-in known working fingerprints dropdown |
| 👻 **Anti-Detection** | Hide apps, root paths, filter props, clean mounts/logcat |
| 🔄 **Auto-Fallback** | Try next keybox/fingerprint on attestation failure |
| 💽 **OTA-Safe Config** | Persist in `/data/system/override/` — survives OTA |

## Quick Start

### 1. Copy to ROM source

```bash
git clone https://github.com/ziachi/android-override.git

# Framework patches
mkdir -p frameworks/base/core/java/com/android/override/services
cp android-override/patches/frameworks_base/core/*.java \
   frameworks/base/core/java/com/android/override/
cp android-override/patches/frameworks_base/keystore/*.java \
   frameworks/base/core/java/com/android/override/
cp android-override/patches/frameworks_base/services/*.java \
   frameworks/base/core/java/com/android/override/services/

# Settings app
cp -r android-override/packages/OverrideSettings packages/apps/

# SEPolicy
cp android-override/sepolicy/* device/YOUR_DEVICE/sepolicy/
```

### 2. Add hook to ActivityThread

```java
// frameworks/base/core/java/android/app/ActivityThread.java
// In handleBindApplication(), before app.onCreate():

import com.android.override.PropsHooks;

PropsHooks.onApplicationCreated(app, data.processName);
```

### 3. Add to device makefile

```makefile
PRODUCT_PACKAGES += OverrideSettings
BOARD_SEPOLICY_DIRS += device/YOUR_DEVICE/sepolicy
```

### 4. Build

```bash
mka bacon
```

## Configuration

After flashing, configure via **Settings → System → Override** or the **Override** app:

1. **Enable** master switch
2. **Select fingerprint** from database or enter manually
3. **Import keybox** XML (your own — not included)
4. **Enable TEE spoofing** for DEVICE level
5. **Run integrity checker** to verify

### Config file location

```
/data/system/override/
├── config.json          # Main configuration
├── keybox/
│   ├── default.xml      # Active keybox
│   └── backup.xml       # Fallback keybox
└── profiles/
    ├── pixel9pro.json    # Saved profile
    └── samsung.json      # Another profile
```

## Directory Structure

```
android-override/
├── README.md
├── LICENSE                          # Apache 2.0
├── patches/
│   └── frameworks_base/
│       ├── core/
│       │   ├── OverrideController.java    # Central config manager
│       │   └── PropsHooks.java            # Build.* field hooks
│       ├── keystore/
│       │   ├── KeyboxManager.java         # Keybox XML loader
│       │   └── AttestationHooks.java      # TEE attestation spoof
│       └── services/
│           ├── AntiDetection.java         # Hide root/spoof traces
│           └── IntegrityChecker.java      # PI prediction
├── packages/
│   └── OverrideSettings/                  # Settings UI app
│       ├── Android.bp
│       ├── AndroidManifest.xml
│       ├── src/                           # Java sources
│       └── res/                           # Layouts, strings, drawables
├── config/
│   ├── props_database.xml                 # Known fingerprints
│   ├── default_config.xml                 # Config template
│   └── example_profile.xml               # Example profile
├── sepolicy/
│   ├── override.te                        # SELinux policy
│   └── file_contexts                      # File labels
└── docs/
    ├── integration-guide.md               # Full integration guide
    ├── per-app-spoofing.md                # Per-app config guide
    └── troubleshooting.md                 # Common issues + fixes
```

## Security Model

- ✅ **No keys in repo** — keybox is user-provided via Settings UI
- ✅ **No leaked fingerprints** — props database contains only public build info
- ✅ **Platform-signed** — runs as privileged system app
- ✅ **SELinux enforcing** — targeted policy rules only
- ✅ **Config in /data/** — not readable by untrusted apps

## Compatibility

| ROM Base | Status |
|----------|--------|
| AOSP 13+ | ✅ Compatible |
| AOSP 14  | ✅ Compatible |
| AOSP 15  | ✅ Compatible |
| LineageOS 21+ | ✅ Compatible |
| ProjectMatrixx | ✅ Compatible |
| PixelOS | ✅ Compatible |
| crDroid | ✅ Compatible |

## License

```
Copyright 2025 Android Override Project

Licensed under the Apache License, Version 2.0
```

See [LICENSE](LICENSE) for full text.
