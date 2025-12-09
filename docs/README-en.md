# WhatsBerry - Complete Documentation

**WhatsApp Client for BlackBerry 10 using XMPP + Slidge-WhatsApp**

---

## 📚 Documentation Index

### User Guides

1. **[🚀 Quick Start (QUICKSTART-en.md)](QUICKSTART-en.md)**
   - Complete setup in 15-20 minutes
   - Step-by-step from zero to sending messages
   - **Start here if it's your first time**

2. **[📱 Client Configuration (CLIENT_CONFIGURATION-en.md)](CLIENT_CONFIGURATION-en.md)**
   - How to configure the BB10 app
   - Create XMPP account
   - Link with WhatsApp
   - Common troubleshooting

3. **[🐛 Troubleshooting (TROUBLESHOOTING-en.md)](TROUBLESHOOTING-en.md)**
   - Common issues and solutions
   - STARTTLS proxy debugging
   - Logs and diagnostics
   - Lessons learned

### Administrator Guides

4. **[🖥️ Server Setup (SERVER_SETUP-en.md)](SERVER_SETUP-en.md)**
   - Step-by-step installation
   - Hybrid Configuration (Recommended)
   - Docker Compose
   - Prosody and Slidge configuration

5. **[🌐 HTTP Server (HTTP_SERVER-en.md)](HTTP_SERVER-en.md)**
   - Nginx configuration
   - Serving APK and attachments
   - PHP endpoints for uploads
   - SSL with Let's Encrypt
   - Security and monitoring

6. **[📝 Changelog (CHANGELOG-en.md)](CHANGELOG-en.md)**
   - Versions and updates
   - Changes per version
   - Important updates

---

## 🎯 Project Architecture

```
┌─────────────────────┐
│  BlackBerry 10      │
│  Android App        │
│  (API Level 18)     │
└──────────┬──────────┘
           │ STARTTLS (TLS 1.0+)
           │ Port 5222
           ▼
┌─────────────────────────────┐
│ XMPP STARTTLS Proxy         │
│ - Node.js                   │
│ - Supports TLS 1.0-1.3      │
│ - Injects STARTTLS          │
└──────────┬──────────────────┘
           │ Plaintext XMPP
           │ Port 5200
           ▼
┌─────────────────────────────┐
│ Prosody XMPP Server         │
│ - VirtualHost config        │
│ - Slidge component          │
└──────────┬──────────────────┘
           │ XMPP Component
           │ Port 5347
           ▼
┌─────────────────────────────┐
│ Slidge WhatsApp Gateway     │
│ - Docker container          │
│ - network_mode: host        │
│ - whatsmeow (Go)            │
└──────────┬──────────────────┘
           │ WhatsApp Protocol
           ▼
┌─────────────────────────────┐
│ WhatsApp Servers            │
└─────────────────────────────┘
```

---

## 📦 Project Components

### 1. Android Client (BlackBerry 10)

**Location**: `/opt/whatsberry/app/`

**Technologies**:
- Android API Level 18 (4.3 Jelly Bean)
- Smack XMPP 4.1.9
- SQLite for messages
- Adapted Material Design

**Current Version**: v3.3.1
- Database location selector
- Configurable credentials
- Foreground service for notifications
- Menu-accessible database settings

### 2. XMPP STARTTLS Proxy

**Location**: `/opt/whatsberry/xmpp-starttls-proxy.js`

**Purpose**: Allow BB10 devices (TLS 1.0) to connect to modern Prosody (TLS 1.2+)

**Features**:
- Listens on port 5222
- Supports TLS 1.0, 1.1, 1.2, 1.3
- Injects STARTTLS in Prosody features
- Creates new connection after TLS handshake
- Bidirectional relay: Client(TLS) ↔ Proxy ↔ Prosody(plaintext)

### 3. Prosody XMPP Server

**Location**: `/etc/prosody/prosody.cfg.lua`

**Configuration**:
- c2s port: 5200 (internal, no TLS)
- Component port: 5347
- VirtualHost with TLS disabled
- Component for slidge-whatsapp

### 4. Slidge WhatsApp Gateway

**Location**: Docker container + `~/.local/share/slidge/slidge.conf`

**Configuration**:
- JID: `whatsapp.localhost`
- Shared secret with Prosody
- network_mode: host (access to localhost:5347)
- Storage in `/data`

---

## 🚀 Deployment Options

### Option 1: Hybrid Setup ✅ (Recommended)

**Production-tested configuration**

- **Prosody**: systemd service (port 5200)
- **Slidge**: Docker container with host network
- **Proxy**: systemd service (port 5222)

**Advantages**:
- ✅ Tested and working
- ✅ Better resource control
- ✅ Easy debugging (separate logs)
- ✅ Independent updates

**See**: [SERVER_SETUP-en.md - Hybrid Setup](SERVER_SETUP-en.md#hybrid-setup-tested--recommended)

### Option 2: Full Docker Compose ⚠️

**All services in Docker**

**Status**: Untested

**See**: [`docker-compose.yml`](../docker-compose.yml)

### Option 3: Full Manual

**All services with systemd**

**See**: [SERVER_SETUP-en.md - Manual Installation](SERVER_SETUP-en.md#manual-installation)

---

## 🔐 Security and Privacy

### User Data

- ✅ **Self-hostable**: You control your data
- ✅ **No server storage**: Messages only on device
- ✅ **TLS encryption**: Client ↔ Server always encrypted
- ✅ **Open source**: Full transparency

### Considerations

- ⚠️ **TLS 1.0 enabled**: Required for BB10, legacy protocol
- ⚠️ **Proxy has traffic access**: Decrypts between client and Prosody
- ✅ **Mitigation**: Proxy and Prosody on same server (localhost)

---

## 📱 App Versions

### v3.3.1 (December 8, 2024) - CURRENT

**Changes**:
- Database Settings accessible from main menu
- Menu: Refresh, Database Settings, Logout

### v3.3.0 (December 8, 2024)

**Changes**:
- Database location selector (4 options)
- BlackBerry 10 auto-detection
- Safe migration between locations
- Database information dialog

### v3.2.0 (December 8, 2024)

**Changes**:
- User-configurable XMPP credentials
- Visible server fields in login
- Auto-login only with saved credentials
- Self-hosting support

### v3.1.9 (December 8, 2024)

**CRITICAL**:
- **Foreground Service** for XMPP
- Notifications working in BB10 Hub
- Prevents BB10 from killing the app

**See full history**: [CHANGELOG-en.md](CHANGELOG-en.md)

---

## 📄 License

MIT License

See [LICENSE](../LICENSE) for full details.

---

## 🆘 Support

### Help Resources

1. **Documentation**:
   - [Quick Start](QUICKSTART-en.md) - First steps
   - [Troubleshooting](TROUBLESHOOTING-en.md) - Common issues
   - [Server Setup](SERVER_SETUP-en.md) - Advanced setup

2. **Logs and Debugging**:
   ```bash
   # STARTTLS Proxy
   sudo journalctl -u xmpp-tls-proxy -f

   # Prosody
   sudo journalctl -u prosody -f

   # Slidge
   docker logs -f slidge-whatsapp
   ```

3. **Community**:
   - GitHub Issues: [Report issue](https://github.com/yourusername/whatsberry/issues)
   - Discussions: [Ask questions](https://github.com/yourusername/whatsberry/discussions)

---

**Built with ❤️ for BlackBerry 10 users**
