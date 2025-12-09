# 🚀 Quick Start Guide - WhatsBerry

**Estimated time: 15-20 minutes**

This guide will take you from zero to having WhatsApp working on your BlackBerry 10.

---

## 📋 Prerequisites

### For the Server
- A Linux server (Ubuntu 22.04+, Arch Linux, or Debian)
- 1GB RAM minimum (2GB recommended)
- Domain or public IP
- Ports 5222, 80, 443 open in firewall

### For the Client
- BlackBerry 10 device (BlackBerry Q10, Z10, Z30, Passport, Classic, etc.)
- Android Runtime enabled
- Internet access (WiFi or mobile data)

---

## 🖥️ Step 1: Configure the Server

### Option A: Using Docker (Recommended - Slidge Only)

```bash
# 1. Install Prosody
sudo apt update && sudo apt install prosody

# 2. Configure Prosody
sudo nano /etc/prosody/prosody.cfg.lua
```

Copy the content from [`prosody-config/prosody.cfg.lua`](../prosody-config/prosody.cfg.lua) and change:
- `whatsberry.descarga.media` → your domain
- `component_secret` → generate one with `openssl rand -base64 32`

```bash
# 3. Restart Prosody
sudo systemctl restart prosody
sudo systemctl enable prosody

# 4. Install Node.js for the proxy
sudo apt install nodejs npm

# 5. Copy the STARTTLS proxy
sudo mkdir -p /opt/whatsberry
sudo cp xmpp-starttls-proxy.js /opt/whatsberry/
```

Create the systemd service for the proxy (`/etc/systemd/system/xmpp-tls-proxy.service`):

```ini
[Unit]
Description=XMPP STARTTLS Proxy for BlackBerry 10
After=network.target prosody.service
Requires=prosody.service

[Service]
Type=simple
User=your-user
WorkingDirectory=/opt/whatsberry
ExecStart=/usr/bin/node /opt/whatsberry/xmpp-starttls-proxy.js
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
# 6. Start the proxy
sudo systemctl daemon-reload
sudo systemctl start xmpp-tls-proxy
sudo systemctl enable xmpp-tls-proxy

# 7. Run Slidge WhatsApp with Docker
mkdir -p ~/.local/share/slidge
```

Create `~/.local/share/slidge/slidge.conf`:

```ini
[global]
jid = whatsapp.localhost
secret = YOUR_SECRET_HERE  # Same as in Prosody
server = localhost
port = 5347
home-dir = /data
debug = true

user-jid-validator = ^.*@(localhost|your-domain\.com)(/.*)?$
```

```bash
# 8. Start Slidge with Docker
docker run -d \
  --name slidge-whatsapp \
  --network host \
  --restart unless-stopped \
  -v ~/.local/share/slidge:/data \
  --log-opt max-size=10m \
  --log-opt max-file=5 \
  codeberg.org/slidge/slidge-whatsapp:latest-amd64 \
  -c /data/slidge.conf

# 9. Configure firewall
sudo ufw allow 5222/tcp
sudo ufw enable
```

### Option B: Full Docker Compose (Untested)

See [`docker-compose.yml`](../docker-compose.yml) for complete configuration.

**⚠️ NOTE**: This option has not been tested. We recommend Option A (Hybrid Setup).

---

## 📱 Step 2: Install the App on BlackBerry 10

### Method 1: Direct Download (Easiest)

1. On your BlackBerry 10, open the browser
2. Go to: `https://your-domain.com/whatsberry-v3.3.1.apk`
3. Download the APK
4. When finished, tap "Open" to install
5. Accept permissions

### Method 2: ADB (From PC)

```bash
# Connect your BB10 via USB
adb devices

# Install the APK
adb install -r whatsberry-v3.3.1.apk
```

---

## 🔐 Step 3: Configure the App

1. **Open WhatsBerry** on your BlackBerry 10

2. **Enter server details**:
   ```
   Server: your-domain.com
   Port: 5222
   Domain: your-domain.com
   Username: yourname
   Password: yourpassword
   Gateway: whatsapp.localhost
   ```

3. **Register a new account**:
   - Tap "Register New Account" (first time)
   - Or "Connect & Login" if you already have an account

4. **Wait for QR Code**:
   - The app will connect to the server
   - In a few seconds a QR code will appear

---

## 📲 Step 4: Link WhatsApp

1. **Open WhatsApp** on your main phone

2. **Go to Linked Devices**:
   - Android: Menu → Linked devices
   - iPhone: Settings → Linked devices

3. **Scan the QR**:
   - Tap "Link a device"
   - Point the camera at the QR on your BlackBerry 10
   - Wait for confirmation

4. **Done!**:
   - The app will show "Connected"
   - You'll see your WhatsApp contacts
   - You can now send and receive messages

---

## ✅ Verification

### Check that everything works:

1. **On the server**:
   ```bash
   # Verify all services are running
   sudo systemctl status prosody
   sudo systemctl status xmpp-tls-proxy
   docker ps | grep slidge

   # View logs in real-time
   sudo journalctl -u xmpp-tls-proxy -f
   ```

2. **In the app**:
   - You should see your WhatsApp contacts
   - Try sending a message to yourself
   - Verify it arrives on your main phone

---

## 🐛 Common Issues

### "Connection failed"

**Solution**:
```bash
# Verify the proxy is running
sudo systemctl status xmpp-tls-proxy

# Verify the port is open
sudo ufw status | grep 5222

# View logs for errors
sudo journalctl -u xmpp-tls-proxy -n 50
```

### "Authentication failed"

**Common causes**:
- User doesn't exist → Use "Register New Account"
- Wrong password → Verify credentials
- Wrong domain → Must match your Prosody VirtualHost

### QR Code doesn't appear

**Solution**:
```bash
# Verify Slidge is running
docker logs slidge-whatsapp

# Restart Slidge
docker restart slidge-whatsapp
```

### App disconnects constantly

**Solution**:
- Make sure you're using WhatsBerry v3.1.9+ (has Foreground Service)
- Check battery settings on BB10
- Verify firewall doesn't have LIMIT rule (must be ALLOW):
  ```bash
  sudo ufw delete limit 5222/tcp
  sudo ufw allow 5222/tcp
  ```

---

## 📚 Next Steps

Now that you have WhatsBerry working:

- **[Advanced Configuration](SERVER_SETUP-en.md)** - Advanced server options
- **[Troubleshooting](TROUBLESHOOTING-en.md)** - Complete troubleshooting guide
- **[Client Configuration](CLIENT_CONFIGURATION-en.md)** - Advanced app options
- **[Changelog](CHANGELOG-en.md)** - What's new in each version

---

## 🆘 Need Help?

If you have problems:

1. **Check the logs**:
   ```bash
   sudo journalctl -u xmpp-tls-proxy -f
   sudo journalctl -u prosody -f
   docker logs -f slidge-whatsapp
   ```

2. **Consult the complete documentation**:
   - [Detailed Troubleshooting](TROUBLESHOOTING-en.md)
   - [Technical Documentation](../TECHNICAL.md)

3. **Report an issue**:
   - GitHub Issues: [whatsberry/issues](https://github.com/yourusername/whatsberry/issues)

---

**Enjoy WhatsApp on your BlackBerry 10!** 🎉
