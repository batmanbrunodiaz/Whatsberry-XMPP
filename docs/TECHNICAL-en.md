<div align="center">

# <img src="public/img/apple-touch-icon.png" alt="WhatsBerry Logo" width="50" align="center"/> WhatsBerry - Technical Documentation

Complete technical reference for developers, contributors, and code reviewers.

[![Discord](https://img.shields.io/badge/Discord-Join%20Community-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/MtU7JqrEnW) [![Buy Me A Coffee](https://img.shields.io/badge/Buy%20Me%20A%20Coffee-Support%20Dev-FFDD00?style=for-the-badge&logo=buy-me-a-coffee&logoColor=white)](https://buymeacoffee.com/danzkigg)

</div>

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [XMPP STARTTLS Proxy Architecture](#xmpp-starttls-proxy-architecture)
3. [Technology Stack](#technology-stack)
4. [Authentication & Security](#authentication--security)
5. [API Reference](#api-reference)
6. [WebSocket Events](#websocket-events)
7. [Session Management](#session-management)
8. [Media Processing](#media-processing)
9. [Configuration](#configuration)
10. [Error Handling](#error-handling)
11. [Performance Optimization](#performance-optimization)
12. [Debugging](#debugging)
13. [Contributing](#contributing)
14. [License](#license)

---

## Architecture Overview

WhatsBerry uses a multi-layered architecture:

```
┌─────────────────┐
│  Android Client │
└────────┬────────┘
         │ (HTTP/WebSocket)
         ▼
┌─────────────────┐
│  Express Server │ ← API Routes, Middleware
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Session Manager │ ← Session lifecycle, health checks
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ WhatsApp Client │ ← whatsapp-web.js + Puppeteer
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  WhatsApp Web   │
└─────────────────┘
```

### Key Components

- **Express Server**: HTTP API and static file serving
- **Socket.IO**: Real-time bidirectional communication
- **Session Manager**: Handles session lifecycle, health monitoring, and reconnection
- **WhatsApp Client**: Puppeteer-based WhatsApp Web automation
- **Audio Converter**: FFmpeg-based media transcoding

---

## XMPP STARTTLS Proxy Architecture

### Overview

WhatsBerry includes a custom XMPP STARTTLS proxy (`xmpp-starttls-proxy.js`) to enable BlackBerry 10 devices to connect to the Prosody XMPP server with legacy TLS support. This proxy sits between BB10 clients and Prosody, handling TLS negotiation with support for older TLS versions (TLSv1.0+) that modern Prosody installations no longer support directly.

### Architecture Diagram

```
┌─────────────────┐
│  BB10 Client    │ (Requires TLS 1.0+)
└────────┬────────┘
         │ Port 5222 (XMPP)
         ▼
┌─────────────────────────────┐
│  STARTTLS Proxy             │
│  - Listens: 0.0.0.0:5222    │
│  - Handles STARTTLS         │
│  - TLS 1.0 - 1.3 support    │
│  - Certificate management   │
└────────┬────────────────────┘
         │ Port 5200 (Plain XMPP)
         ▼
┌─────────────────────────────┐
│  Prosody XMPP Server        │
│  - Listens: 127.0.0.1:5200  │
│  - TLS module DISABLED      │
│  - Handles authentication   │
└─────────────────────────────┘
```

### Technical Specifications

| Component | Value | Purpose |
|-----------|-------|---------|
| Listen Address | `0.0.0.0:5222` | Standard XMPP client port |
| Backend | `127.0.0.1:5200` | Prosody non-TLS port |
| TLS Versions | TLSv1.0 - TLSv1.3 | Legacy BB10 compatibility |
| Certificate | `/etc/prosody/certs/whatsberry.descarga.media.crt` | SSL certificate |
| Private Key | `/etc/prosody/certs/whatsberry.descarga.media.key` | SSL private key |
| Node.js Modules | `net`, `tls`, `fs`, `crypto` | Core networking |

### Key Implementation Details

#### 1. Legacy TLS Support

The proxy enables TLS 1.0 support (disabled by default in modern OpenSSL 3) to accommodate BlackBerry 10 devices:

```javascript
// Enable legacy TLS protocols in OpenSSL 3
process.env.OPENSSL_CONF = '/dev/null';
crypto.DEFAULT_MIN_VERSION = 'TLSv1';

tlsOptions = {
  minVersion: 'TLSv1',
  maxVersion: 'TLSv1.3',
  secureOptions: crypto.constants.SSL_OP_NO_SSLv2 | crypto.constants.SSL_OP_NO_SSLv3
};
```

**Why**: BlackBerry 10.3.3 only supports TLS 1.0 and 1.1, while modern servers require TLS 1.2+. This proxy bridges the compatibility gap.

#### 2. STARTTLS Negotiation Flow

1. **Initial Connection**: Client connects to proxy on port 5222
2. **Proxy → Prosody**: Proxy creates initial connection to Prosody on port 5200
3. **Stream Initiation**: Client sends XMPP stream opening
4. **Feature Advertisement**: Proxy ensures STARTTLS is advertised (injected if missing)
5. **STARTTLS Request**: Client sends `<starttls/>` stanza
6. **Critical Decision**: Proxy destroys initial Prosody connection
7. **TLS Upgrade**: Proxy wraps client socket with TLS
8. **New Backend Connection**: Proxy creates FRESH Prosody connection post-TLS
9. **Encrypted Communication**: All subsequent traffic flows through encrypted tunnel

#### 3. Critical Architectural Decision: New Connection After TLS

**Problem**: Initially, the proxy attempted to reuse the same Prosody connection before and after TLS upgrade, causing XMPP stream conflicts and authentication failures.

**Solution**: The proxy now creates a **completely new** connection to Prosody after TLS handshake:

```javascript
// Before TLS: Initial Prosody connection
const prosodySocket = net.createConnection({ port: 5200 });

// When STARTTLS requested:
prosodySocket.removeAllListeners(); // Clean up
prosodySocket.destroy();            // Close old connection

// After TLS handshake:
const newProsodySocket = net.createConnection({ port: 5200 }); // Fresh connection
```

**Why This Works**:
- XMPP protocol requires a fresh stream after STARTTLS
- Reusing the same backend connection created stream state conflicts
- Each Prosody connection maintains its own XMPP stream context
- New connection = clean XMPP stream = proper authentication flow

**Result**: Eliminates authentication failures and stream errors that occurred with connection reuse.

#### 4. STARTTLS Feature Injection

The proxy actively monitors Prosody's stream features and injects STARTTLS if absent:

```javascript
if (dataStr.includes('<stream:features>') && !dataStr.includes('<starttls')) {
  dataStr = dataStr.replace(
    '</stream:features>',
    "<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/></stream:features>"
  );
}
```

**Why**: Ensures clients always see STARTTLS as available, even if Prosody's configuration changes.

#### 5. Bidirectional Data Flow

**Pre-TLS** (plaintext):
```
Client ←→ Proxy ←→ Initial Prosody Connection
```

**Post-TLS** (encrypted):
```
Client ←(TLS)→ Proxy ←(plaintext)→ New Prosody Connection
       encrypted              decrypted
```

The proxy:
- Decrypts data from TLS-wrapped client socket
- Forwards plaintext to new Prosody connection
- Encrypts Prosody responses back to client

### Prosody Configuration Changes

To work with the STARTTLS proxy, Prosody's TLS module must be **disabled** for the WhatsBerry VirtualHost:

**File**: `/etc/prosody/prosody.cfg.lua`

```lua
VirtualHost "whatsberry.descarga.media"
  modules_disabled = { "tls" }  -- Proxy handles TLS

  c2s_ports = { 5200 }          -- Non-TLS port for proxy
  legacy_ssl_ports = {}         -- Disable direct TLS
```

**Why**:
- Prosody no longer negotiates TLS directly
- Proxy handles all TLS operations
- Prosody receives plaintext connections from localhost
- Separation of concerns: TLS termination vs XMPP logic

### Connection Lifecycle

1. **Client Connects**: BB10 client connects to `whatsberry.descarga.media:5222`
2. **Initial Handshake**: Proxy forwards XMPP stream opening to Prosody
3. **Feature Discovery**: Client receives injected STARTTLS feature
4. **STARTTLS Trigger**: Client sends `<starttls/>` stanza
5. **Proceed Response**: Proxy sends `<proceed xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>`
6. **TLS Handshake**: TLS 1.0-1.3 negotiation (BB10 typically uses TLS 1.0)
7. **Connection Reset**: Old Prosody connection destroyed
8. **New Backend**: Fresh Prosody connection established
9. **Stream Restart**: Client initiates new XMPP stream (encrypted)
10. **Authentication**: SASL authentication proceeds normally
11. **Active Session**: Encrypted XMPP communication

### Logging and Monitoring

Each connection is assigned a unique ID for tracking:

```
[1] NEW connection from 192.168.1.100:54321
[1] Connected to Prosody
[1] STARTTLS requested by client
[1] Removed all Prosody listeners
[1] Closed old Prosody connection
[1] ✓ TLS established: TLSv1, Cipher: ECDHE-RSA-AES256-SHA
[1] New Prosody connection established after TLS
[1] Prosody→Client: 256B
[1] Client→Prosody: 512B (decrypted)
[1] Client closed - Sent: 2048B, Received: 4096B
```

**Metrics Logged**:
- Connection establishment/teardown
- TLS protocol and cipher suite used
- Bytes transferred (client→proxy, proxy→Prosody)
- Data flow direction and size
- Error conditions

### Security Considerations

**Strengths**:
- TLS encryption for all client traffic
- Certificate validation (client trusts server cert)
- No credential exposure over plaintext
- Localhost-only Prosody binding (127.0.0.1)

**Trade-offs**:
- TLS 1.0 support required for BB10 (deprecated protocol)
- Proxy has access to decrypted XMPP traffic
- Additional attack surface compared to direct Prosody connection

**Mitigation**:
- Proxy runs on same server as Prosody (trusted environment)
- Prosody only accessible via localhost
- External exposure limited to TLS-encrypted port 5222
- Regular security updates for Node.js and OpenSSL

### Deployment and Management

**Starting the Proxy**:
```bash
node /opt/whatsberry/xmpp-starttls-proxy.js
```

**Running as Service** (systemd):
```ini
[Unit]
Description=XMPP STARTTLS Proxy for BlackBerry 10
After=network.target prosody.service

[Service]
Type=simple
User=whatsberry
ExecStart=/usr/bin/node /opt/whatsberry/xmpp-starttls-proxy.js
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

**Process Management**:
- Graceful shutdown on SIGINT/SIGTERM
- Automatic cleanup of active connections
- Error recovery with connection destruction

### Troubleshooting

**Common Issues**:

| Issue | Symptom | Solution |
|-------|---------|----------|
| Port in use | `EADDRINUSE` error | Check for other services on port 5222, kill or reconfigure |
| Certificate errors | TLS handshake fails | Verify cert/key files exist and are readable |
| Authentication failure | Client disconnects after TLS | Ensure Prosody TLS module is disabled, check new connection logic |
| No STARTTLS offered | Client never attempts TLS | Verify feature injection code, check Prosody features |
| Connection hangs | No data flow post-TLS | Check new Prosody connection establishment, verify bidirectional handlers |

**Debug Steps**:
1. Check proxy logs for connection ID tracking
2. Verify TLS protocol negotiated (should show TLSv1.x)
3. Confirm new Prosody connection created after TLS
4. Monitor byte counters for data flow
5. Verify Prosody logs for authentication attempts

### Performance Characteristics

- **Latency**: Minimal (~1-2ms) for localhost Prosody connection
- **Throughput**: Line-rate for text-based XMPP traffic
- **Memory**: ~10-20MB per active connection (Node.js + buffers)
- **CPU**: Negligible except during TLS handshake
- **Concurrent Connections**: Limited by system resources, typically 1000+ supported

### Future Enhancements

Potential improvements:
- Connection pooling for Prosody backend
- WebSocket support for modern clients
- Rate limiting and DoS protection
- TLS session resumption for faster reconnects
- Metrics export (Prometheus/StatsD)
- Health check endpoint

---

## Technology Stack

### Core Dependencies

| Package | Version | Purpose |
|---------|---------|---------|
| Node.js | ≥16.0.0 | Runtime environment |
| Express | ^4.18.2 | Web server framework |
| Socket.IO | ^4.7.2 | WebSocket communication |
| whatsapp-web.js | ^1.34.1 | WhatsApp Web interface |
| Puppeteer | ^24.10.2 | Headless Chrome automation |
| fluent-ffmpeg | ^2.1.3 | Audio conversion |

### Development Tools

- **nodemon**: Auto-restart during development
- **PM2**: Production process management (optional)
- **dotenv**: Environment variable management

---

## Authentication & Security

### Security Model

**API Key Authentication**: All requests require a valid API key in the `X-API-Key` header.

### Authentication Flow

```
┌─────────┐
│  Start  │
└────┬────┘
     │
     ▼
┌──────────────────────┐
│ POST /create-session │ ← API Key required
│ Body: { deviceInfo } │
└──────────┬───────────┘
           │
           ▼ Returns sessionId
┌────────────────────────────┐
│ POST /start-session/:id    │ ← API Key required
│ Launches WhatsApp client   │
└──────────┬─────────────────┘
           │
           ▼ Emits QR via WebSocket
┌──────────────────────────────┐
│ User scans QR code           │
│ WhatsApp session established │
└──────────┬───────────────────┘
           │
           ▼
┌────────────────────────┐
│ Authenticated Requests │ ← API Key required
│ GET /chats, /messages  │
└────────────────────────┘
```

### Security Features

- **API Key Authentication**: All API requests require a valid API key
- **Automatic Cleanup**: Inactive sessions removed after 24 hours
- **No Data Storage**: Messages are never stored on the server
- **Self-Hosted**: You control the server and your data

### Middleware Chain

```javascript
// API Key required for all endpoints
POST /create-session
  → apiKeyMiddleware

GET /session/:sessionId/chats
  → apiKeyMiddleware
```

---

## API Reference

### Base URL

Self-hosted: `http://your-server:3000`

### Authentication Headers

```http
X-API-Key: your-api-key-here
```

---

### Public Endpoints

No authentication required.

#### `GET /`
Landing page with app download links.

#### `GET /health`
Server health check with metrics.

**Response:**
```json
{
  "status": "healthy",
  "uptime": 86400,
  "memory": {
    "used": 256000000,
    "total": 512000000
  },
  "activeSessions": 12,
  "readySessions": 10
}
```

#### `GET /status`
Simple status check.

**Response:**
```json
{
  "status": "ok",
  "timestamp": 1234567890
}
```

---

### Session Management Endpoints

API Key required.

#### `POST /create-session`

Creates a new session or retrieves existing one for a device.

**Request:**
```json
{
  "deviceInfo": {
    "deviceId": "unique-device-id",
    "deviceName": "BlackBerry Q10",
    "model": "Q10",
    "osVersion": "10.3.3"
  }
}
```

**Response:**
```json
{
  "sessionId": "abc123-def456-ghi789",
  "userId": "hashed-user-id",
  "message": "Session created successfully"
}
```

**Status Codes:**
- `200`: Session created/retrieved
- `400`: Invalid device info
- `401`: Invalid API key
- `500`: Server error

---

#### `POST /start-session/:sessionId`

Initializes WhatsApp client and generates QR code.

**Parameters:**
- `sessionId`: Session ID from create-session

**Response:**
```json
{
  "message": "Session initialization started"
}
```

**WebSocket Events:**
After calling this endpoint, listen for:
- `qr`: QR code data (base64 image)
- `ready`: Session authenticated and ready
- `loading_screen`: Loading progress updates

**Status Codes:**
- `200`: Initialization started
- `404`: Session not found
- `410`: Session replaced by newer one
- `500`: Initialization failed

---

#### `GET /session/:sessionId/status`

Check current session status.

**Response:**
```json
{
  "sessionId": "abc123",
  "isReady": true,
  "hasQR": false,
  "phoneNumber": "1234567890",
  "lastActivity": 1234567890000
}
```

---

#### `GET /session/:sessionId/qr`

Retrieve current QR code (if available).

**Response:**
```json
{
  "qr": "data:image/png;base64,iVBORw0KG..."
}
```

**Status Codes:**
- `200`: QR code available
- `404`: Session or QR not found

---

### Chat Endpoints

API Key required.

#### `GET /session/:sessionId/chats`

Retrieve list of chats.

**Query Parameters:**
- `includeProfilePics` (default: `true`): Include profile pictures
- `limit`: Number of chats to return
- `offset` (default: `0`): Pagination offset

**Response:**
```json
{
  "chats": [
    {
      "id": "1234567890@c.us",
      "name": "John Doe",
      "isGroup": false,
      "unreadCount": 3,
      "timestamp": 1234567890,
      "profilePic": "https://...",
      "lastMessage": {
        "body": "Hello!",
        "timestamp": 1234567890,
        "fromMe": false,
        "ack": 1
      }
    }
  ],
  "total": 50,
  "offset": 0,
  "limit": 50,
  "hasMore": false
}
```

**Status Codes:**
- `200`: Success
- `400`: Client not ready
- `404`: Session not found
- `503`: Session disconnected, reconnecting

---

#### `GET /session/:sessionId/chat/:chatId/messages`

Get messages from a specific chat.

**Query Parameters:**
- `limit` (default: `50`): Number of messages
- `includeMedia` (default: `false`): Include media data
- `includeContacts` (default: `true`): Include contact info

**Response:**
```json
{
  "messages": [
    {
      "id": "msg123",
      "body": "Hello",
      "timestamp": 1234567890,
      "fromMe": false,
      "hasMedia": false,
      "type": "chat",
      "ack": 2,
      "from": "1234567890@c.us",
      "to": "0987654321@c.us"
    }
  ]
}
```

---

#### `POST /session/:sessionId/send-message`

Send a text message.

**Request:**
```json
{
  "to": "1234567890@c.us",
  "message": "Hello, world!"
}
```

**Response:**
```json
{
  "success": true,
  "messageId": "msg123_serialized",
  "timestamp": 1234567890
}
```

**Status Codes:**
- `200`: Message sent
- `400`: Missing parameters or client not ready
- `404`: Session not found
- `503`: Session unhealthy, reconnecting

---

#### `POST /session/:sessionId/send-media`

Send media message (image, video, document, audio).

**Request:**
```json
{
  "to": "1234567890@c.us",
  "media": "data:image/png;base64,iVBORw0KG...",
  "caption": "Check this out!",
  "filename": "photo.png"
}
```

**Response:**
```json
{
  "success": true,
  "messageId": "msg456_serialized",
  "timestamp": 1234567890
}
```

---

#### `POST /session/:sessionId/chat/:chatId/mark-read`

Mark all messages in a chat as read.

**Response:**
```json
{
  "success": true,
  "chatId": "1234567890@c.us",
  "message": "Chat marked as read"
}
```

---

#### `GET /session/:sessionId/contacts`

Get user's contacts.

**Query Parameters:**
- `includeProfilePics` (default: `true`)
- `limit`: Number of contacts
- `offset` (default: `0`)

**Response:**
```json
{
  "contacts": [
    {
      "id": "1234567890@c.us",
      "name": "John Doe",
      "number": "1234567890",
      "isMyContact": true,
      "profilePic": "https://..."
    }
  ],
  "total": 100,
  "offset": 0,
  "limit": 100,
  "hasMore": false
}
```

---

#### `GET /session/:sessionId/group/:groupId/participants`

Get participants of a group chat.

**Response:**
```json
{
  "groupId": "123456789@g.us",
  "groupName": "Team Chat",
  "participants": [
    {
      "id": "1234567890@c.us",
      "phoneId": "1234567890@c.us",
      "name": "John Doe",
      "number": "1234567890",
      "isAdmin": true,
      "isSuperAdmin": false
    }
  ],
  "participantCount": 5
}
```

---

### Media Endpoints

API Key required.

#### `GET /session/:sessionId/message/:messageId/media`

Download media from a message.

**Query Parameters:**
- `download` (default: `false`): Force download vs inline
- `format` (default: `original`): Conversion format (e.g., `mp3` for audio)

**Response Headers:**
```
Content-Type: audio/mpeg
Content-Length: 1234567
Content-Disposition: inline; filename="audio.mp3"
X-Media-Type: ptt
X-Original-Mimetype: audio/ogg
X-Converted: true
```

**Response:** Binary media data

---

#### `GET /session/:sessionId/chat/:chatId/media/:messageIndex`

Download media by message index in chat.

**Query Parameters:**
- `download` (default: `false`)
- `limit` (default: `50`): Messages to fetch for indexing
- `format` (default: `original`)

**Response:** Binary media data (same headers as above)

---

#### `GET /formats/:mimetype`

Get supported conversion formats for a media type.

**Example:** `/formats/audio%2Fogg`

**Response:**
```json
{
  "inputMimetype": "audio/ogg",
  "supportedFormats": ["original", "mp3"],
  "conversionInfo": {
    "mp3": {
      "description": "Convert to MP3",
      "quality": "128kbps, 44.1kHz, Stereo",
      "usage": "Add ?format=mp3 to media download URL"
    }
  }
}
```

---

#### `GET /ffmpeg/status`

Check FFmpeg availability and installation.

**Response:**
```json
{
  "available": true,
  "path": "/usr/bin/ffmpeg",
  "platform": "linux",
  "audioConversionEnabled": true
}
```

---

#### `GET /audio-cache/stats`

Get audio conversion cache statistics.

**Response:**
```json
{
  "totalEntries": 15,
  "expiredEntries": 3,
  "totalCacheSizeKB": 5120,
  "cacheTTLHours": 2,
  "entries": [...]
}
```

---

### Debug Endpoints

API Key required. For development/debugging only.

#### `GET /debug/sessions`

List all active sessions.

**Response:**
```json
{
  "sessions": [
    {
      "sessionId": "abc123",
      "userId": "user123",
      "isReady": true,
      "lastActivity": 1234567890000
    }
  ]
}
```

---

#### `GET /debug/session-details`

Detailed session information including phone numbers.

**Response:**
```json
{
  "sessions": [
    {
      "sessionId": "abc123",
      "userId": "user123",
      "phoneNumber": "1234567890",
      "isReady": true,
      "isAuthenticated": true,
      "lastActivity": 1234567890000,
      "hasQR": false
    }
  ],
  "totalSessions": 1
}
```

---

#### `POST /session/:sessionId/logout`

Logout and destroy session.

**Headers:** API Key required

**Response:**
```json
{
  "message": "Session destroyed successfully"
}
```

---

## WebSocket Events

Connect to Socket.IO server at the base URL.

### Client → Server Events

#### `join_session`
Join a session room to receive events.

```javascript
socket.emit('join_session', sessionId);
```

**Response:**
```javascript
socket.on('session_joined', (data) => {
  // { sessionId: 'abc123', socketId: 'xyz789' }
});
```

---

#### `request_qr`
Request current QR code.

```javascript
socket.emit('request_qr', sessionId);
```

---

#### `request_session_status`
Request session status.

```javascript
socket.emit('request_session_status', sessionId);
```

**Response:**
```javascript
socket.on('session_status', (data) => {
  // { sessionId, isReady, hasQR, lastActivity, phoneNumber }
});
```

---

#### `ping`
Ping server (for connection testing).

```javascript
socket.emit('ping');
```

**Response:**
```javascript
socket.on('pong', (data) => {
  // { timestamp: 1234567890 }
});
```

---

### Server → Client Events

#### `qr`
QR code generated or updated.

```javascript
socket.on('qr', (qrCodeData) => {
  // qrCodeData: base64 image string
});
```

---

#### `ready`
WhatsApp session authenticated and ready.

```javascript
socket.on('ready', (data) => {
  // { phoneNumber: '1234567890', sessionId: 'abc123' }
});
```

---

#### `authenticated`
WhatsApp authentication successful.

```javascript
socket.on('authenticated', () => {
  console.log('Authenticated!');
});
```

---

#### `auth_failure`
Authentication failed.

```javascript
socket.on('auth_failure', (message) => {
  console.error('Auth failed:', message);
});
```

---

#### `disconnected`
WhatsApp client disconnected.

```javascript
socket.on('disconnected', (reason) => {
  console.log('Disconnected:', reason);
});
```

---

#### `loading_screen`
Loading progress updates.

```javascript
socket.on('loading_screen', (percent, message) => {
  console.log(`Loading: ${percent}%`);
});
```

---

#### `message`
New message received.

```javascript
socket.on('message', (message) => {
  // message object
});
```

---

#### `message_ack`
Message acknowledgment updated.

```javascript
socket.on('message_ack', (message, ack) => {
  // ack values: 0=error, 1=pending, 2=sent, 3=delivered, 4=read
});
```

---

## Session Management

### Session Lifecycle

1. **Creation**: `POST /create-session` generates session ID
2. **Initialization**: `POST /start-session` launches Puppeteer browser
3. **QR Generation**: WhatsApp Web displays QR, emitted via WebSocket
4. **Authentication**: User scans QR, session becomes "authenticated"
5. **Ready State**: Session is fully ready for messaging
6. **Active Use**: API calls keep session alive via `lastActivity`
7. **Cleanup**: Inactive sessions removed after timeout

### Session States

```javascript
{
  sessionId: "abc123",
  userId: "hashed-user-id",
  isAuthenticated: false,  // WhatsApp QR scanned
  isReady: false,          // WhatsApp fully loaded
  client: null,            // WhatsApp client instance
  qrCode: null,            // Current QR code (if any)
  phoneNumber: null,       // User's phone number
  lastActivity: timestamp, // Last API call
  deviceInfo: {}           // Device metadata
}
```

### Health Monitoring

Sessions are monitored every 5 minutes:

- **Health Check**: Verifies browser and WhatsApp state
- **Auto-Reconnection**: Reconnects if session becomes unhealthy
- **Cleanup**: Removes dead sessions

### Timeouts

| Timeout | Duration | Purpose |
|---------|----------|---------|
| Active Session | 24 hours | Remove inactive authenticated sessions |
| Unfinished Session | 15 minutes | Remove un-authenticated sessions |
| Audio Cache | 2 hours | Clean up converted audio files |
| Health Check | 5 minutes | Monitor session health |

---

## Media Processing

### Audio Conversion

WhatsBerry automatically converts audio formats incompatible with Android 4.3 to MP3.

**Supported Input Formats:**
- audio/ogg (Opus codec)
- audio/opus
- audio/webm
- audio/aac
- audio/m4a
- audio/wav
- audio/flac

**Output Format:**
- **Codec**: MP3
- **Bitrate**: 128kbps
- **Sample Rate**: 44.1kHz
- **Channels**: Stereo

### Conversion Process

1. App requests media with `?format=mp3`
2. Server checks cache for converted file
3. If not cached:
   - Downloads original from WhatsApp
   - Converts using FFmpeg
   - Caches converted file (2-hour TTL)
4. Serves converted MP3

### Caching Strategy

- **Key**: Message ID
- **TTL**: 2 hours
- **Storage**: Filesystem (`src/audio_cache/`)
- **Cleanup**: Automatic every 30 minutes

---

## Configuration

### Constants Configuration

`src/config/constants.js` for advanced settings:

```javascript
module.exports = {
  // Timeouts
  SESSION_TIMEOUT: 24 * 60 * 60 * 1000,        // 24 hours
  UNFINISHED_SESSION_TIMEOUT: 15 * 60 * 1000,  // 15 minutes

  // Audio settings
  AUDIO_CONVERSION_TTL: 2 * 60 * 60 * 1000,    // 2 hours
  AUDIO_BITRATE: 128,
  AUDIO_FREQUENCY: 44100,
  AUDIO_CHANNELS: 2,

  // Cleanup intervals
  SESSION_CLEANUP_INTERVAL: 60 * 60 * 1000,    // 1 hour
  HEALTH_CHECK_INTERVAL: 5 * 60 * 1000,        // 5 minutes
};
```

### Environment Variables

Required environment variables in `.env`:

```env
# Server Port
PORT=3000

# Security - API Key Authentication
API_KEY=your_secure_api_key_here

# Optional Settings
NODE_ENV=production
```

**Generate a secure API key:**
```bash
node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
```

---

## Error Handling

### HTTP Status Codes

| Code | Meaning | Common Causes |
|------|---------|---------------|
| 200 | Success | Request completed successfully |
| 400 | Bad Request | Missing parameters, invalid input |
| 401 | Unauthorized | Invalid or missing API key |
| 404 | Not Found | Session or resource not found |
| 410 | Gone | Session replaced by newer one |
| 500 | Server Error | Internal error, check logs |
| 503 | Service Unavailable | Session reconnecting, try again |

### Common Error Responses

#### Session Not Ready
```json
{
  "error": "WhatsApp client not ready"
}
```

**Solution**: Wait for `ready` WebSocket event before making requests.

---

#### Session Disconnected
```json
{
  "error": "Session disconnected, reconnection in progress. Please try again in a moment.",
  "reconnecting": true
}
```

**Solution**: Retry request after a few seconds. Server is auto-reconnecting.

---

### Reconnection Strategy

When a session becomes unhealthy:

1. Server detects issue (error or health check failure)
2. Sets session state to "reconnecting"
3. Returns `503 Service Unavailable` with `reconnecting: true`
4. Attempts to reinitialize WhatsApp client
5. Once reconnected, API calls resume normally

**App should:**
- Detect `503` + `reconnecting: true` response
- Show "Reconnecting..." UI
- Retry request after 3-5 seconds
- Listen for `ready` WebSocket event

---

## Performance Optimization

### Session Management
- Sessions use isolated Puppeteer instances
- Automatic cleanup prevents memory leaks
- Health checks prevent zombie sessions

### Media Handling
- Converted audio cached for 2 hours
- Profile pics cached during fetch
- Aggressive timeouts prevent hanging requests

### API Response Times
- Parallel processing for contacts/chats
- Pagination support for large lists
- Timeout limits on all WhatsApp operations

---

## Debugging

### Debug Endpoints

Use `/debug/sessions` and `/debug/session-details` to inspect:
- Session states
- Phone numbers
- Last activity times
- Authentication status

### Logging

All errors are logged to console with timestamps:
```
[CREATE-SESSION] Request received at 2025-01-15T10:30:00.000Z
[START-SESSION] Session: abc123 - Timestamp: 1234567890
[WebSocket] Client connected: xyz789
```

---

## Contributing

This repository is open for transparency. To contribute:

1. Review this technical documentation
2. Fork the repository
3. Create a feature branch
4. Make your changes
5. Submit a pull request with detailed description

---

## License

**WhatsBerry** is released under the Apache-2.0 License with Commons Clause, which allows code inspection and contribution while preventing commercial use. See the [LICENSE.md](LICENSE.md) for full details.

---

**Last Updated**: October 2025
**Version**: 0.10.3-beta
