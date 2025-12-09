-- Prosody XMPP Server Configuration for WhatsBerry
-- Optimized for BlackBerry 10 compatibility

-- ========================================
-- Global Settings
-- ========================================

admins = { "admin@whatsberry.descarga.media" }  -- Change to your domain

-- Modules to load
modules_enabled = {
    -- Generally required
    "roster";        -- Allow users to have a roster (contact list)
    "saslauth";      -- Authentication for clients
    "tls";           -- Add support for secure TLS
    "dialback";      -- s2s dialback support
    "disco";         -- Service discovery

    -- Nice to have
    "carbons";       -- Message carbons (XEP-0280)
    "pep";           -- Personal Eventing Protocol (XEP-0163)
    "private";       -- Private XML storage (XEP-0049)
    "blocklist";     -- Allow users to block communications
    "vcard4";        -- User profiles (XEP-0292)
    "vcard_legacy";  -- Legacy vCard support
    "version";       -- Replies to server version requests
    "uptime";        -- Report how long server has been running
    "time";          -- Let others know the time here
    "ping";          -- Replies to XMPP pings
    "register";      -- Allow users to register on this server
    "admin_adhoc";   -- Allows administration via XMPP client
    "cloud_notify";  -- Push notifications (XEP-0357)
    "http_file_share"; -- File sharing (XEP-0363)
}

-- Modules to disable
modules_disabled = {
    "offline";  -- Store offline messages (disabled for performance)
    "c2s";      -- Client-to-server (will be enabled per virtualhost)
    "s2s";      -- Server-to-server (we don't need federation)
}

-- ========================================
-- Registration Settings
-- ========================================

allow_registration = true
min_seconds_between_registrations = 0  -- No rate limiting for testing

-- ========================================
-- TLS/Encryption Settings
-- ========================================

-- IMPORTANT: We disable TLS requirements because the STARTTLS proxy handles it
c2s_require_encryption = false
s2s_require_encryption = false

-- ========================================
-- Network Settings
-- ========================================

-- Interfaces and ports
interfaces = { "0.0.0.0" }
c2s_ports = { 5200 }  -- Internal port (no TLS, proxy handles STARTTLS)
s2s_ports = { }       -- Disable server-to-server

-- ========================================
-- Storage Settings
-- ========================================

storage = "internal"  -- Use internal storage (SQLite-like)

-- ========================================
-- Logging Settings
-- ========================================

log = {
    info = "/var/log/prosody/prosody.log";
    error = "/var/log/prosody/prosody.err";
    "*syslog"; -- Also log to syslog
}

-- ========================================
-- Virtual Host Configuration
-- ========================================

VirtualHost "whatsberry.descarga.media"  -- Change to your domain
    -- Disable TLS module (proxy handles STARTTLS)
    modules_disabled = { "tls" }

    enabled = true
    allow_registration = true
    min_seconds_between_registrations = 0

    -- Enable cloud notifications for push
    modules_enabled = {
        "cloud_notify";
    }

    -- Grant roster privileges to WhatsApp gateway
    -- This allows the gateway to manage user rosters
    privileged_entities = {
        ["whatsapp.localhost"] = {
            roster = "both";
            message = "outgoing";
            presence = "roster";
        };
    }

-- ========================================
-- Component: Slidge WhatsApp Gateway
-- ========================================

Component "whatsapp.localhost"
    component_secret = "your-secret-change-this"  -- CHANGE THIS! Must match docker-compose.yml

    modules_enabled = {
        "http_file_share";
    }

-- ========================================
-- Component: HTTP File Upload
-- ========================================

Component "upload.localhost" "http_file_share"
    http_file_share_access = { "whatsapp.localhost" }
    http_file_share_size_limit = 10485760  -- 10 MB
    http_file_share_expire_after = 86400   -- 24 hours (1 day)

-- ========================================
-- Additional Settings
-- ========================================

-- HTTP settings (for file upload)
http_ports = { 5280 }
http_interfaces = { "0.0.0.0" }

-- Archive settings (disable for performance)
archive_expires_after = "1w"  -- Keep messages for 1 week

-- Limits
limits = {
    c2s = {
        rate = "10kb/s";
    };
}
