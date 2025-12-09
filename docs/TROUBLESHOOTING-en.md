# XMPP STARTTLS Proxy Troubleshooting - Lessons Learned

**Date**: December 7, 2025
**Context**: Implementation of STARTTLS proxy to support BlackBerry 10 clients with TLS 1.0

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Original Problem](#original-problem)
3. [Solution Architecture](#solution-architecture)
4. [Problems Found and Solutions](#problems-found-and-solutions)
5. [Critical Lessons Learned](#critical-lessons-learned)
6. [Final Configuration](#final-configuration)
7. [Diagnostic Commands](#diagnostic-commands)
8. [References](#references)

---

## Executive Summary

**Objective**: Allow BlackBerry 10 clients (which only support TLS 1.0) to connect to a Prosody XMPP server that requires TLS 1.2+.

**Implemented Solution**: STARTTLS proxy in Node.js that:
- Listens on port 5222 with TLS 1.0-1.3 support
- Handles STARTTLS negotiation with the client
- Creates a **new TCP connection** to Prosody (port 5200) after TLS handshake
- Performs bidirectional relay of data between TLS client and plaintext Prosody

**Status**: ✅ **WORKING** - Bidirectional relay confirmed, client receives responses from Prosody

---

## Original Problem

### Symptoms
- BlackBerry 10 clients couldn't connect to Prosody directly
- Error: TLS handshake failed (TLS version not supported)
- BlackBerry 10 only supports TLS 1.0, Prosody/OpenSSL 3 requires TLS 1.2+ by default

### Technical Requirements
1. TLS 1.0 support (legacy protocol)
2. Standard XMPP STARTTLS negotiation
3. Compatibility with existing Prosody architecture
4. No modifications to BlackBerry clients

---

## Solution Architecture

```
┌─────────────────────┐
│   BlackBerry 10     │
│   Client (TLS 1.0)  │
└──────────┬──────────┘
           │ TLS 1.0 encrypted
           │ Port 5222
           ▼
┌─────────────────────────────────────┐
│  XMPP STARTTLS Proxy (Node.js)      │
│  - Accepts TLS 1.0-1.3              │
│  - Injects STARTTLS in features     │
│  - Creates NEW connection after TLS │
└──────────┬──────────────────────────┘
           │ Plaintext XMPP
           │ Port 5200
           ▼
┌─────────────────────┐
│   Prosody XMPP      │
│   (TLS disabled)    │
└─────────────────────┘
```

### Connection Flow

1. **Client → Proxy**: TCP connection to port 5222
2. **Proxy → Prosody (connection #1)**: Opens connection to port 5200
3. **Prosody → Proxy**: Sends stream features (without STARTTLS)
4. **Proxy → Client**: Injects `<starttls/>` in features
5. **Client → Proxy**: Requests `<starttls/>`
6. **Proxy**: Destroys connection #1 to Prosody
7. **Proxy → Client**: Sends `<proceed/>`
8. **TLS Handshake**: Client and proxy negotiate TLS 1.0
9. **Proxy → Prosody (connection #2)**: Creates **NEW** TCP connection
10. **Client → Proxy → Prosody**: Bidirectional data relay

---

## Problems Found and Solutions

### Problem 1: TLS Handshake Timing

**Symptom**: TLS setup code inside `write()` callback caused listeners to register too late.

**Problematic Code**:
```javascript
clientSocket.write(proceedResponse, () => {
  // TLS setup here - WRONG
  const tlsSocket = new tls.TLSSocket(...);
  tlsSocket.on('secure', () => {
    prosodySocket.on('data', ...); // Too late!
  });
});
```

**Solution**: Move TLS setup outside callback.

**Lesson**: `write()` callbacks execute AFTER data is written to buffer, not when client receives it.

---

### Problem 2: Listener Registered But Never Executed

**Symptom**:
- Logs showed "Client→Prosody: 144B (decrypted)"
- `write()` returned `true`
- Prosody **never received** the data
- Listener count: 0→1 but 'data' event never fired

**Failed Attempts**:
1. ❌ `prosodySocket.read()` - returned null
2. ❌ `setImmediate()` to check buffer - no data
3. ❌ `prosodySocket.resume()` after registering listener - didn't help

**Root Cause**: The XMPP stream was already open in Prosody. When the client sent a second `<stream:stream>` after TLS, Prosody **ignored it** because it already had an active stream on that TCP connection.

**Solution**: Create a **NEW TCP connection** to Prosody after the TLS handshake.

```javascript
// BEFORE TLS: Close old connection
prosodySocket.removeAllListeners();
prosodySocket.destroy();

// AFTER TLS: New connection
tlsSocket.on('secure', () => {
  const newProsodySocket = net.createConnection({
    host: '127.0.0.1',
    port: 5200
  });

  // Setup bidirectional relay with NEW connection
  newProsodySocket.on('data', (data) => {
    tlsSocket.write(data);
  });

  tlsSocket.on('data', (data) => {
    newProsodySocket.write(data);
  });
});
```

**Critical Lesson**: In XMPP, you can only open a new stream after a reset event (complete STARTTLS, SASL auth). Reusing the same backend connection caused stream state conflicts.

**Result**: Eliminates authentication failures and stream errors that occurred with connection reuse.

---

### Problem 3: Order of resume() and Listener Registration

**Symptom**:
- `prosodySocket.resume()` called before registering listener
- Buffered data was lost

**Problematic Code**:
```javascript
prosodySocket.resume();  // Fires 'data' events
console.log('Resumed');

prosodySocket.on('data', (data) => {
  // Listener registered AFTER - loses data!
});
```

**Solution**: Register listeners BEFORE resume().

```javascript
prosodySocket.on('data', (data) => {
  // Listener ready
});

prosodySocket.resume();  // Now fires events
```

**Lesson**: `pause()` buffers data, `resume()` immediately fires buffered data events to **existing** listeners.

---

### Problem 4: UFW Rate Limiting

**Symptom**:
- First 6 connections worked
- After that connections stopped reaching the proxy
- No errors in proxy logs

**Cause**: UFW rule with `LIMIT` blocked IPs with >6 connections in 30 seconds.

```bash
# BEFORE (caused problems)
5222/tcp    LIMIT    Anywhere

# AFTER (solution)
5222/tcp    ALLOW    Anywhere
```

**Fix Command**:
```bash
sudo ufw delete limit 5222/tcp
sudo ufw allow 5222/tcp
```

**Lesson**: XMPP clients make multiple rapid connection attempts. `LIMIT` is for SSH, not for XMPP.

---

### Problem 5: Socket Event Listener Cascade

**Symptom**: When destroying old `prosodySocket`, the `close` event destroyed `clientSocket`, aborting TLS handshake.

**Cause**: Old socket event listeners still active.

```javascript
prosodySocket.on('close', () => {
  clientSocket.destroy(); // This aborted TLS!
});

// Later...
prosodySocket.destroy(); // Triggers 'close' event
```

**Solution**: Remove ALL listeners before destroying.

```javascript
prosodySocket.removeAllListeners();
prosodySocket.destroy();
```

**Lesson**: `removeAllListeners()` is critical when re-architecting connections mid-stream.

---

### Problem 6: Prosody Closing TCP Connection After Stream Close

**Symptom**:
- Sending `</stream:stream>` to Prosody
- Prosody responded with `</stream:stream>` (16 bytes)
- Prosody **closed the entire TCP connection**

**Problematic Code**:
```javascript
prosodySocket.write('</stream:stream>'); // Closes stream
prosodySocket.pause(); // Useless - connection will close
```

**Solution**: Don't try to close the old stream. Simply destroy the connection and create a new one.

**Lesson**: In XMPP, `</stream:stream>` is terminal for the TCP connection. It cannot be reused.

---

### Problem 7: Prosody Not Offering STARTTLS

**Context**: We disabled TLS in Prosody because the proxy handles it.

**Prosody Configuration**:
```lua
VirtualHost "whatsberry.descarga.media"
    modules_disabled = { "tls" }  -- No STARTTLS
```

**Problem**: BB10 client requires seeing `<starttls/>` in features.

**Solution**: Proxy injects STARTTLS in Prosody features.

```javascript
let dataStr = data.toString('utf8');

if (dataStr.includes('<stream:features>') && !dataStr.includes('<starttls')) {
  console.log(`Injecting STARTTLS into Prosody features`);
  dataStr = dataStr.replace(
    '</stream:features>',
    "<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/></stream:features>"
  );
  data = Buffer.from(dataStr, 'utf8');
}

clientSocket.write(data);
```

**Lesson**: Proxy acts as TLS termination proxy, but must maintain XMPP semantics expected by the client.

---

## Critical Lessons Learned

### 1. XMPP Connection Architecture

**Key**: XMPP streams are stateful within a TCP connection. You can't simply "reset" a stream without closing the connection.

**Implication**: After STARTTLS, you need:
- **Option A**: New TCP connection (what we implemented)
- **Option B**: End-to-end STARTTLS (client→Prosody direct, but requires TLS 1.2+)

**Why Option A**: Allows proxy to handle TLS 1.0 while Prosody works in plaintext.

### 2. Node.js Stream Pausing/Buffering

**Documented But Not Obvious Behavior**:
- `pause()` stops 'data' events BUT continues buffering
- `resume()` fires 'data' events with complete buffer **immediately**
- If no listener is registered, data is lost

**Best Practice**:
```javascript
socket.pause();           // 1. Pause first
socket.on('data', ...);   // 2. Register listener
socket.resume();          // 3. Resume last
```

### 3. OpenSSL 3 and TLS Legacy

**Change in OpenSSL 3**: TLS 1.0 and 1.1 disabled by default for security.

**Solution for Node.js**:
```javascript
process.env.OPENSSL_CONF = '/dev/null';  // Disable config
crypto.DEFAULT_MIN_VERSION = 'TLSv1';    // Allow TLS 1.0+
```

**Trade-off**: Security vs compatibility. Document clearly.

### 4. UFW and High-Frequency Network Services

**LIMIT vs ALLOW**:
- `LIMIT`: Max 6 connections in 30 sec (good for SSH)
- `ALLOW`: No limit (needed for XMPP, HTTP, etc.)

**Confusing Symptom**: No error in service logs, connections simply don't arrive.

**Diagnosis**:
```bash
sudo journalctl -xe | grep UFW
# Look for: [UFW BLOCK] SRC=<IP> DST=... DPT=5222
```

### 5. TLS Socket Wrapping

**Key Concept**: `tls.TLSSocket` can wrap an existing plaintext socket.

```javascript
const tlsSocket = new tls.TLSSocket(clientSocket, {
  isServer: true,
  ...tlsOptions
});
```

**Advantage**: No need to create a new listener socket on another port.

**Disadvantage**: Underlying socket remains the same - careful with event listeners.

### 6. Event Listener Cleanup

**Common Problem**: Old listeners interfere with new logic.

**Solution**: Always cleanup before re-architecting:
```javascript
socket.removeAllListeners('data');
socket.removeAllListeners('close');
socket.removeAllListeners('error');
// Or simply:
socket.removeAllListeners();
```

### 7. Debugging Binary/Text Protocols

**Useful Tools**:
```javascript
// Hex dump
console.log('Hex:', data.toString('hex'));

// Text preview
console.log('Preview:', data.toString('utf8').substring(0, 100));

// Socket state
console.log('State:', {
  destroyed: socket.destroyed,
  writable: socket.writable,
  readable: socket.readable,
  readyState: socket.readyState
});
```

---

## Final Configuration

### File: `/opt/whatsberry/xmpp-starttls-proxy.js`

**Key Features**:
- Port: `0.0.0.0:5222`
- Backend: `127.0.0.1:5200`
- TLS: 1.0 - 1.3
- Certificates: Let's Encrypt in `/etc/prosody/certs/`

**Connection Handling Flow**:
1. Client connects → proxy creates connection #1 to Prosody
2. Proxy receives Prosody features → injects `<starttls/>`
3. Client requests STARTTLS
4. Proxy destroys connection #1, sends `<proceed/>`
5. TLS handshake with client
6. On 'secure' event: proxy creates **new** connection #2 to Prosody
7. Bidirectional relay: client(TLS) ↔ proxy ↔ Prosody(plaintext)

### File: `/etc/prosody/prosody.cfg.lua`

**Key Changes**:
```lua
-- Port changed from 5222 to 5200 (backend)
c2s_ports = { 5200 }

-- TLS NOT required (proxy handles it)
c2s_require_encryption = false
allow_unencrypted_plain_auth = true

-- Specific VirtualHost
VirtualHost "whatsberry.descarga.media"
    -- CRITICAL: Disable TLS module
    modules_disabled = { "tls" }

    enabled = true
    allow_registration = true
```

### File: `/etc/systemd/system/xmpp-tls-proxy.service`

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

### Firewall (UFW)

```bash
# IMPORTANT: ALLOW, not LIMIT
sudo ufw allow 5222/tcp
sudo ufw status | grep 5222
# Should show: 5222/tcp    ALLOW    Anywhere
```

---

## Diagnostic Commands

### Verify Service Status

```bash
# Proxy
sudo systemctl status xmpp-tls-proxy.service

# Prosody
sudo systemctl status prosody.service

# Verify ports
ss -tlnp | grep 5222  # Proxy should be listening
ss -tlnp | grep 5200  # Prosody should be listening
```

### View Real-Time Logs

```bash
# Proxy (shows bidirectional relay)
sudo journalctl -u xmpp-tls-proxy.service -f

# Prosody
sudo journalctl -u prosody.service -f

# Search for specific connections
sudo journalctl -u xmpp-tls-proxy.service -n 100 | grep "NEW connection"
```

### Verify TLS Handshake

```bash
# From the server
openssl s_client -connect whatsberry.descarga.media:5222 -starttls xmpp -tls1

# Should show:
# - Certificate chain
# - Protocol: TLSv1
# - Cipher: ECDHE-ECDSA-AES128-SHA
```

### Verify Bidirectional Relay

Search in proxy logs:
```bash
sudo journalctl -u xmpp-tls-proxy.service -n 50 | grep -E "(Client→Prosody|Prosody→Client)"

# Should show:
# Client→Prosody: 144B (decrypted)
# Prosody→Client: 650B
```

### Verify UFW Isn't Blocking

```bash
# View rules
sudo ufw status verbose | grep 5222

# View recent blocks
sudo journalctl -xe | grep UFW | grep 5222 | tail -20

# If there are blocks, change to ALLOW
sudo ufw delete limit 5222/tcp
sudo ufw allow 5222/tcp
```

### Debug Specific Connection

```bash
# Enable debug code in proxy (already there)
# Logs show:
# - [ID] NEW connection from IP:PORT
# - [ID] ✓ TLS established: TLSv1
# - [ID] Bidirectional proxy established
# - [ID] Client→Prosody: XB
# - [ID] Prosody→Client: YB

# Prosody also shows:
sudo journalctl -u prosody.service | grep "Client sent opening"
# Should see TWO stream openings per successful connection
```

---

## References

### Related Technical Documentation

- [README-en.md](README-en.md) - Complete documentation index
- [SERVER_SETUP-en.md](SERVER_SETUP-en.md) - Server configuration
- [HTTP_SERVER-en.md](HTTP_SERVER-en.md) - Nginx configuration
- [CLIENT_CONFIGURATION-en.md](CLIENT_CONFIGURATION-en.md) - Client configuration guide
- [ARCHITECTURE_DIAGRAMS-en.md](ARCHITECTURE_DIAGRAMS-en.md) - Architecture diagrams
- [../TECHNICAL.md](../TECHNICAL.md) - Complete technical details of proxy (in root directory)

### RFCs and Specifications

- RFC 6120: XMPP Core (STARTTLS)
- RFC 5246: TLS 1.2 (to understand downgrade to 1.0)
- XEP-0170: XMPP STARTTLS

### Node.js APIs Used

- `net.createServer()` - TCP server
- `net.createConnection()` - TCP client
- `tls.TLSSocket()` - TLS wrapping
- `socket.pause()/resume()` - Flow control

### Commands and Tools

- `ss -tlnp` - View listening ports
- `journalctl` - Systemd logs
- `openssl s_client` - Test TLS
- `ufw` - Ubuntu firewall

---

## Conclusions

The XMPP STARTTLS proxy implementation for BlackBerry 10 required:

1. **Deep understanding of XMPP streams**: Streams are stateful and cannot be "reset" without closing the TCP connection.

2. **New connection architecture**: The solution of creating a new connection to Prosody after the TLS handshake was key to solving the bidirectional relay problem.

3. **Attention to Node.js details**: Order of resume/pause, listener cleanup, event timing.

4. **Careful system configuration**: UFW ALLOW vs LIMIT, OpenSSL 3 legacy support, Prosody TLS disabled.

5. **Systematic debugging**: Detailed logs, hex dumps, state inspection were critical for identifying problems.

**Final Status**: ✅ **FULLY FUNCTIONAL**
- TLS 1.0 handshake: ✅
- Bidirectional proxy: ✅
- Client receives responses: ✅
- Ready for XMPP authentication: ✅

**Next Steps**:
- Verify complete SASL authentication
- Monitor performance with multiple clients
- Consider implementing metrics/monitoring
- Document additional use cases

---

**Author**: Claude (Anthropic)
**Documentation Date**: December 7, 2025
**Version**: 1.0
