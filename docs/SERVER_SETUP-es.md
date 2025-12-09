# 🖥️ WhatsBerry Server Setup Guide

Complete guide to set up your own WhatsBerry XMPP server with WhatsApp gateway support.

**Last updated**: December 8, 2024
**Tested on**: Arch Linux, Ubuntu 22.04+

---

## 📋 Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Prerequisites](#prerequisites)
3. [Setup Options](#setup-options)
4. [Hybrid Setup (Tested & Recommended)](#hybrid-setup-tested--recommended)
5. [Full Docker Compose (Alternative)](#full-docker-compose-alternative)
6. [Manual Installation](#manual-installation)
7. [XMPP STARTTLS Proxy Setup](#xmpp-starttls-proxy-setup)
8. [Firewall Configuration](#firewall-configuration)
9. [HTTP Server Setup (Optional but Recommended)](#http-server-setup-optional-but-recommended)
10. [Testing the Setup](#testing-the-setup)
11. [Troubleshooting](#troubleshooting)

---

## 🏗️ Architecture Overview

```
BlackBerry 10 Client (Android 4.3)
         ↓
    Port 5222 (STARTTLS Proxy)
         ↓
    Prosody XMPP Server (Port 5200)
         ↓
    Slidge WhatsApp Gateway (whatsapp.localhost)
         ↓
    WhatsApp Servers
```

### Components:

1. **Prosody XMPP Server** - Main XMPP server
2. **Slidge WhatsApp Gateway** - Bridges XMPP to WhatsApp
3. **XMPP STARTTLS Proxy** - Handles TLS 1.0 for BlackBerry 10 compatibility
4. **Node.js** - Runs the STARTTLS proxy

---

## 📦 Prerequisites

### System Requirements:
- **OS**: Linux (Ubuntu 22.04+, Arch Linux, Debian 11+)
- **RAM**: 1GB minimum (2GB recommended)
- **Disk**: 5GB minimum
- **Network**: Public IP with ports 5222, 80, 443 open

### Software Requirements:
- Docker & Docker Compose (for quick start) **OR**
- Prosody XMPP Server 0.12+
- Python 3.10+
- Node.js 16+
- Nginx (optional, for HTTPS)

---

## 🎯 Setup Options

WhatsBerry server can be deployed in three ways:

### 1️⃣ Hybrid Setup (Recommended) ✅
- **Prosody**: systemd service
- **Slidge WhatsApp**: Docker container
- **XMPP Proxy**: systemd service
- **Status**: ✅ Tested and working in production
- **Best for**: Production deployments, better resource control

### 2️⃣ Full Docker Compose
- **All services**: Run in Docker containers
- **Status**: ⚠️ Untested (provided as alternative)
- **Best for**: Quick testing, development environments

### 3️⃣ Full Manual Installation
- **All services**: systemd services
- **Status**: ✅ Tested
- **Best for**: Full control, minimal dependencies

---

## 🚀 Hybrid Setup (Tested & Recommended)

This is the **production-tested** configuration used by whatsberry.descarga.media.

### Architecture:
```
BlackBerry 10 → Port 5222 (Proxy systemd) → Port 5200 (Prosody systemd) → Port 5347 (Slidge Docker)
```

### Step 1: Install Prosody (systemd)

**Ubuntu/Debian:**
```bash
sudo apt update
sudo apt install prosody
```

**Arch Linux:**
```bash
sudo pacman -S prosody
```

**Configure Prosody** (`/etc/prosody/prosody.cfg.lua`):

Use the example configuration from [`prosody-config/prosody.cfg.lua`](prosody-config/prosody.cfg.lua), changing:
- `whatsberry.descarga.media` → your domain
- `component_secret` → generate with `openssl rand -base64 32`

**Start Prosody:**
```bash
sudo systemctl restart prosody
sudo systemctl enable prosody
sudo systemctl status prosody
```

### Step 2: Install Slidge WhatsApp (Docker)

**Create config directory:**
```bash
mkdir -p ~/.local/share/slidge
```

**Create config file** (`~/.local/share/slidge/slidge.conf`):
```ini
[global]
jid = whatsapp.localhost
secret = YOUR_SECRET_HERE  # Same as in Prosody config
server = localhost
port = 5347
home-dir = /data
debug = true

# Allow users from your domain
user-jid-validator = ^.*@(localhost|your-domain\.com)(/.*)?$

# File attachments (optional)
no-upload-path = /data/attachments
no-upload-url-prefix = https://your-domain.com/attachments/
no-upload-method = copy
```

**Run Slidge WhatsApp container:**
```bash
docker run -d \
  --name slidge-whatsapp \
  --network host \
  --restart unless-stopped \
  -v ~/.local/share/slidge:/data \
  --log-opt max-size=10m \
  --log-opt max-file=5 \
  codeberg.org/slidge/slidge-whatsapp:latest-amd64 \
  -c /data/slidge.conf
```

**Check logs:**
```bash
docker logs -f slidge-whatsapp
```

### Step 3: Install XMPP STARTTLS Proxy (systemd)

**Install Node.js:**
```bash
# Ubuntu/Debian
sudo apt install nodejs npm

# Arch Linux
sudo pacman -S nodejs npm
```

**Copy proxy file:**
```bash
sudo mkdir -p /opt/whatsberry
sudo cp xmpp-starttls-proxy.js /opt/whatsberry/
sudo chown -R $USER:$USER /opt/whatsberry
```

**Create systemd service** (`/etc/systemd/system/xmpp-tls-proxy.service`):
```ini
[Unit]
Description=XMPP STARTTLS Proxy for BlackBerry 10
Documentation=file:///opt/whatsberry/xmpp-starttls-proxy.js
After=network.target prosody.service
Requires=prosody.service

[Service]
Type=simple
User=batman
Group=batman
WorkingDirectory=/opt/whatsberry
ExecStart=/usr/bin/node /opt/whatsberry/xmpp-starttls-proxy.js
Restart=always
RestartSec=5
StandardOutput=journal
StandardError=journal
SyslogIdentifier=xmpp-tls-proxy

# Security
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/opt/whatsberry
CapabilityBoundingSet=CAP_NET_BIND_SERVICE

[Install]
WantedBy=multi-user.target
```

**Start the proxy:**
```bash
sudo systemctl daemon-reload
sudo systemctl start xmpp-tls-proxy
sudo systemctl enable xmpp-tls-proxy
sudo systemctl status xmpp-tls-proxy
```

### Step 4: Verify Setup

**Check all services are running:**
```bash
sudo systemctl status prosody
docker ps | grep slidge
sudo systemctl status xmpp-tls-proxy
```

**Check ports are listening:**
```bash
ss -tlnp | grep -E "(5222|5200|5347)"
# Expected:
# 5222 - xmpp-tls-proxy (STARTTLS)
# 5200 - prosody (internal)
# 5347 - prosody (component port for slidge)
```

**Test STARTTLS:**
```bash
timeout 3 openssl s_client -connect localhost:5222 -starttls xmpp -tls1
```

---

## 🐳 Full Docker Compose (Alternative)

⚠️ **NOTE**: This configuration is **untested**. The recommended approach is the [Hybrid Setup](#hybrid-setup-tested--recommended) above, which is tested and working in production.

This setup runs all three services in Docker containers. Use at your own risk or for development/testing purposes.

**1. Create `docker-compose.yml`:**

```yaml
version: '3.8'

services:
  prosody:
    image: prosody/prosody:0.12
    container_name: whatsberry-prosody
    hostname: prosody
    ports:
      - "5200:5200"  # XMPP internal port
    volumes:
      - ./prosody-config:/etc/prosody
      - prosody-data:/var/lib/prosody
    environment:
      - LOCAL=localhost
      - DOMAIN=whatsberry.descarga.media
      - ADMIN=admin@whatsberry.descarga.media
    restart: unless-stopped
    networks:
      - whatsberry-net

  slidge-whatsapp:
    image: nicoco/slidge-whatsapp:latest
    container_name: whatsberry-slidge
    hostname: slidge
    depends_on:
      - prosody
    volumes:
      - slidge-data:/var/lib/slidge
    environment:
      - SLIDGE_JID=whatsapp.localhost
      - SLIDGE_SECRET=your-secret-here
      - SLIDGE_XMPP_HOST=prosody
      - SLIDGE_XMPP_PORT=5200
    restart: unless-stopped
    networks:
      - whatsberry-net

  xmpp-proxy:
    image: node:16-alpine
    container_name: whatsberry-proxy
    hostname: proxy
    ports:
      - "5222:5222"  # Public XMPP port with STARTTLS
    volumes:
      - ./xmpp-starttls-proxy.js:/app/proxy.js:ro
    working_dir: /app
    command: node proxy.js
    depends_on:
      - prosody
    restart: unless-stopped
    networks:
      - whatsberry-net

networks:
  whatsberry-net:
    driver: bridge

volumes:
  prosody-data:
  slidge-data:
```

**2. Create Prosody configuration (`prosody-config/prosody.cfg.lua`):**

```lua
-- Prosody XMPP Server Configuration for WhatsBerry

admins = { "admin@whatsberry.descarga.media" }

modules_enabled = {
    "roster";
    "saslauth";
    "tls";
    "dialback";
    "disco";
    "carbons";
    "pep";
    "private";
    "blocklist";
    "vcard4";
    "vcard_legacy";
    "version";
    "uptime";
    "time";
    "ping";
    "register";
    "admin_adhoc";
    "cloud_notify";
    "http_file_share";
}

modules_disabled = {
    "offline";
    "c2s";
    "s2s";
}

allow_registration = true
min_seconds_between_registrations = 0

c2s_require_encryption = false
s2s_require_encryption = false

-- Interfaces and ports
interfaces = { "0.0.0.0" }
c2s_ports = { 5200 }

-- Storage
storage = "internal"

-- Logging
log = {
    info = "/var/log/prosody/prosody.log";
    error = "/var/log/prosody/prosody.err";
}

-- VirtualHost
VirtualHost "whatsberry.descarga.media"
    modules_disabled = { "tls" }
    enabled = true
    allow_registration = true

    modules_enabled = {
        "cloud_notify";
    }

    -- Grant roster privileges to WhatsApp gateway
    privileged_entities = {
        ["whatsapp.localhost"] = {
            roster = "both";
            message = "outgoing";
            presence = "roster";
        };
    }

-- Component for Slidge WhatsApp gateway
Component "whatsapp.localhost"
    component_secret = "your-secret-here"
    modules_enabled = {
        "http_file_share";
    }

-- HTTP File Upload component (XEP-0363)
Component "upload.localhost" "http_file_share"
    http_file_share_access = { "whatsapp.localhost" }
    http_file_share_size_limit = 10485760  -- 10 MB
    http_file_share_expire_after = 86400   -- 24 hours
```

**3. Copy the STARTTLS proxy file:**

Copy `xmpp-starttls-proxy.js` from this repository to the same directory.

**4. Start the services:**

```bash
docker-compose up -d
```

**5. Check logs:**

```bash
docker-compose logs -f
```

**6. Create admin user:**

```bash
docker exec -it whatsberry-prosody prosodyctl register admin whatsberry.descarga.media your-password
```

---

## 🔧 Manual Installation

### Step 1: Install Prosody

**Ubuntu/Debian:**
```bash
sudo apt update
sudo apt install prosody
```

**Arch Linux:**
```bash
sudo pacman -S prosody
```

### Step 2: Configure Prosody

Edit `/etc/prosody/prosody.cfg.lua`:

```lua
# See the Prosody config above in Docker Compose section
# Same configuration applies
```

**Restart Prosody:**
```bash
sudo systemctl restart prosody
sudo systemctl enable prosody
```

### Step 3: Install Slidge WhatsApp Gateway

**Install pipx:**
```bash
# Ubuntu/Debian
sudo apt install pipx
pipx ensurepath

# Arch Linux
sudo pacman -S python-pipx
```

**Install slidge-whatsapp:**
```bash
pipx install slidge-whatsapp
```

**Install qrcode-terminal dependency:**
```bash
pipx inject slidge-whatsapp qrcode-terminal
```

**Create systemd service (`/etc/systemd/system/slidge-whatsapp.service`):**

```ini
[Unit]
Description=Slidge WhatsApp XMPP Gateway
Documentation=https://slidge.im/
After=network.target prosody.service
Requires=prosody.service

[Service]
Type=simple
User=batman
Group=batman
WorkingDirectory=/home/batman
ExecStart=/home/batman/.local/bin/slidge-whatsapp
Restart=always
RestartSec=5
StandardOutput=journal
StandardError=journal
SyslogIdentifier=slidge-whatsapp

Environment="HOME=/home/batman"
Environment="SLIDGE_JID=whatsapp.localhost"
Environment="SLIDGE_SECRET=your-secret-here"

[Install]
WantedBy=multi-user.target
```

**Start slidge-whatsapp:**
```bash
sudo systemctl daemon-reload
sudo systemctl start slidge-whatsapp
sudo systemctl enable slidge-whatsapp
sudo systemctl status slidge-whatsapp
```

### Step 4: Install XMPP STARTTLS Proxy

**Install Node.js:**
```bash
# Ubuntu/Debian
sudo apt install nodejs npm

# Arch Linux
sudo pacman -S nodejs npm
```

**Copy proxy file:**
```bash
sudo cp xmpp-starttls-proxy.js /opt/whatsberry/
```

**Create systemd service (`/etc/systemd/system/xmpp-tls-proxy.service`):**

```ini
[Unit]
Description=XMPP STARTTLS Proxy for BlackBerry 10
Documentation=file:///opt/whatsberry/xmpp-starttls-proxy.js
After=network.target prosody.service
Requires=prosody.service

[Service]
Type=simple
User=batman
Group=batman
WorkingDirectory=/opt/whatsberry
ExecStart=/usr/bin/node /opt/whatsberry/xmpp-starttls-proxy.js
Restart=always
RestartSec=5
StandardOutput=journal
StandardError=journal
SyslogIdentifier=xmpp-tls-proxy

# Security
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/opt/whatsberry
CapabilityBoundingSet=CAP_NET_BIND_SERVICE

[Install]
WantedBy=multi-user.target
```

**Start the proxy:**
```bash
sudo systemctl daemon-reload
sudo systemctl start xmpp-tls-proxy
sudo systemctl enable xmpp-tls-proxy
sudo systemctl status xmpp-tls-proxy
```

---

## 🔐 XMPP STARTTLS Proxy Setup

The proxy is **critical** for BlackBerry 10 compatibility. BB10's Android Runtime only supports TLS 1.0, while modern Prosody uses TLS 1.2+.

### Proxy Features:

- ✅ Accepts STARTTLS on port 5222
- ✅ Negotiates TLS 1.0 with BB10 clients
- ✅ Forwards decrypted traffic to Prosody on port 5200
- ✅ Creates new connection to Prosody after TLS handshake

### Configuration:

The proxy is configured in `xmpp-starttls-proxy.js`:

```javascript
const PROXY_PORT = 5222;        // Public port (STARTTLS)
const PROSODY_PORT = 5200;      // Prosody internal port (no TLS)
const PROSODY_HOST = 'localhost';
```

### Logs:

```bash
# View proxy logs
sudo journalctl -u xmpp-tls-proxy.service -f

# View recent activity
sudo journalctl -u xmpp-tls-proxy.service -n 100
```

---

## 🔥 Firewall Configuration

### UFW (Ubuntu/Debian):

```bash
sudo ufw allow 5222/tcp comment 'XMPP STARTTLS'
sudo ufw allow 80/tcp comment 'HTTP'
sudo ufw allow 443/tcp comment 'HTTPS'
sudo ufw enable
```

**⚠️ IMPORTANT**: Use `allow`, NOT `limit` for port 5222. BB10 makes multiple connection attempts and `limit` will block legitimate traffic.

### Firewalld (CentOS/RHEL):

```bash
sudo firewall-cmd --permanent --add-port=5222/tcp
sudo firewall-cmd --permanent --add-port=80/tcp
sudo firewall-cmd --permanent --add-port=443/tcp
sudo firewall-cmd --reload
```

### Docker:

Docker automatically handles port mapping. Ensure your host firewall allows the mapped ports.

---

## 🌐 HTTP Server Setup (Optional but Recommended)

The HTTP server is used to serve the APK file and WhatsApp attachments (images, videos, documents).

### Quick Setup with Nginx:

**1. Install Nginx:**
```bash
# Ubuntu/Debian
sudo apt install nginx

# Arch Linux
sudo pacman -S nginx
```

**2. Basic Configuration:**

Create `/etc/nginx/sites-available/whatsberry`:

```nginx
server {
    listen 80;
    server_name whatsberry.descarga.media;  # Change to your domain

    # Serve APK for download
    location ~ \.apk$ {
        root /opt/whatsberry/public;
        add_header Content-Disposition "attachment";
    }

    # Serve WhatsApp attachments
    location /attachments/ {
        alias /home/batman/.local/share/slidge/attachments/;
        autoindex off;
        add_header Access-Control-Allow-Origin *;
        expires 7d;
    }

    # Main page
    location / {
        return 200 "Whatsberry Server\n";
        add_header Content-Type text/plain;
    }
}
```

**3. Enable site:**
```bash
sudo ln -s /etc/nginx/sites-available/whatsberry /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx
sudo systemctl enable nginx
```

**4. Add SSL (Recommended):**
```bash
# Install certbot
sudo apt install certbot python3-certbot-nginx  # Ubuntu
sudo pacman -S certbot certbot-nginx  # Arch

# Get certificate
sudo certbot --nginx -d whatsberry.descarga.media

# Auto-renewal
sudo systemctl enable certbot-renew.timer
```

**5. Copy APK to public directory:**
```bash
mkdir -p /opt/whatsberry/public
cp /opt/whatsberry/app/build/outputs/apk/debug/app-debug.apk \
   /opt/whatsberry/public/whatsberry-v3.3.1.apk
```

**For complete HTTP server documentation**, see [HTTP_SERVER-es.md](HTTP_SERVER-es.md) which covers:
- Multiple Nginx configurations (ports 80, 443, 8765, 8888, 9003)
- PHP endpoints for file uploads and audio conversion
- WebDAV configuration for attachments
- Security best practices
- Troubleshooting

---

## 🧪 Testing the Setup

### 1. Test Prosody:

```bash
# Check if Prosody is running
sudo systemctl status prosody

# Check if port 5200 is listening
ss -tlnp | grep 5200

# Create a test user
sudo prosodyctl register testuser whatsberry.descarga.media testpass
```

### 2. Test STARTTLS Proxy:

```bash
# Check if proxy is running
sudo systemctl status xmpp-tls-proxy

# Check if port 5222 is listening
ss -tlnp | grep 5222

# Test STARTTLS handshake
timeout 3 openssl s_client -connect localhost:5222 -starttls xmpp
```

Expected output:
```
CONNECTED(00000003)
depth=0 CN = localhost
...
New, TLSv1.0, Cipher is ECDHE-RSA-AES256-SHA
```

### 3. Test Slidge WhatsApp:

```bash
# Check if slidge is running
sudo systemctl status slidge-whatsapp

# View logs
sudo journalctl -u slidge-whatsapp -f
```

### 4. Test from Client:

1. Install WhatsBerry app on BB10
2. Configure server: `whatsberry.descarga.media`
3. Port: `5222`
4. Create account or login
5. Check logs:
   ```bash
   sudo journalctl -u xmpp-tls-proxy -f
   sudo journalctl -u prosody -f
   sudo journalctl -u slidge-whatsapp -f
   ```

---

## 🐛 Troubleshooting

### Issue: "Connection timeout"

**Cause**: Firewall blocking port 5222

**Solution**:
```bash
# Check firewall
sudo ufw status
sudo ufw allow 5222/tcp

# Check if port is listening
ss -tlnp | grep 5222
```

---

### Issue: "TLS handshake failed"

**Cause**: Proxy not running or TLS version mismatch

**Solution**:
```bash
# Check proxy status
sudo systemctl status xmpp-tls-proxy

# View proxy logs
sudo journalctl -u xmpp-tls-proxy -f

# Test TLS manually
timeout 3 openssl s_client -connect localhost:5222 -starttls xmpp -tls1
```

---

### Issue: "Authentication failed"

**Cause**: User doesn't exist or wrong password

**Solution**:
```bash
# Create user
sudo prosodyctl register username whatsberry.descarga.media password

# Verify user exists
sudo prosodyctl check
```

---

### Issue: "WhatsApp gateway not responding"

**Cause**: Slidge not running or misconfigured

**Solution**:
```bash
# Check slidge status
sudo systemctl status slidge-whatsapp

# View slidge logs
sudo journalctl -u slidge-whatsapp -f

# Restart slidge
sudo systemctl restart slidge-whatsapp
```

---

### Issue: "No notifications on BB10"

**Cause**: XMPPService not running as foreground service

**Solution**: Update to WhatsBerry v3.1.9+ which includes foreground service fix.

---

## 📚 Additional Documentation

- **[🌐 HTTP Server Setup (HTTP_SERVER-es.md)](HTTP_SERVER-es.md)** - Complete Nginx configuration guide
- **[🐛 Troubleshooting (TROUBLESHOOTING-es.md)](TROUBLESHOOTING-es.md)** - Detailed proxy troubleshooting
- **[📱 Client Configuration (CLIENT_CONFIGURATION-es.md)](CLIENT_CONFIGURATION-es.md)** - Client configuration guide
- **[🏗️ Architecture Diagrams (ARCHITECTURE_DIAGRAMS.md)](ARCHITECTURE_DIAGRAMS.md)** - Visual system architecture
- **[📝 Changelog (CHANGELOG-es.md)](CHANGELOG-es.md)** - Version history

**Documentation in root directory** (not yet migrated to docs/):
- [TECHNICAL.md](../TECHNICAL.md) - Technical architecture details
- [APK_BUILD_SIGNING_GUIDE.md](../APK_BUILD_SIGNING_GUIDE.md) - How to build the Android app

---

## 🔗 Useful Commands

### Prosody:
```bash
# Restart Prosody
sudo systemctl restart prosody

# Create user
sudo prosodyctl register <username> whatsberry.descarga.media <password>

# Check config
sudo prosodyctl check

# View logs
sudo journalctl -u prosody -f
```

### Slidge WhatsApp:
```bash
# Restart slidge
sudo systemctl restart slidge-whatsapp

# View logs
sudo journalctl -u slidge-whatsapp -f

# Check Python environment
pipx list
```

### XMPP Proxy:
```bash
# Restart proxy
sudo systemctl restart xmpp-tls-proxy

# View logs
sudo journalctl -u xmpp-tls-proxy -f

# Test connectivity
ss -tlnp | grep 5222
```

### Network Debugging:
```bash
# Check listening ports
ss -tlnp | grep -E "(5222|5200)"

# Test STARTTLS
timeout 3 openssl s_client -connect localhost:5222 -starttls xmpp

# Check firewall
sudo ufw status
sudo ufw allow 5222/tcp
```

---

## 🤝 Support

For issues and questions:
- Check [TROUBLESHOOTING-es.md](TROUBLESHOOTING-es.md) for detailed troubleshooting
- Check [HTTP_SERVER-es.md](HTTP_SERVER-es.md) for HTTP/Nginx issues
- Review logs: `sudo journalctl -u xmpp-tls-proxy -f`
- Consult [Complete Documentation Index](README-es.md)
- Open an issue on GitHub

---

**Built with ❤️ for BlackBerry 10 users**
