# Android Override

> Device identity management framework for Android custom ROM development.
> A research and development toolkit for ROM maintainers.

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-13%2B-green.svg)]()
[![API](https://img.shields.io/badge/API-33%2B-brightgreen.svg)]()

## ⚠️ Disclaimer

This project is provided **strictly for educational and research purposes**. It is intended for custom ROM developers and security researchers who need to understand and manage device identity properties in AOSP-based builds.

**This project does NOT:**
- Include any private keys, certificates, or keybox files
- Distribute any proprietary or copyrighted material
- Encourage or facilitate any violation of terms of service
- Bypass any digital rights management (DRM) protections

**Users are solely responsible** for how they use this framework and must comply with all applicable laws and terms of service in their jurisdiction. The authors assume no liability for misuse.

## What is this?

A modular, ROM-integrated framework for managing device identity properties at the system level — designed for custom ROM developers who need to configure `Build.*` fields, manage attestation certificates, and handle per-application device property configurations.

This is comparable to how custom ROM projects like LineageOS, ProtonAOSP, and others manage device identity in their source trees.

**No keys included** — this is a tool only. Users must provide their own configuration.

## Use Cases

- **ROM Development & Testing** — Test how different device configurations affect app compatibility
- **Security Research** — Study device attestation mechanisms and identity management
- **Device Configuration** — Manage device properties for custom ROM builds
- **Compatibility Testing** — Verify app behavior across different device profiles

## Architecture

```
┌──────────────────────────────────────────────────┐
│                  Settings UI                      │
│        (OverrideSettings system app)              │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌────────┐ │
│  │Device│ │Cert  │ │PerApp│ │Profi-│ │System  │ │
│  │Props │ │Mgmt  │ │Config│ │les   │ │Health  │ │
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
│Props   │ │Certificate  │ │Environment          │
│Manager │ │Manager      │ │Manager              │
│        │ │             │ │                      │
│Build.* │ │• XML parse  │ │• Filter properties  │
│field   │ │• PEM keys   │ │• Clean environment  │
│config  │ │• Multi-slot │ │• Manage visibility   │
│via     │ │• Health chk │ │• Suppress debug logs │
│reflect │ │• Rotation   │ │                      │
└─────┬──┘ └──┬──────────┘ └──────────────────────┘
      │        │
┌─────▼──┐ ┌──▼──────────┐
│App     │ │Attestation  │
│Process │ │Config       │
│hook    │ │             │
│point   │ │• TEE config │
│        │ │• Boot state │
│        │ │• Cert chain │
└────────┘ └─────────────┘
```

## Features

| Feature | Description |
|---------|-------------|
| 🔧 **Property Management** | Configure `Build.*` fields per-process for ROM development |
| 📦 **Certificate Manager** | Import and manage attestation certificates (user-provided) |
| 🛡️ **Attestation Config** | Configure attestation parameters (security level, boot state) |
| 📱 **Per-App Configuration** | Different device properties per application for testing |
| 💾 **Profiles** | Save/load/switch entire device configurations |
| ✅ **Health Checker** | Built-in diagnostics for configuration validation |
| 🔍 **Certificate Health** | Validate certificate chain integrity |
| 📋 **Device Database** | Built-in device property presets (public build info) |
| 🔄 **Auto-Rotation** | Rotate certificate slots on validation failure |
| 💽 **OTA-Safe Config** | Persist in `/data/system/override/` — survives updates |

## Quick Start

### 1. Copy to ROM source

```bash
git clone https://github.com/ziachi/android-override.git

# Framework components
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

After building, configure via **Settings → System → Override**:

1. **Enable** master switch
2. **Select device profile** from database or enter manually
3. **Import certificates** (your own — not included)
4. **Configure attestation** parameters
5. **Run health checker** to validate configuration

## Directory Structure

```
android-override/
├── README.md
├── LICENSE                          # Apache 2.0
├── patches/
│   └── frameworks_base/
│       ├── core/
│       │   ├── OverrideController.java    # Central config manager
│       │   └── PropsHooks.java            # Build.* field configuration
│       ├── keystore/
│       │   ├── KeyboxManager.java         # Certificate manager
│       │   └── AttestationHooks.java      # Attestation configuration
│       └── services/
│           ├── AntiDetection.java         # Environment management
│           └── IntegrityChecker.java      # Health diagnostics
├── packages/
│   └── OverrideSettings/                  # Settings UI app
│       ├── Android.bp
│       ├── AndroidManifest.xml
│       ├── src/                           # Java sources
│       └── res/                           # Layouts, strings, drawables
├── config/
│   ├── props_database.xml                 # Device property presets
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

- ✅ **No keys in repo** — certificates are user-provided via Settings UI
- ✅ **No proprietary data** — device database contains only public build information
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
| Any AOSP-based ROM | ✅ Compatible |

## Contributing

Contributions are welcome. Please ensure all contributions follow the project's security model — no keys, certificates, or proprietary data.

## License

```
Copyright 2025 Android Override Project

Licensed under the Apache License, Version 2.0
```

See [LICENSE](LICENSE) for full text.
