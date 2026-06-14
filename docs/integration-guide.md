# Integration Guide

How to integrate `android-override` into your custom ROM.

## Prerequisites

- AOSP / LineageOS / Matrixx based ROM source tree
- Android 13+ (API 33+), recommended Android 14-15
- Platform signing keys access

## Quick Integration (3 Steps)

### Step 1: Copy Framework Patches

Copy the framework patches into your ROM source tree:

```bash
# From your ROM root directory
cp -r android-override/patches/frameworks_base/core/OverrideController.java \
      frameworks/base/core/java/com/android/override/

cp -r android-override/patches/frameworks_base/core/PropsHooks.java \
      frameworks/base/core/java/com/android/override/

cp -r android-override/patches/frameworks_base/keystore/KeyboxManager.java \
      frameworks/base/core/java/com/android/override/

cp -r android-override/patches/frameworks_base/keystore/AttestationHooks.java \
      frameworks/base/core/java/com/android/override/

cp -r android-override/patches/frameworks_base/services/AntiDetection.java \
      frameworks/base/core/java/com/android/override/

cp -r android-override/patches/frameworks_base/services/IntegrityChecker.java \
      frameworks/base/core/java/com/android/override/services/
```

### Step 2: Add Hook Call

Add the PropsHooks call to `ActivityThread.handleBindApplication()`:

```java
// In frameworks/base/core/java/android/app/ActivityThread.java
// Inside handleBindApplication(), before app.onCreate():

import com.android.override.PropsHooks;

// ... existing code ...

// Add this line:
PropsHooks.onApplicationCreated(app, data.processName);

// ... rest of onCreate() ...
```

### Step 3: Copy Settings App

```bash
# Copy OverrideSettings app
cp -r android-override/packages/OverrideSettings/ \
      packages/apps/OverrideSettings/

# Copy SEPolicy
cp android-override/sepolicy/override.te \
   device/YOUR_DEVICE/sepolicy/
cp android-override/sepolicy/file_contexts \
   device/YOUR_DEVICE/sepolicy/
```

Add to your device makefile:

```makefile
# In device.mk or common.mk
PRODUCT_PACKAGES += OverrideSettings

# SEPolicy
BOARD_SEPOLICY_DIRS += device/YOUR_DEVICE/sepolicy
```

## Build & Flash

```bash
# Build
source build/envsetup.sh
lunch your_device-userdebug
mka bacon

# Flash and enjoy
```

## Configuration

After flashing, go to **Settings → System → Override** or open the **Override** app.

1. Enable master switch
2. Select fingerprint from database or enter manually
3. Import keybox XML (optional, for DEVICE level PI)
4. Enable TEE spoofing
5. Configure anti-detection
6. Run integrity checker to verify

## Advanced: Cherry-Pick Integration

If you want to integrate specific features only:

### Fingerprint Spoofing Only

Copy only:
- `OverrideController.java`
- `PropsHooks.java`

Add the hook call to ActivityThread. This gives you Build.* field spoofing
without keybox or anti-detection.

### Keybox Only

Copy:
- `OverrideController.java`
- `KeyboxManager.java`
- `AttestationHooks.java`

Requires additional integration with KeyMint HAL.

## Troubleshooting

See [troubleshooting.md](troubleshooting.md) for common issues.
