# 📚 Master Documentation Index - WhatsBerry Project

**Last updated**: December 7, 2025

This document serves as the central index for all WhatsBerry project documentation.

---

## 🚀 Quick Start

**If you're new to the project, read in this order**:

1. **README.md** (9.5K) - General project overview
2. **CLIENT_CONFIGURATION.md** (10K) - Configure XMPP connection/client
3. **APK_BUILD_SIGNING_GUIDE.md** (13K) - 🆕 APK building and signing

---

## 📖 Documentation by Category

### 🏗️ Architecture and Design

| Document | Size | Description |
|----------|------|-------------|
| **README.md** | 9.5K | General overview, architecture, installation |
| **TECHNICAL.md** | 31K | 🔥 Deep technical details, architectural decisions |
| **TROUBLESHOOTING_STARTTLS_PROXY.md** | 17K | 🆕 STARTTLS proxy lessons learned |

**Recommendation**: Start with README.md, then TECHNICAL.md for deeper understanding.

---

### 🔧 Configuration and Deployment

| Document | Size | Description |
|----------|------|-------------|
| **CLIENT_CONFIGURATION.md** | 10K | Server setup, clients, troubleshooting |
| **PROJECT_STATUS.md** | 7.4K | Project status, completed features |
| **NEXT_STEPS.md** | 8.4K | Roadmap, future improvements |

**Recommendation**: CLIENT_CONFIGURATION.md is essential for setup.

---

### 📱 APK Building and Modification

| Document | Size | Description |
|----------|------|-------------|
| **APK_BUILD_SIGNING_GUIDE.md** | 13K | 🆕 **KEY**: Debug vs Release, signing, apksigner |
| **APK_PUBLICATION_GUIDE.md** | 15K | 🆕 **IMPORTANT**: Where and how to publish APKs |
| **apk-mod/APK_MODIFICATION_METHOD.md** | 7.3K | Modify existing APK with apktool |
| **ICON_NAME_CHANGES.md** | 3.9K | Change app icon and name |
| **REVERSE_ENGINEERING.md** | 3.1K | Original APK analysis |

**⚠️ IMPORTANT**: Read in this order:
1. **APK_BUILD_SIGNING_GUIDE.md** - How to compile and sign
   - Why `./gradlew assembleDebug` does NOT need manual signing
   - Why `apktool b` DOES need manual signing
   - Difference between apksigner and jarsigner
   - Correct order: zipalign → apksigner

2. **APK_PUBLICATION_GUIDE.md** - Where to publish the APK
   - ✅ CORRECT locations: `/opt/whatsberry/public/downloads/`
   - ❌ INCORRECT locations: `/var/www/html/downloads/`
   - Nginx configuration
   - Automated script: `./publish-apk.sh`

**Published APK paths** (CORRECT):
```
/opt/whatsberry/public/downloads/whatsberry-new.apk       (57 MB) - Latest version
/opt/whatsberry/public/downloads/whatsberry-v2.7.0.apk    (57 MB) - v2.7.0
/opt/whatsberry/public/whatsberry-v2.7.0.apk              (57 MB) - v2.7.0 (port 9003)
```

**Download URLs** (ALL WORKING):
```
https://whatsberry.descarga.media/downloads/whatsberry-new.apk       ← Main (HTTPS)
https://whatsberry.descarga.media/downloads/whatsberry-v2.7.0.apk
http://whatsberry.descarga.media/downloads/whatsberry-new.apk
http://whatsberry.descarga.media:9003/whatsberry-v2.7.0.apk          ← Alternative
```

**Automated publication script**:
```bash
cd /opt/whatsberry
./publish-apk.sh 2.7.0
```

---

### 🌐 XMPP and WhatsApp Gateway

| Document | Size | Description |
|----------|------|-------------|
| **FIX_GATEWAY_REGISTRATION.md** | 7.4K | Fix WhatsApp gateway registration |
| **GET_QR_MANUAL.md** | 4.3K | Manually obtain QR code |
| **WEB_QR_READY.md** | 6.0K | Web QR implementation |
| **NEW_MESSAGE_FLOW.md** | 5.5K | Updated message flow |
| **GAJIM_STEPS.md** | 8.3K | Testing with Gajim client |

**Typical flow**:
1. FIX_GATEWAY_REGISTRATION.md - Register with gateway
2. GET_QR_MANUAL.md - Get QR if needed
3. WEB_QR_READY.md - Use web interface for QR

---

### 🔍 Troubleshooting and Debugging

| Document | Size | Description | Problems it Solves |
|----------|------|-------------|-------------------|
| **TROUBLESHOOTING_STARTTLS_PROXY.md** | 17K | 🔥 **MOST IMPORTANT** | 7 critical proxy problems documented |
| **CLIENT_CONFIGURATION.md** | 10K | Connection troubleshooting | Connection, timeouts, UFW |
| **APK_BUILD_SIGNING_GUIDE.md** | 13K | Build troubleshooting | Signing, zipalign, apksigner |

**Common Problems Covered**:

#### STARTTLS Proxy (TROUBLESHOOTING_STARTTLS_PROXY.md)
- ✅ TLS handshake timing
- ✅ Listeners not executing
- ✅ UFW rate limiting blocking connections
- ✅ Socket event listener cascade
- ✅ Prosody closing TCP connection
- ✅ XMPP stream conflicts
- ✅ pause/resume ordering

#### APK Build (APK_BUILD_SIGNING_GUIDE.md)
- ✅ "APK won't install" → zipalign before signing
- ✅ "Already signed with debug" → Don't re-sign assembleDebug
- ✅ "Invalid signature" → Use apksigner, not jarsigner
- ✅ "Version incompatible" → Java 17

#### Connection (CLIENT_CONFIGURATION.md)
- ✅ Connection timeout → UFW LIMIT → ALLOW
- ✅ TLS handshake failed → Check proxy TLS 1.0
- ✅ Not receiving messages → Check bidirectional relay

---

## 🎯 Use Case Scenarios

### "I want to understand the project"
1. README.md - Overview
2. TECHNICAL.md - Depth
3. TROUBLESHOOTING_STARTTLS_PROXY.md - Lessons learned

### "I want to set up the server"
1. README.md - Base installation
2. CLIENT_CONFIGURATION.md - Detailed configuration
3. TROUBLESHOOTING_STARTTLS_PROXY.md - If problems arise

### "I want to build the APK"
1. **APK_BUILD_SIGNING_GUIDE.md** ← **START HERE**
2. build-apk.sh script (for debug builds)
3. apk-mod/APK_MODIFICATION_METHOD.md (to modify existing APKs)

### "I want to modify the original APK"
1. **APK_BUILD_SIGNING_GUIDE.md** - Understand signing
2. apk-mod/APK_MODIFICATION_METHOD.md - Modification process
3. ICON_NAME_CHANGES.md - Customization

### "I have an error"
1. Search for error in **TROUBLESHOOTING_STARTTLS_PROXY.md** (proxy)
2. Search in **APK_BUILD_SIGNING_GUIDE.md** (APK)
3. Search in **CLIENT_CONFIGURATION.md** (connection)

### "I want to contribute"
1. TECHNICAL.md - Understand architecture
2. TROUBLESHOOTING_STARTTLS_PROXY.md - Learn from past errors
3. NEXT_STEPS.md - See what's missing

---

## 📊 Complete Documentation

```
Total: 14 .md files
Total size: ~134 KB

Breakdown:
├── Architecture/Design: 57.5K (README, TECHNICAL, TROUBLESHOOTING_STARTTLS_PROXY)
├── APK Build/Mod: 27.5K (APK_BUILD_SIGNING, MODIFICATION_METHOD, ICON_CHANGES, REVERSE)
├── Config/Deploy: 25.8K (CLIENT_CONFIGURATION, PROJECT_STATUS, NEXT_STEPS)
├── XMPP/Gateway: 31.5K (FIX_GATEWAY, GET_QR, WEB_QR, NEW_FLOW, GAJIM_STEPS)
```

---

## 🆕 New Documents (December 7, 2025)

**Created today**:
1. **APK_BUILD_SIGNING_GUIDE.md** (13K)
   - Clarifies debug vs release
   - Explains when to sign and when not to
   - Documents apksigner vs jarsigner
   - Includes published APK paths

2. **TROUBLESHOOTING_STARTTLS_PROXY.md** (17K)
   - 7 critical problems solved
   - 7 lessons learned
   - Code examples for each problem
   - Complete final configuration

**Updated today**:
1. **README.md** - STARTTLS proxy section
2. **TECHNICAL.md** - Complete new proxy section (276 lines)
3. **CLIENT_CONFIGURATION.md** - Proxy architecture, UFW troubleshooting
4. **apk-mod/APK_MODIFICATION_METHOD.md** - Reference to new guide

---

## 🔑 Key Concepts by Document

### README.md
- General architecture (BB10 → Proxy → Prosody → slidge → WhatsApp)
- Technology stack
- Base installation

### TECHNICAL.md
- Architectural decision: New connection post-TLS
- 11-step STARTTLS flow
- Performance characteristics
- Security considerations

### TROUBLESHOOTING_STARTTLS_PROXY.md
- **Hardest problem**: Listener registered but never executed
- **Key solution**: New TCP connection to Prosody after TLS
- **UFW rate limiting**: Change LIMIT to ALLOW
- **Socket cleanup**: removeAllListeners() before destroy()

### APK_BUILD_SIGNING_GUIDE.md
- **Debug builds**: ALREADY auto-signed
- **apktool builds**: Need manual signing
- **Correct order**: zipalign → apksigner
- **apksigner > jarsigner**: v1/v2/v3 schemes

### CLIENT_CONFIGURATION.md
- Recommended connection: whatsberry.descarga.media:5222
- UFW: ALLOW (not LIMIT) for 5222
- Timeout and TLS troubleshooting

---

## 💡 Navigation Tips

### Quick Search

```bash
# Search all documentation
grep -r "keyword" /opt/whatsberry/*.md

# Search only troubleshooting
grep -i "error" /opt/whatsberry/TROUBLESHOOTING_*.md

# List all documents
ls -lh /opt/whatsberry/*.md
```

### Most Useful Documents

**Top 5 by utility**:
1. **APK_BUILD_SIGNING_GUIDE.md** - Avoid signing errors
2. **TROUBLESHOOTING_STARTTLS_PROXY.md** - Solve complex problems
3. **TECHNICAL.md** - Deep understanding
4. **CLIENT_CONFIGURATION.md** - Quick setup
5. **README.md** - General overview

**Top 3 by lessons learned**:
1. **TROUBLESHOOTING_STARTTLS_PROXY.md** - 7 problems documented
2. **APK_BUILD_SIGNING_GUIDE.md** - Clarifies common confusions
3. **TECHNICAL.md** - Architectural decisions explained

---

## 📞 Support

If you can't find what you're looking for:

1. **Search this index** for the relevant category
2. **Read the specific document** from that category
3. **Check troubleshooting** in the 3 main documents
4. **Review logs** using commands from TROUBLESHOOTING_STARTTLS_PROXY.md

**Useful diagnostic commands**:
```bash
# View proxy logs
sudo journalctl -u xmpp-tls-proxy.service -f

# View service status
sudo systemctl status xmpp-tls-proxy prosody

# Verify signed APK
~/Android/Sdk/build-tools/34.0.0/apksigner verify -v your-app.apk

# View listening ports
ss -tlnp | grep -E "(5222|5200|9003)"
```

---

## 📝 Documentation Maintenance

**When you update code**:
- ✅ Update TECHNICAL.md with architectural changes
- ✅ Update TROUBLESHOOTING if you solve a new problem
- ✅ Update APK_BUILD_SIGNING_GUIDE.md if you change build process
- ✅ Update this index if you add new documents

**When you find a bug**:
- ✅ Document in TROUBLESHOOTING_STARTTLS_PROXY.md
- ✅ Include: symptom, cause, solution, lesson learned

---

**Last updated**: December 7, 2025
**Total documents**: 14 .md files
**Total size**: ~134 KB
**Status**: ✅ Fully documented
