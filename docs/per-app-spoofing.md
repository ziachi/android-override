# Per-App Spoofing Guide

## Overview

Per-app spoofing lets you configure different device identities
for different apps. This is useful when:

- Banking apps require Samsung fingerprint
- Google apps need Pixel fingerprint
- Some apps should NOT be spoofed
- Different apps need different Play Integrity levels

## How It Works

```
App starts → ActivityThread.handleBindApplication()
           → PropsHooks.onApplicationCreated()
           → Check per-app config for this package
           → Apply per-app fingerprint (or fall back to global)
           → Build.* fields are overridden via reflection
```

## Configuration

### Via Settings UI

1. Open **Override → Per-App Profiles**
2. Tap **Add App**
3. Select the app from the list
4. Configure:
   - **Fingerprint** — leave empty to use global
   - **Model** — device model name
   - **Manufacturer** — OEM name
   - **Enabled** — toggle spoofing on/off for this app

### Via Config File

Edit `/data/system/override/config.json`:

```json
{
  "per_app": {
    "com.whatsapp": {
      "fingerprint": "",
      "model": "",
      "enabled": false
    },
    "com.bank.app": {
      "fingerprint": "samsung/dm3q/dm3q:15/...",
      "model": "SM-S928B",
      "manufacturer": "samsung",
      "enabled": true
    }
  }
}
```

## Priority Order

```
Per-app config → Global config → Real device values
```

1. If per-app config exists AND is enabled → use per-app values
2. If per-app value is empty → fall back to global
3. If global value is empty → use real device value

## Examples

### Banking App: Use Samsung Identity
```
Package: com.bank.mybank
Fingerprint: samsung/dm3q/dm3q:15/AP4A.250105.002/...
Model: SM-S928B
Manufacturer: samsung
```

### WhatsApp: No Spoofing
```
Package: com.whatsapp
Enabled: false
```

### Google Apps: Use Pixel (Global)
Leave global config as Pixel → all Google apps automatically use it.

## Exempt Processes

These processes are NEVER spoofed (hardcoded):
- `com.android.settings`
- `com.android.systemui`
- `com.android.shell`
- `android` (system)

## Notes

- Changes take effect on next app restart
- To force reload: kill the app and reopen
- Per-app configs persist across OTA updates (stored in /data/)
