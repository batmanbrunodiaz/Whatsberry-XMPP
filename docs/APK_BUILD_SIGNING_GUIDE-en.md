# APK Build and Signing Guide - Important Clarifications

**Last updated**: December 7, 2025

## ⚠️ IMPORTANT: Debug vs Release

### Key Concept

**ALL APKs must be signed to install on Android.** The difference is WHAT signature they use:

| Type | Signature | When to Re-sign | Use Case |
|------|-----------|-----------------|----------|
| **Debug** | Automatic (debug keystore) | ❌ NOT necessary | Development, testing |
| **Release** | Manual (your keystore) | ✅ YES necessary | Production, Play Store |

### ⚠️ COMMON ERROR

```bash
# ❌ INCORRECT: Signing an APK that's already debug-signed
./gradlew assembleDebug          # Generates debug-signed APK
# ... then ...
apksigner sign ...                # ERROR! Already signed

# ✅ CORRECT: Option 1 - Use debug build directly
./gradlew assembleDebug           # Generates ready-to-use APK

# ✅ CORRECT: Option 2 - Build release (unsigned) and sign
./gradlew assembleRelease         # Generates unsigned APK
apksigner sign ...                # Sign manually
```

---

## Project 1: BlackBerry Wrapper (~/blackberry-wrapper)

### Current Build

```bash
#!/bin/bash
# File: /home/batman/build-apk.sh
export ANDROID_HOME=~/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

cd ~/blackberry-wrapper

echo "Building APK..."
./gradlew assembleDebug --no-daemon --stacktrace
```

### What does it generate?

**Output**: `app/build/outputs/apk/debug/app-debug.apk`

**Signing status**: ✅ **ALREADY SIGNED** with debug keystore

**Debug keystore location**: `~/.android/debug.keystore` (automatically generated)

### Does it need to be signed again?

**❌ NO** - The debug APK is already signed and ready for:
- Direct installation on devices
- Testing and development
- Internal distribution (not production)

### When to use Release Build?

**Only if you need to**:
- Publish to Play Store
- Public/production distribution
- Custom signature for branding

```bash
# For release:
./gradlew assembleRelease   # Generates unsigned APK
# Then sign manually (see signing section below)
```

---

## Project 2: WhatsBerry APK Modification (/opt/whatsberry/apk-mod)

### Current Process

**Input**: `WhatsBerry_v0_11_1-beta.apk` (original APK)

**Steps**:
1. Decompile with apktool
2. Modify URLs (.smali files)
3. Recompile with apktool
4. **IMPORTANT**: Apktool generates **UNSIGNED APK** or with temporary signature
5. ✅ **Must be signed manually**

### Why does it need signing?

When you modify an existing APK:
- `apktool b` generates APK **without valid signature**
- Android **won't install it** without valid signature
- **You must sign it manually** with apksigner

### Correct Process

```bash
# 1. Decompile
apktool d WhatsBerry_v0_11_1-beta.apk -o whatsberry-decompiled

# 2. Modify
cd whatsberry-decompiled/smali/com/blackberry/whatsapp
sed -i 's|whatsberry.com|whatsberry.descarga.media|g' WhatsAppAPI.smali
sed -i 's|wss://whatsberry.com|wss://whatsberry.descarga.media|g' WebSocketManager.smali

# 3. Recompile (generates UNSIGNED APK)
cd /opt/whatsberry/apk-mod
apktool b whatsberry-decompiled -o WhatsBerry-BB10-CUSTOM.apk

# 4. Align (BEFORE signing)
~/Android/Sdk/build-tools/34.0.0/zipalign -f -v 4 \
  WhatsBerry-BB10-CUSTOM.apk \
  WhatsBerry-BB10-CUSTOM-aligned.apk

# 5. Sign (AFTER aligning)
~/Android/Sdk/build-tools/34.0.0/apksigner sign \
  --ks whatsberry.keystore \
  --ks-key-alias whatsberry \
  --ks-pass pass:whatsberry123 \
  --key-pass pass:whatsberry123 \
  --out WhatsBerry-BB10-CUSTOM-final.apk \
  WhatsBerry-BB10-CUSTOM-aligned.apk

# 6. Verify
~/Android/Sdk/build-tools/34.0.0/apksigner verify -v WhatsBerry-BB10-CUSTOM-final.apk
```

---

## Signing Tools Comparison

### jarsigner (❌ Old, DO NOT use)

```bash
# Old method - NOT RECOMMENDED
jarsigner -verbose \
  -sigalg SHA1withRSA \
  -digestalg SHA1 \
  -keystore whatsberry.keystore \
  WhatsBerry-BB10-CUSTOM.apk \
  whatsberry
```

**Problems**:
- Only supports v1 signature scheme (old)
- Doesn't support v2/v3 (more secure and efficient)
- Android 7+ prefers v2/v3

### apksigner (✅ Modern, ALWAYS USE)

```bash
# Modern method - RECOMMENDED
~/Android/Sdk/build-tools/34.0.0/apksigner sign \
  --ks whatsberry.keystore \
  --ks-key-alias whatsberry \
  --ks-pass pass:whatsberry123 \
  --key-pass pass:whatsberry123 \
  --out WhatsBerry-BB10-CUSTOM-final.apk \
  WhatsBerry-BB10-CUSTOM-aligned.apk
```

**Advantages**:
- ✅ Supports v1, v2, v3 signature schemes
- ✅ More secure and efficient
- ✅ Optimized for modern Android
- ✅ Built-in verification

### Verify Signature

```bash
# With apksigner
~/Android/Sdk/build-tools/34.0.0/apksigner verify -v WhatsBerry-BB10-CUSTOM-final.apk

# Expected output:
Verifies
Verified using v1 scheme (JAR signing): true
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
```

---

## ⚠️ CRITICAL Order of Operations

### For Modified APK (apktool)

```
1. apktool d      (decompile)
2. [modify]       (edit files)
3. apktool b      (recompile - generates UNSIGNED APK)
4. zipalign       (align - BEFORE signing) ← IMPORTANT
5. apksigner      (sign - AFTER aligning) ← IMPORTANT
6. verify         (verify)
```

**❌ COMMON ERROR**: Signing before aligning
```bash
apksigner sign ... original.apk
zipalign ... signed.apk          # ERROR! Breaks signature
```

**✅ CORRECT**: Align before signing
```bash
zipalign ... original.apk aligned.apk
apksigner sign ... aligned.apk   # Correct
```

### For Gradle Build

#### Debug (No manual signing needed)
```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
# ✅ Already signed, ready to use
```

#### Release (Needs configuration)

**Option 1**: Configure in `build.gradle`
```gradle
android {
    signingConfigs {
        release {
            storeFile file("whatsberry.keystore")
            storePassword "whatsberry123"
            keyAlias "whatsberry"
            keyPassword "whatsberry123"
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
        }
    }
}
```

```bash
./gradlew assembleRelease
# Output automatically signed
```

**Option 2**: Sign manually afterwards
```bash
./gradlew assembleRelease  # Generates unsigned
apksigner sign ...         # Sign manually
```

---

## Published APK Paths

### Server Location

```
/opt/whatsberry/public/
├── whatsberry-v2.2.0.apk    (53.6 MB) - Version 2.2.0
└── whatsberry-v2.7.0.apk    (57.9 MB) - Version 2.7.0 (latest)
```

### Download URLs

**Current version (2.7.0)**:
```
http://whatsberry.descarga.media:9003/whatsberry-v2.7.0.apk
https://whatsberry.descarga.media/whatsberry-v2.7.0.apk
```

**Previous version (2.2.0)**:
```
http://whatsberry.descarga.media:9003/whatsberry-v2.2.0.apk
```

### Update Published APK

```bash
# After successful build:
sudo cp ~/blackberry-wrapper/app/build/outputs/apk/debug/app-debug.apk \
        /opt/whatsberry/public/whatsberry-v2.7.0.apk

# Adjust permissions
sudo chown batman:batman /opt/whatsberry/public/whatsberry-v2.7.0.apk
sudo chmod 644 /opt/whatsberry/public/whatsberry-v2.7.0.apk

# Verify
ls -lh /opt/whatsberry/public/*.apk
curl -I http://localhost:9003/whatsberry-v2.7.0.apk
```

---

## Keystore Management

### Current Location

```
/opt/whatsberry/apk-mod/whatsberry.keystore
```

### Credentials

```
Store Password: whatsberry123
Key Alias:      whatsberry
Key Password:   whatsberry123
```

### ⚠️ SECURITY

**For production**:
- ✅ Use strong passwords
- ✅ Backup keystore (if you lose it, you can't update the app)
- ✅ DO NOT commit keystore to repository
- ✅ Use environment variables for passwords

**For development/testing**:
- ✅ Use debug keystore (automatic)
- ✅ Simple passwords OK

### Keystore Backup

```bash
# Critical backup
cp /opt/whatsberry/apk-mod/whatsberry.keystore ~/whatsberry.keystore.backup
chmod 400 ~/whatsberry.keystore.backup

# Verify integrity
keytool -list -v -keystore ~/whatsberry.keystore.backup
```

---

## Automated Scripts

### Script for WhatsBerry APK Modification

Create `/opt/whatsberry/apk-mod/rebuild-and-sign.sh`:

```bash
#!/bin/bash
set -e

cd /opt/whatsberry/apk-mod

echo "=== 1. Decompiling ==="
apktool d WhatsBerry_v0_11_1-beta.apk -o whatsberry-decompiled -f

echo "=== 2. Modifying URLs ==="
cd whatsberry-decompiled/smali/com/blackberry/whatsapp
sed -i 's|https://whatsberry.com|https://whatsberry.descarga.media|g' WhatsAppAPI.smali
sed -i 's|wss://whatsberry.com|wss://whatsberry.descarga.media|g' WebSocketManager.smali
cd /opt/whatsberry/apk-mod

echo "=== 3. Recompiling ==="
apktool b whatsberry-decompiled -o WhatsBerry-BB10-CUSTOM.apk

echo "=== 4. Aligning (BEFORE signing) ==="
~/Android/Sdk/build-tools/34.0.0/zipalign -f -v 4 \
  WhatsBerry-BB10-CUSTOM.apk \
  WhatsBerry-BB10-CUSTOM-aligned.apk

echo "=== 5. Signing (AFTER aligning) ==="
~/Android/Sdk/build-tools/34.0.0/apksigner sign \
  --ks whatsberry.keystore \
  --ks-key-alias whatsberry \
  --ks-pass pass:whatsberry123 \
  --key-pass pass:whatsberry123 \
  --out WhatsBerry-BB10-CUSTOM-final.apk \
  WhatsBerry-BB10-CUSTOM-aligned.apk

echo "=== 6. Verifying ==="
~/Android/Sdk/build-tools/34.0.0/apksigner verify -v WhatsBerry-BB10-CUSTOM-final.apk

echo ""
echo "✅ Signed APK ready: WhatsBerry-BB10-CUSTOM-final.apk"
echo ""
echo "To publish:"
echo "  sudo cp WhatsBerry-BB10-CUSTOM-final.apk /opt/whatsberry/public/"
```

### Script for BlackBerry Wrapper Build

Update `/home/batman/build-apk.sh`:

```bash
#!/bin/bash
set -e

export ANDROID_HOME=~/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

cd ~/blackberry-wrapper

echo "=== Building Debug APK ==="
./gradlew assembleDebug --no-daemon --stacktrace

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

echo ""
echo "✅ Debug APK generated (ALREADY SIGNED):"
echo "   $APK_PATH"
echo ""
echo "Size:"
ls -lh "$APK_PATH"
echo ""
echo "❌ Does NOT need to be signed again (already has debug signature)"
echo ""
echo "To publish:"
echo "  sudo cp $APK_PATH /opt/whatsberry/public/whatsberry-v2.7.0.apk"
```

---

## FAQ - Frequently Asked Questions

### Do I need to sign a debug APK?

**❌ NO** - `./gradlew assembleDebug` generates an ALREADY SIGNED APK with debug keystore.

### Why does apktool need manual signing?

Because `apktool b` generates APK **without valid signature**. You must sign it yourself.

### Which is better: jarsigner or apksigner?

**apksigner** - Always. Supports v1/v2/v3 signatures. jarsigner is obsolete.

### Can I sign an already-signed APK?

**Yes**, but **you'll lose the previous signature**. apksigner replaces all existing signatures.

### What happens if I sign before zipalign?

❌ **ERROR** - zipalign modifies the APK and **breaks the signature**. Always:
1. zipalign first
2. sign afterwards

### Do I lose anything using debug signature?

For **development**: ❌ No
For **production**: ✅ Yes - Play Store requires release signature

### How do I verify if an APK is signed?

```bash
~/Android/Sdk/build-tools/34.0.0/apksigner verify -v your-app.apk
```

### Where is the debug keystore?

```
~/.android/debug.keystore
```

**Debug credentials** (standard Android):
- Store Password: `android`
- Key Alias: `androiddebugkey`
- Key Password: `android`

---

## Quick Summary

| Scenario | Command | Needs manual signing? |
|----------|---------|----------------------|
| Gradle Debug | `./gradlew assembleDebug` | ❌ NO (auto-signed) |
| Gradle Release (configured) | `./gradlew assembleRelease` | ❌ NO (auto-signed) |
| Gradle Release (no config) | `./gradlew assembleRelease` | ✅ YES (sign with apksigner) |
| apktool modification | `apktool b ...` | ✅ YES (sign with apksigner) |

**Golden rule**: If you used `apktool b`, **ALWAYS** zipalign + apksigner.

---

## References

- [APK Signature Scheme](https://source.android.com/docs/security/features/apksigning)
- [apksigner Documentation](https://developer.android.com/studio/command-line/apksigner)
- [zipalign Documentation](https://developer.android.com/studio/command-line/zipalign)
- [Gradle Signing](https://developer.android.com/studio/publish/app-signing#gradle-sign)

---

**Last updated**: December 7, 2025
**Author**: WhatsBerry Project Documentation
