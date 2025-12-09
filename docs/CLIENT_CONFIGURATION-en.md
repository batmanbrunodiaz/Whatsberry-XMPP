# 🔧 Client Configuration - WhatsApp XMPP Client

## ✅ Server Status

### Active Services
- ✅ **XMPP STARTTLS Proxy** - Running (xmpp-starttls-proxy.js)
- ✅ **Prosody XMPP Server** - Running (Backend, port 5200)
- ✅ **Slidge-WhatsApp Gateway** - Running (Docker container)
- ✅ **Port 5222** - Open in UFW firewall (ALLOW, not LIMIT)

### Connection Architecture
```
BlackBerry Client → STARTTLS Proxy (5222) → Prosody Backend (5200)
                    [TLS 1.0+ Support]      [No TLS]
```

### Configured Ports
- **5222/tcp** - STARTTLS Proxy (XMPP clients - your BB10 app) ✅
- **5200/tcp** - Prosody Backend (no TLS, internal only) ✅
- **5347/tcp** - Components (slidge-whatsapp) ✅
- **5269/tcp** - Server to server (federation)

---

## 📱 Configuration in BlackBerry App

### Recommended Configuration (with STARTTLS Proxy)
Connect through the STARTTLS proxy that supports legacy devices:

```
XMPP Server Settings:
├─ Server IP/Host: whatsberry.descarga.media
├─ Port: 5222
└─ Domain: localhost

Account Credentials:
├─ Username: yourname
└─ Password: yourpassword

WhatsApp Gateway:
└─ Gateway JID: whatsapp.localhost
```

**Proxy Advantages**:
- ✅ TLS 1.0+ support (compatible with BlackBerry 10)
- ✅ Automatic STARTTLS
- ✅ No need to configure certificates manually
- ✅ Works with legacy devices

### Alternative Option: Local Connection (Same WiFi)
If your BlackBerry is on the same WiFi network as the server:

```
XMPP Server Settings:
├─ Server IP/Host: 10.0.0.2  (or 10.1.1.2, depends on your network)
├─ Port: 5222
└─ Domain: localhost
```

### Option 3: Connection from Internet
If you need to connect from outside your local network, you'll need:

1. Your public IP (find at: https://www.whatismyip.com/)
2. Configure port forwarding on your router: `5222 → 10.0.0.2:5222`
3. Use your public IP or domain (whatsberry.descarga.media) in the app

---

## 🔐 Create XMPP User

### Method 1: From the App (Recommended)
1. Open app on BB10
2. Fill in fields with configuration above
3. Click **"Register New Account"**
4. Done! The app creates the account automatically

### Method 2: From the Server
If you prefer to create manually:

```bash
# Create user
sudo prosodyctl adduser myuser@localhost

# It will ask for a password
# Then in the app use "Connect & Login"
```

---

## 📋 Complete Login Process

### Step 1: Install APK
```bash
# Connect BB10 via USB
adb devices

# Install
adb install -r /opt/whatsberry/app/build/outputs/apk/debug/app-debug.apk
```

### Step 2: Initial Configuration
1. Open "WhatsApp XMPP Client"
2. Configure according to your network (see above)
3. Click "Register New Account" (first time)
   - Or "Connect & Login" (if you already have an account)

### Step 3: Get WhatsApp QR
1. The app automatically requests the QR
2. It's displayed on screen
3. **Scan with official WhatsApp**:
   - WhatsApp mobile → Settings
   - Linked devices
   - Link a device
   - Scan QR

### Step 4: Done!
1. Click "Continue" in the app
2. You'll see your WhatsApp contacts
3. Click on contact → Chat
4. Send messages

---

## 🔍 Verify Connection

### From the Server
```bash
# Check if STARTTLS proxy is listening on 5222
ss -tlnp | grep 5222

# Check if Prosody backend is listening on 5200
ss -tlnp | grep 5200

# View STARTTLS proxy logs
sudo journalctl -u xmpp-tls-proxy -f

# View Prosody logs
sudo journalctl -u prosody -f

# Check if slidge is running
docker ps | grep slidge

# Verify UFW configuration
sudo ufw status | grep 5222
# Should show: 5222/tcp ALLOW (not LIMIT)
```

### From the Client (BB10)
If the app doesn't connect, verify:

1. **Correct network**: BB10 and server on same WiFi
2. **Correct IP**: Use `10.0.0.2` or `10.1.1.2`
3. **Firewall**: Port 5222 must be open
4. **Ping test**: `ping 10.0.0.2` from BB10 (if it has terminal)

---

## ⚠️ Troubleshooting

### Error: "Connection failed"
- **FIRST**: Verify STARTTLS proxy is running: `systemctl status xmpp-tls-proxy`
- Verify server IP/domain (whatsberry.descarga.media)
- Verify port 5222 is open in UFW
- Verify Prosody backend is running on port 5200
- View proxy logs: `sudo journalctl -u xmpp-tls-proxy -f`

### Error: "Authentication failed"
- First register account ("Register New Account")
- Or verify username/password if it already exists

### Error: "Connection timeout" or frequent disconnections
- **COMMON CAUSE**: UFW with LIMIT rule instead of ALLOW
- **SOLUTION**: Change to ALLOW to avoid rate limiting
  ```bash
  sudo ufw delete limit 5222/tcp
  sudo ufw allow 5222/tcp
  sudo ufw reload
  ```
- UFW rate limiting can block legitimate BB10 device connections

### Error: "TLS handshake failed" or "SSL error"
- STARTTLS proxy supports TLS 1.0+ for legacy devices
- Verify proxy is running: `systemctl status xmpp-tls-proxy`
- View proxy logs for details: `sudo journalctl -u xmpp-tls-proxy -f`
- Restart proxy if necessary: `systemctl restart xmpp-tls-proxy`

### QR Code doesn't appear
- Verify slidge-whatsapp is running
- View logs: `docker logs -f slidge-whatsapp`

### Messages don't arrive
- Verify WhatsApp is linked (scan QR)
- Wait a few seconds (initial sync)

---

## 📊 System Information

**Server:**
- OS: Arch Linux
- Prosody: 13.0.2
- Slidge-WhatsApp: 0.3.8
- Available IPs: 10.0.0.2, 10.1.1.2

**Client:**
- Platform: BlackBerry 10
- Android API: 18 (4.3)
- App Version: 3.3.1
- Package: com.whatsberry.xmpp
- APK: /opt/whatsberry/public/whatsberry-v3.3.1.apk

---

## 🎯 Complete Configuration Example

```
===========================================
  WHATSAPP XMPP CLIENT CONFIGURATION
  (With STARTTLS Proxy)
===========================================

XMPP Server Settings:
  Server IP/Host....: whatsberry.descarga.media
  Port..............: 5222
  Domain............: localhost

  NOTE: Proxy handles STARTTLS automatically
        Supports TLS 1.0+ for legacy devices

Account Credentials:
  Username..........: batman
  Password..........: myBatPassword123

WhatsApp Gateway:
  Gateway JID.......: whatsapp.localhost

===========================================
```

**Steps:**
1. Fill fields with these values
2. Click "Register New Account"
3. Wait for QR code
4. Scan with mobile WhatsApp
5. Click "Continue"
6. Start chatting!

**Alternative Configuration (Local Network):**
If you prefer to connect locally, you can use:
- Server IP/Host: 10.0.0.2 (or 10.1.1.2)
- Rest of parameters are the same

---

## 🔗 Useful Commands

```bash
# === STARTTLS Proxy Management ===
# View status
sudo systemctl status xmpp-tls-proxy

# View real-time logs
sudo journalctl -u xmpp-tls-proxy -f

# Restart proxy
sudo systemctl restart xmpp-tls-proxy

# === Prosody Management ===
# Restart Prosody backend
sudo systemctl restart prosody

# View Prosody status
sudo systemctl status prosody

# === Slidge-WhatsApp Management ===
# Restart slidge-whatsapp
docker restart slidge-whatsapp

# View logs
docker logs -f slidge-whatsapp

# === XMPP User Management ===
# View registered XMPP users
sudo prosodyctl list localhost

# Create user manually
sudo prosodyctl adduser user@localhost

# Delete user
sudo prosodyctl deluser user@localhost

# === Firewall Configuration ===
# Verify UFW rules
sudo ufw status verbose

# Ensure 5222 is ALLOW (not LIMIT)
sudo ufw delete limit 5222/tcp
sudo ufw allow 5222/tcp
sudo ufw reload

# Check for connections blocked by rate limiting
sudo journalctl -k | grep UFW | grep 5222
```

---

## 📞 Support

If you have problems:
1. Review proxy logs: `sudo journalctl -u xmpp-tls-proxy -f`
2. Review Prosody logs: `sudo journalctl -u prosody -f`
3. Verify slidge is running
4. Verify UFW configuration (should be ALLOW, not LIMIT)
5. Verify network connectivity
6. Consult README.md and documentation

---

**Ready to connect from your BlackBerry 10!** 🚀
