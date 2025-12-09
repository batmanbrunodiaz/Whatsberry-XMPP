# APK Publication Guide - WhatsBerry

**Last updated**: December 7, 2025

---

## 🎯 IMPORTANT: Publication Locations

### ⚠️ COMMON ERROR
**DO NOT** publish APKs to `/var/www/html/downloads/` - that directory is NOT configured in nginx.

### ✅ CORRECT LOCATIONS

#### 1. Main Download Directory (HTTPS)
```
Physical path: /opt/whatsberry/public/downloads/
Public URL: https://whatsberry.descarga.media/downloads/
```

**Files to publish here**:
- `whatsberry-new.apk` - Latest version (always overwrite)
- `whatsberry-v2.7.0.apk` - Numbered version (keep historical)

#### 2. Secondary Directory (HTTP port 9003)
```
Physical path: /opt/whatsberry/public/
Public URL: http://whatsberry.descarga.media:9003/
```

**Files to publish here**:
- `whatsberry-v2.7.0.apk` - Main numbered version

---

## 📝 Nginx Configuration

### File: `/etc/nginx/sites-available/whatsberry`

**Port 80 (HTTP)**:
```nginx
server {
    listen 80;
    server_name whatsberry.descarga.media;

    # Downloads directory for APKs
    location /downloads/ {
        alias /opt/whatsberry/public/downloads/;
        autoindex on;
        add_header Content-Disposition "attachment";
    }
}
```

**Port 443 (HTTPS)**:
```nginx
server {
    listen 443 ssl;
    server_name whatsberry.descarga.media;

    # SSL certificates
    ssl_certificate /etc/letsencrypt/live/whatsberry.descarga.media/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/whatsberry.descarga.media/privkey.pem;

    # Downloads directory for APKs (SAME as HTTP)
    location /downloads/ {
        alias /opt/whatsberry/public/downloads/;
        autoindex on;
        add_header Content-Disposition "attachment";
    }
}
```

### File: `/etc/nginx/sites-available/whatsberry.descarga.media.conf`

**Port 9003 (Alternative HTTP)**:
```nginx
server {
    listen 9003;
    server_name localhost;

    # Serve static files (APK downloads)
    location ~* \.(apk|zip|tar\.gz)$ {
        root /opt/whatsberry/public;
        try_files $uri =404;
        add_header Content-Disposition "attachment";
    }
}
```

---

## 🚀 New APK Publication Process

### Step 1: Build the APK
```bash
cd /opt/whatsberry
./gradlew assembleDebug --no-daemon --stacktrace
```

**Output**: `/opt/whatsberry/app/build/outputs/apk/debug/app-debug.apk`

### Step 2: Copy to Correct Locations

```bash
#!/bin/bash
# Script: publish-apk.sh

VERSION="2.7.0"
APK_SOURCE="/opt/whatsberry/app/build/outputs/apk/debug/app-debug.apk"

echo "📦 Publishing APK v${VERSION}..."

# 1. Downloads directory (HTTPS/HTTP)
echo "  ✓ Copying to /opt/whatsberry/public/downloads/"
sudo cp "$APK_SOURCE" /opt/whatsberry/public/downloads/whatsberry-new.apk
sudo cp "$APK_SOURCE" /opt/whatsberry/public/downloads/whatsberry-v${VERSION}.apk

# 2. Main directory (HTTP port 9003)
echo "  ✓ Copying to /opt/whatsberry/public/"
sudo cp "$APK_SOURCE" /opt/whatsberry/public/whatsberry-v${VERSION}.apk

# 3. Adjust permissions
echo "  ✓ Adjusting permissions..."
sudo chown batman:batman /opt/whatsberry/public/downloads/whatsberry-new.apk
sudo chown batman:batman /opt/whatsberry/public/downloads/whatsberry-v${VERSION}.apk
sudo chown batman:batman /opt/whatsberry/public/whatsberry-v${VERSION}.apk
sudo chmod 644 /opt/whatsberry/public/downloads/*.apk
sudo chmod 644 /opt/whatsberry/public/whatsberry-v${VERSION}.apk

echo "✅ Publication completed"
echo ""
echo "Available URLs:"
echo "  • https://whatsberry.descarga.media/downloads/whatsberry-new.apk"
echo "  • https://whatsberry.descarga.media/downloads/whatsberry-v${VERSION}.apk"
echo "  • http://whatsberry.descarga.media/downloads/whatsberry-new.apk"
echo "  • http://whatsberry.descarga.media:9003/whatsberry-v${VERSION}.apk"
```

### Step 3: Verify Publication

```bash
# Verify HTTPS
curl -I https://whatsberry.descarga.media/downloads/whatsberry-new.apk

# Verify HTTP
curl -I http://whatsberry.descarga.media/downloads/whatsberry-new.apk

# Verify port 9003
curl -I http://whatsberry.descarga.media:9003/whatsberry-v2.7.0.apk

# All should return: HTTP 200 OK
```

---

## 📂 Directory Structure

```
/opt/whatsberry/
├── app/
│   └── build/outputs/apk/debug/
│       └── app-debug.apk               ← Compiled APK (temporary)
│
└── public/
    ├── downloads/                       ← MAIN HTTPS/HTTP DIRECTORY
    │   ├── whatsberry-new.apk          ← Latest version (overwrite)
    │   ├── whatsberry-v2.7.0.apk       ← Specific version
    │   ├── whatsberry-v2.6.0.apk       ← Previous versions
    │   └── whatsberry-hardcoded.apk    ← Special versions
    │
    ├── whatsberry-v2.7.0.apk           ← For port 9003
    ├── whatsberry-v2.2.0.apk           ← Previous versions
    ├── upload.php
    ├── convert_audio.php
    └── index.html

/var/www/html/downloads/                 ← ⚠️ DO NOT USE - Not configured in nginx
```

---

## 🌐 Public Download URLs

### Main URLs (Recommended)
```
✅ https://whatsberry.descarga.media/downloads/whatsberry-new.apk
   → Always points to latest version
   → Secure HTTPS
   → Autoindex enabled (browsable)

✅ https://whatsberry.descarga.media/downloads/whatsberry-v2.7.0.apk
   → Specific version
   → Secure HTTPS
```

### Alternative URLs
```
✅ http://whatsberry.descarga.media/downloads/whatsberry-new.apk
   → Same content as HTTPS
   → Unencrypted HTTP

✅ http://whatsberry.descarga.media:9003/whatsberry-v2.7.0.apk
   → Alternative port
   → Different directory (/opt/whatsberry/public/)
```

---

## 🔍 Configuration Verification

### Command to verify active nginx configuration
```bash
sudo nginx -T | grep -A 20 "location /downloads"
```

**Expected output**:
```nginx
location /downloads/ {
    alias /opt/whatsberry/public/downloads/;
    autoindex on;
    add_header Content-Disposition "attachment";
}
```

### Command to list published APKs
```bash
ls -lh /opt/whatsberry/public/downloads/*.apk
ls -lh /opt/whatsberry/public/whatsberry*.apk
```

---

## ❌ Common Errors and Solutions

### Error 1: APK not accessible via HTTPS
**Symptom**: `curl -I https://whatsberry.descarga.media/downloads/whatsberry-new.apk` returns 404

**Causes**:
- ❌ APK copied to `/var/www/html/downloads/` (wrong location)
- ❌ Incorrect permissions

**Solution**:
```bash
# Verify correct location
ls -la /opt/whatsberry/public/downloads/whatsberry-new.apk

# If it doesn't exist, copy again
sudo cp /opt/whatsberry/app/build/outputs/apk/debug/app-debug.apk \
        /opt/whatsberry/public/downloads/whatsberry-new.apk
sudo chown batman:batman /opt/whatsberry/public/downloads/whatsberry-new.apk
sudo chmod 644 /opt/whatsberry/public/downloads/whatsberry-new.apk
```

### Error 2: Permission denied
**Symptom**: nginx returns 403 Forbidden

**Solution**:
```bash
# Verify permissions
ls -la /opt/whatsberry/public/downloads/

# Should show: -rw-r--r-- batman batman

# Fix if necessary
sudo chown batman:batman /opt/whatsberry/public/downloads/*.apk
sudo chmod 644 /opt/whatsberry/public/downloads/*.apk
```

### Error 3: Nginx doesn't reload configuration
**Symptom**: Configuration changes don't take effect

**Solution**:
```bash
# Verify syntax
sudo nginx -t

# Reload nginx
sudo systemctl reload nginx

# If it fails, restart
sudo systemctl restart nginx
```

---

## 📊 Published Versions History

| Version | Date | Size | Main Changes | URLs |
|---------|------|------|--------------|------|
| v2.7.0  | 2025-12-07 | 57 MB | Debug logging for JID matching | [HTTPS](https://whatsberry.descarga.media/downloads/whatsberry-v2.7.0.apk) |
| v2.6.0  | 2025-12-07 | 56 MB | Typing indicators, emoji picker | - |
| v2.5.1  | 2025-12-07 | 52 MB | Bugfix version | downloads/ |
| v2.2.0  | 2025-12-07 | 53 MB | XMPP basic messaging | port 9003 |

---

## 🛠️ Automated Publication Script

**Location**: `/opt/whatsberry/publish-apk.sh`

```bash
#!/bin/bash
# Publish new WhatsBerry APK version
# Usage: ./publish-apk.sh [VERSION]
# Example: ./publish-apk.sh 2.7.0

set -e

VERSION=${1:-"2.7.0"}
APK_SOURCE="/opt/whatsberry/app/build/outputs/apk/debug/app-debug.apk"

echo "================================================"
echo "  📦 WhatsBerry APK Publication Script"
echo "================================================"
echo "Version: v${VERSION}"
echo ""

# Verify that APK exists
if [ ! -f "$APK_SOURCE" ]; then
    echo "❌ ERROR: APK not found at $APK_SOURCE"
    echo "   Please run first: ./gradlew assembleDebug"
    exit 1
fi

# Show APK size
APK_SIZE=$(du -h "$APK_SOURCE" | cut -f1)
echo "📊 APK Size: $APK_SIZE"
echo ""

# Create directories if they don't exist
echo "📁 Verifying directories..."
sudo mkdir -p /opt/whatsberry/public/downloads
echo "   ✓ /opt/whatsberry/public/downloads/"
echo ""

# Copy APKs
echo "📦 Publishing APKs..."

echo "   → whatsberry-new.apk"
sudo cp "$APK_SOURCE" /opt/whatsberry/public/downloads/whatsberry-new.apk

echo "   → whatsberry-v${VERSION}.apk (downloads)"
sudo cp "$APK_SOURCE" /opt/whatsberry/public/downloads/whatsberry-v${VERSION}.apk

echo "   → whatsberry-v${VERSION}.apk (public)"
sudo cp "$APK_SOURCE" /opt/whatsberry/public/whatsberry-v${VERSION}.apk

echo ""

# Adjust permissions
echo "🔐 Adjusting permissions..."
sudo chown batman:batman /opt/whatsberry/public/downloads/whatsberry-new.apk
sudo chown batman:batman /opt/whatsberry/public/downloads/whatsberry-v${VERSION}.apk
sudo chown batman:batman /opt/whatsberry/public/whatsberry-v${VERSION}.apk
sudo chmod 644 /opt/whatsberry/public/downloads/*.apk
sudo chmod 644 /opt/whatsberry/public/whatsberry-v${VERSION}.apk
echo "   ✓ Permissions: 644 (batman:batman)"
echo ""

# Verify publication
echo "🔍 Verifying publication..."

check_url() {
    local url=$1
    local status=$(curl -s -o /dev/null -w "%{http_code}" "$url")
    if [ "$status" = "200" ]; then
        echo "   ✅ $url"
    else
        echo "   ❌ $url (HTTP $status)"
    fi
}

check_url "https://whatsberry.descarga.media/downloads/whatsberry-new.apk"
check_url "https://whatsberry.descarga.media/downloads/whatsberry-v${VERSION}.apk"
check_url "http://whatsberry.descarga.media/downloads/whatsberry-new.apk"
check_url "http://whatsberry.descarga.media:9003/whatsberry-v${VERSION}.apk"

echo ""
echo "================================================"
echo "  ✅ PUBLICATION COMPLETED"
echo "================================================"
echo ""
echo "🌐 Download URLs:"
echo "   • https://whatsberry.descarga.media/downloads/whatsberry-new.apk"
echo "   • https://whatsberry.descarga.media/downloads/whatsberry-v${VERSION}.apk"
echo "   • http://whatsberry.descarga.media/downloads/whatsberry-new.apk"
echo "   • http://whatsberry.descarga.media:9003/whatsberry-v${VERSION}.apk"
echo ""
echo "📂 Local files:"
ls -lh /opt/whatsberry/public/downloads/whatsberry*.apk
echo ""
ls -lh /opt/whatsberry/public/whatsberry-v${VERSION}.apk
echo ""
echo "✨ Ready to download!"
```

---

**Last updated**: December 7, 2025
**Author**: WhatsBerry Project Documentation
