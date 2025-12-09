# Changelog

All notable changes to WhatsBerry will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [3.3.1] - 2024-12-08

### Added
- **Database Settings** option in main menu (Menu → Database Settings)
- Accessibility fix: Database settings now available from main app menu

### Changed
- Menu now includes: Refresh, Database Settings, Logout

---

## [3.3.0] - 2024-12-08

### Added
- **Configurable database location** with 4 options:
  - Internal Storage (secure, deleted on uninstall)
  - External Storage (/sdcard/Whatsberry/)
  - BB10 External SD (/mnt/sdcard/external_sd/Whatsberry/)
  - Custom Path (user-specified)
- **Auto-detection of BlackBerry 10** devices
- **Database migration tool** to move existing database between locations
- Database information dialog showing current location and BB10 status

### Changed
- Database path now configurable via SharedPreferences
- Improved UI with database settings dialog

---

## [3.2.0] - 2024-12-08

### Added
- **User-configurable XMPP credentials**
- Server configuration UI (server, port, domain, gateway)
- Credentials persistence in SharedPreferences
- Auto-login with saved credentials

### Removed
- Hardcoded server credentials from build.gradle
- BuildConfig-based credential storage

### Changed
- MainActivity now shows all server configuration fields
- XMPPService reads credentials from SharedPreferences
- Version bumped to 3.2.0 for self-hosting support

---

## [3.1.9] - 2024-12-08

### Added
- **Foreground Service for XMPP connection** (CRITICAL FIX)
- Persistent notification to prevent BB10 from killing the app
- Auto-login service starts on app launch

### Fixed
- **BB10 notifications now working!** (was: notifications not appearing in BlackBerry Hub)
- Process staying alive in background
- Message delivery reliability

### Changed
- XMPPService now runs as foreground service with startForeground()
- Added persistent "WhatsBerry - Conectado" notification

---

## [3.1.8] - 2024-12-07

### Changed
- Notification defaults changed to `Notification.DEFAULT_ALL`
- Improved notification compatibility with BB10 Hub

---

## [3.1.7] - 2024-12-07

### Changed
- UI redesign: Removed TabWidget, added button-based navigation
- MainTabsActivity: Changed from TabActivity to Activity
- Chats/Contacts now in single header bar
- Theme changed to NoTitleBar for ChatActivity

---

## [3.1.6] - 2024-12-07

### Added
- Database schema v4 with `is_read` column
- `markMessagesAsRead()` function to mark messages as read when chat opens
- Proper unread message counting

### Fixed
- Unread counter now shows only truly unread messages (not all messages)
- Menu button now visible (changed from "⋮" to "Menu")
- Gray "WhatsXMPP" title bar removed

### Changed
- UI consolidated into single green header bar
- Notification system enhanced with DEFAULT_ALL and ticker text
- Database migration from v3 to v4

---

## [3.1.5] - 2024-12-07

### Added
- Overflow menu with message retraction option
- Unread message counter per chat
- BB10 notification enhancements

### Fixed
- Improved notification delivery

---

## [3.0.0 - 3.1.4] - 2024-12-05 to 2024-12-07

### Added
- Complete XMPP-based WhatsApp client
- Slidge-WhatsApp gateway integration
- Message retraction support (XEP-0424)
- File/media message support
- Contact list with online indicators
- Chat list with last message preview
- Database-backed message storage
- Emoji support with Twemoji assets

---

## [2.x.x] - Previous Architecture

### Deprecated
- Web-based approach with Puppeteer
- WhatsApp Web automation
- Complex Node.js backend

**Reason for deprecation**: Web-based approach was detectable and unstable. 
New XMPP architecture is more reliable and undetectable.

---

## Server Components

### XMPP STARTTLS Proxy
- TLS 1.0 support for BlackBerry 10 compatibility
- STARTTLS negotiation on port 5222
- Forwards decrypted traffic to Prosody on port 5200

### Prosody XMPP Server
- Component configuration for Slidge
- WhatsApp gateway integration
- User authentication and roster management

### Slidge WhatsApp Gateway
- Native WhatsApp protocol support
- QR code authentication
- Message bridging between XMPP and WhatsApp

---

## Links

- [Server Setup Guide](SERVER_SETUP-en.md)
- [HTTP Server Setup](HTTP_SERVER-en.md)
- [Troubleshooting](TROUBLESHOOTING-en.md)
- [Client Configuration](CLIENT_CONFIGURATION-en.md)
- [Architecture Diagrams](ARCHITECTURE_DIAGRAMS-en.md)
- [Technical Documentation](../TECHNICAL.md) (in root directory)
- [APK Build Guide](../APK_BUILD_SIGNING_GUIDE.md) (in root directory)
