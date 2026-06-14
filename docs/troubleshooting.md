# Troubleshooting Guide

## Common Issues

### PI Check: BASIC fails

**Symptoms:** Play Integrity returns no verdict or BASIC fails.

**Checklist:**
1. ✅ Override master switch is ON
2. ✅ Valid fingerprint is set (not empty)
3. ✅ Google Play Services (GMS) is installed
4. ✅ Google Services Framework is installed
5. ✅ Play Store is installed and updated
6. ✅ Device has internet connection

**Fix:** Set a known working fingerprint from the database dropdown.

---

### PI Check: DEVICE fails (BASIC passes)

**Symptoms:** BASIC ✅ but DEVICE ❌

**Checklist:**
1. ✅ Keybox is imported and loaded
2. ✅ Keybox is NOT revoked (check health)
3. ✅ TEE spoofing is enabled
4. ✅ Bootloader state set to "locked"
5. ✅ Anti-detection is enabled

**Common causes:**
- Keybox revoked by Google → import new keybox
- TEE spoofing disabled → enable in Anti-Detection
- SELinux is Permissive → must be Enforcing

---

### PI Check: STRONG always fails

This is **expected**. STRONG level requires real hardware attestation
with a StrongBox secure element. Software spoofing cannot pass STRONG.

---

### Keybox Import Fails

**"Invalid keybox XML":**
- File must be valid XML
- Must contain `<Keybox>`, `<PrivateKey>`, `<Certificate>` tags
- PEM-encoded keys and certificates
- Check file isn't corrupted or truncated

**"Keybox not loading":**
- Check file permissions: `chmod 644`
- Check SELinux: `ls -Z /data/system/override/keybox/`
- Should be labeled `u:object_r:override_config_file:s0`

---

### Build.* Not Being Spoofed

**Check process targeting:**
- Only GMS processes are spoofed by default
- Add apps to per-app config to spoof them
- Check exempt list (settings, systemui, shell are never spoofed)

**Verify with:**
```bash
# Check what GMS sees
adb shell "dumpsys package com.google.android.gms | grep versionName"

# Check Build.FINGERPRINT from app
adb shell am start -a android.intent.action.VIEW \
  -d "https://play.google.com/store/apps" \
  com.google.android.gms
```

---

### Settings App Not Showing

**Check build:**
```bash
# Verify app is in the build
ls system_ext/priv-app/OverrideSettings/

# Check if privileged permissions are whitelisted
cat system_ext/etc/permissions/privapp-permissions-override.xml
```

**Fix:**
- Ensure `PRODUCT_PACKAGES += OverrideSettings` in device makefile
- Clean build: `mka installclean && mka bacon`

---

### SELinux Denials

**Check for denials:**
```bash
adb shell dmesg | grep -i "avc.*override"
adb shell "cat /proc/kmsg" | grep override
```

**Add targeted rules** (never set Permissive):
```
# In your device sepolicy/override.te
allow <source> override_config_file:<class> { <permissions> };
```

---

### OTA Wipe Concern

Override config is stored in `/data/system/override/` which:
- ✅ Survives dirty flash / OTA update
- ❌ Wiped on factory reset / format data

**Backup before format:**
```bash
adb pull /data/system/override/ ./override_backup/
# After flashing:
adb push ./override_backup/ /data/system/override/
```

---

## Debug Commands

```bash
# Check if Override is active
adb shell getprop persist.sys.override.enabled

# View override logs
adb logcat -s OverrideController:* PropsHooks:* KeyboxManager:* AttestationHooks:*

# Check current Build values from GMS perspective
adb shell "run-as com.google.android.gms cat /proc/self/status"

# Verify keybox
adb shell ls -la /data/system/override/keybox/

# Check SELinux mode
adb shell getenforce
```

## Getting Help

1. Run the built-in **Integrity Checker** for diagnostics
2. Check logcat for `OverrideController` / `PropsHooks` tags
3. Verify all components with the checklist above
