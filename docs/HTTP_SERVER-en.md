# 🌐 HTTP Server - WhatsBerry

## 📋 Overview

WhatsBerry uses **Nginx** as HTTP server to serve:
- APK file for download on BlackBerry 10 devices
- WhatsApp attachments (images, videos, documents)
- PHP endpoints for file uploads and audio conversion

---

## 🏗️ Nginx Server Architecture

WhatsBerry has **4 different Nginx configurations**:

### 1. **Main Server** (`whatsberry` - Port 80 and 443)
**Files**: `/etc/nginx/sites-available/whatsberry`
**Domain**: `whatsberry.descarga.media`
**Purpose**: Public server with SSL for Internet access

**Endpoints**:

| Path | Purpose | HTTP Method | Max Size |
|------|---------|-------------|----------|
| `/attachments` | WhatsApp attachments (images, videos, audio) | GET, PUT, DELETE, OPTIONS | 100MB |
| `/downloads/` | APK downloads | GET | - |
| `/upload.php` | Upload files from the app | POST | 100MB |
| `/convert_audio.php` | Convert audio to WhatsApp format | POST | 100MB |
| `/` | Main page | GET | - |

**SSL Features**:
```nginx
ssl_certificate /etc/letsencrypt/live/whatsberry.descarga.media/fullchain.pem;
ssl_certificate_key /etc/letsencrypt/live/whatsberry.descarga.media/privkey.pem;
```

**WebDAV enabled for `/attachments`**:
- Allows uploading, deleting and moving files
- CORS enabled for mobile apps
- 7-day cache for GET files

---

### 2. **Local Server** (`whatsberry.descarga.media.conf` - Port 9003)
**Files**: `/etc/nginx/sites-available/whatsberry.descarga.media.conf`
**Purpose**: Local server for development/testing

**Endpoints**:
- `/attachments/` - WhatsApp attachments
- `/upload.php` - Upload files
- `/convert_audio.php` - Convert audio
- `/*.apk` - Serve APKs directly from `/opt/whatsberry/public`
- `/login` - QR instructions page
- `/` - Static HTML files

**Features**:
- No SSL required
- Accessible only from `localhost:9003`
- Useful for development

---

### 3. **Attachments Server** (`whatsberry-attachments` - Port 8765)
**Files**: `/etc/nginx/sites-available/whatsberry-attachments`
**Purpose**: Dedicated server only for attachments

**Endpoints**:
- `/attachments/` - Read-only attachments
- `/` - Test endpoint

**Features**:
- Only GET and OPTIONS
- CORS enabled
- 7-day cache
- Accessible from local network

---

### 4. **Simple HTTP Server** (`whatsberry_http.conf` - Port 8888)
**Files**: `/etc/nginx/sites-available/whatsberry_http.conf`
**Purpose**: Basic HTTP server without domain

**Endpoints**:
- `/upload.php` - Upload files
- `/convert_audio.php` - Convert audio
- `/attachments/` - Attachments (read-only)

---

## 📁 Directory Structure

```
/opt/whatsberry/
├── public/
│   └── whatsberry-v3.3.1.apk       # Published APK (56 MB)
│
/home/batman/.local/share/slidge/
└── attachments/                     # WhatsApp files
    ├── images/
    ├── videos/
    ├── audio/
    └── documents/
```

---

## 🔧 Step-by-Step Configuration

### 1. Install Nginx and PHP-FPM

```bash
# Arch Linux
sudo pacman -S nginx php-fpm

# Ubuntu/Debian
sudo apt install nginx php-fpm

# Verify installation
nginx -v
php-fpm -v
```

---

### 2. Configure PHP-FPM

Edit `/etc/php/php-fpm.d/www.conf`:

```ini
[www]
user = batman
group = batman
listen = /run/php-fpm/php-fpm.sock
listen.owner = batman
listen.group = http
listen.mode = 0660

# Increase limits for large files
php_admin_value[upload_max_filesize] = 100M
php_admin_value[post_max_size] = 100M
php_admin_value[max_execution_time] = 300
```

Start PHP-FPM:

```bash
sudo systemctl start php-fpm
sudo systemctl enable php-fpm
```

---

### 3. Configure Main Nginx Server (Port 80/443)

Create `/etc/nginx/sites-available/whatsberry`:

```nginx
server {
    listen 80;
    server_name whatsberry.descarga.media;

    # WhatsApp attachments with WebDAV
    location /attachments {
        root /home/batman/.local/share/slidge;
        autoindex off;

        client_max_body_size 100M;

        # Enable WebDAV
        dav_methods PUT DELETE MKCOL COPY MOVE;
        create_full_put_path on;
        dav_access user:rw group:rw all:r;

        # CORS
        add_header Access-Control-Allow-Origin * always;
        add_header Access-Control-Allow-Methods "GET, PUT, DELETE, OPTIONS" always;
        add_header Access-Control-Allow-Headers "Content-Type" always;

        if ($request_method = OPTIONS) {
            return 204;
        }
    }

    # APK downloads
    location /downloads/ {
        alias /opt/whatsberry/public/downloads/;
        autoindex on;
        add_header Content-Disposition "attachment";
    }

    # File upload endpoint
    location /upload.php {
        root /opt/whatsberry/public;
        fastcgi_pass unix:/run/php-fpm/php-fpm.sock;
        include fastcgi_params;
        fastcgi_param SCRIPT_FILENAME $document_root$fastcgi_script_name;
        client_max_body_size 100M;
    }

    # Audio conversion endpoint
    location /convert_audio.php {
        root /opt/whatsberry/public;
        fastcgi_pass unix:/run/php-fpm/php-fpm.sock;
        include fastcgi_params;
        fastcgi_param SCRIPT_FILENAME $document_root$fastcgi_script_name;
        client_max_body_size 100M;
        fastcgi_read_timeout 300;
    }

    location / {
        return 200 "Whatsberry Server\n";
        add_header Content-Type text/plain;
    }
}

# HTTPS version with SSL
server {
    listen 443 ssl;
    server_name whatsberry.descarga.media;

    ssl_certificate /etc/letsencrypt/live/whatsberry.descarga.media/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/whatsberry.descarga.media/privkey.pem;

    # ... same routes as above ...
}
```

---

### 4. Enable Site

```bash
# Create symlink
sudo ln -s /etc/nginx/sites-available/whatsberry /etc/nginx/sites-enabled/

# Verify configuration
sudo nginx -t

# Restart Nginx
sudo systemctl restart nginx
sudo systemctl enable nginx
```

---

### 5. Configure SSL Certificate (Let's Encrypt)

```bash
# Install certbot
sudo pacman -S certbot certbot-nginx  # Arch
sudo apt install certbot python3-certbot-nginx  # Ubuntu

# Obtain certificate
sudo certbot --nginx -d whatsberry.descarga.media

# Auto-renewal
sudo systemctl enable certbot-renew.timer
```

---

### 6. Configure Firewall

```bash
# Open HTTP/HTTPS ports
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw reload

# Verify
sudo ufw status | grep -E '80|443'
```

---

## 📂 Create Directory Structure

```bash
# Create public directory
sudo mkdir -p /opt/whatsberry/public/downloads
sudo chown -R batman:batman /opt/whatsberry/public

# Create attachments directory
mkdir -p /home/batman/.local/share/slidge/attachments
chmod 755 /home/batman/.local/share/slidge/attachments

# Copy APK
cp /opt/whatsberry/app/build/outputs/apk/debug/app-debug.apk \
   /opt/whatsberry/public/whatsberry-v3.3.1.apk
```

---

## 🔍 Verify Configuration

### 1. Verify Nginx is running

```bash
sudo systemctl status nginx
```

**Expected output**:
```
● nginx.service - nginx web server
     Loaded: loaded
     Active: active (running)
```

### 2. Verify PHP-FPM is running

```bash
sudo systemctl status php-fpm
```

### 3. Test endpoints

```bash
# Test main server
curl http://whatsberry.descarga.media/
# Output: "Whatsberry Server"

# Test APK download (if it exists)
curl -I http://whatsberry.descarga.media/whatsberry-v3.3.1.apk

# Test attachments (should return 404 if no files)
curl http://whatsberry.descarga.media/attachments/
```

### 4. Check logs

```bash
# Access logs
sudo tail -f /var/log/nginx/access.log

# Error logs
sudo tail -f /var/log/nginx/error.log

# Whatsberry-specific logs
sudo tail -f /var/log/nginx/whatsberry_access.log
sudo tail -f /var/log/nginx/whatsberry_error.log
```

---

## 📱 Usage from BlackBerry 10 App

### Download APK

1. Open browser on BB10
2. Go to: `https://whatsberry.descarga.media/whatsberry-v3.3.1.apk`
3. Download and install

### Download Attachments

The app automatically downloads attachments from:
```
https://whatsberry.descarga.media/attachments/[hash]/[filename]
```

For example:
```
https://whatsberry.descarga.media/attachments/abc123/photo.jpg
```

---

## 🛠️ PHP Endpoints (Optional)

If you need endpoints for file uploads or audio conversion, create these files:

### `/opt/whatsberry/public/upload.php`

```php
<?php
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST, OPTIONS');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(204);
    exit;
}

if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_FILES['file'])) {
    $upload_dir = '/home/batman/.local/share/slidge/attachments/';
    $filename = basename($_FILES['file']['name']);
    $target_path = $upload_dir . $filename;

    if (move_uploaded_file($_FILES['file']['tmp_name'], $target_path)) {
        echo json_encode(['success' => true, 'url' => "/attachments/$filename"]);
    } else {
        http_response_code(500);
        echo json_encode(['success' => false, 'error' => 'Upload failed']);
    }
} else {
    http_response_code(400);
    echo json_encode(['success' => false, 'error' => 'No file uploaded']);
}
?>
```

### `/opt/whatsberry/public/convert_audio.php`

```php
<?php
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST, OPTIONS');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(204);
    exit;
}

if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_FILES['audio'])) {
    $upload_dir = '/tmp/';
    $input_file = $upload_dir . basename($_FILES['audio']['name']);
    $output_file = $upload_dir . pathinfo($input_file, PATHINFO_FILENAME) . '.ogg';

    if (move_uploaded_file($_FILES['audio']['tmp_name'], $input_file)) {
        // Convert to OGG Opus (WhatsApp format)
        $cmd = "ffmpeg -i " . escapeshellarg($input_file) .
               " -c:a libopus -b:a 16k " . escapeshellarg($output_file);

        exec($cmd, $output, $return_var);

        if ($return_var === 0 && file_exists($output_file)) {
            $final_path = '/home/batman/.local/share/slidge/attachments/' .
                         basename($output_file);
            rename($output_file, $final_path);

            echo json_encode([
                'success' => true,
                'url' => "/attachments/" . basename($output_file)
            ]);
        } else {
            http_response_code(500);
            echo json_encode(['success' => false, 'error' => 'Conversion failed']);
        }

        unlink($input_file);
    }
}
?>
```

**Note**: These endpoints require `ffmpeg` installed:
```bash
sudo pacman -S ffmpeg  # Arch
sudo apt install ffmpeg  # Ubuntu
```

---

## 🔒 Security

### 1. File Permissions

```bash
# Nginx must be able to read files
sudo chown -R batman:http /opt/whatsberry/public
sudo chmod -R 755 /opt/whatsberry/public

# Attachments must be writable
sudo chown -R batman:http /home/batman/.local/share/slidge/attachments
sudo chmod -R 775 /home/batman/.local/share/slidge/attachments
```

### 2. Rate Limiting (Optional)

Add to Nginx configuration:

```nginx
http {
    limit_req_zone $binary_remote_addr zone=upload:10m rate=10r/m;

    server {
        location /upload.php {
            limit_req zone=upload burst=5;
            # ... rest of configuration ...
        }
    }
}
```

### 3. Basic Authentication (Optional)

To protect `/attachments`:

```bash
# Create password file
sudo htpasswd -c /etc/nginx/.htpasswd whatsberry_user
```

Add to configuration:

```nginx
location /attachments {
    auth_basic "Restricted";
    auth_basic_user_file /etc/nginx/.htpasswd;
    # ... rest of configuration ...
}
```

---

## 📊 Monitoring

### View active connections

```bash
# Nginx connections
sudo ss -tlnp | grep nginx

# See who's downloading
sudo tail -f /var/log/nginx/access.log | grep -E 'apk|attachments'
```

### Usage statistics

```bash
# Total APK downloads
grep 'whatsberry.*apk' /var/log/nginx/access.log | wc -l

# Total attachments downloaded
grep 'attachments' /var/log/nginx/access.log | wc -l
```

---

## ⚠️ Troubleshooting

### Error: "502 Bad Gateway" on PHP endpoints

**Cause**: PHP-FPM is not running or socket doesn't exist

**Solution**:
```bash
# Check PHP-FPM
sudo systemctl status php-fpm

# Check socket
ls -la /run/php-fpm/php-fpm.sock

# Restart PHP-FPM
sudo systemctl restart php-fpm
```

### Error: "404 Not Found" for APK

**Cause**: File doesn't exist in `/opt/whatsberry/public/`

**Solution**:
```bash
# Copy APK
cp /opt/whatsberry/app/build/outputs/apk/debug/app-debug.apk \
   /opt/whatsberry/public/whatsberry-v3.3.1.apk

# Check permissions
sudo chown batman:http /opt/whatsberry/public/whatsberry-v3.3.1.apk
sudo chmod 644 /opt/whatsberry/public/whatsberry-v3.3.1.apk
```

### Error: "413 Request Entity Too Large"

**Cause**: File is too large

**Solution**: Increase `client_max_body_size` in Nginx configuration:
```nginx
http {
    client_max_body_size 200M;  # Increase limit
}
```

---

## 📚 References

- [Nginx Documentation](https://nginx.org/en/docs/)
- [PHP-FPM Configuration](https://www.php.net/manual/en/install.fpm.php)
- [Let's Encrypt](https://letsencrypt.org/)
- [WebDAV Module](https://nginx.org/en/docs/http/ngx_http_dav_module.html)

---

## 🎯 Port Summary

| Port | Protocol | Purpose | Public |
|------|----------|---------|--------|
| 80 | HTTP | Main server | ✅ Yes |
| 443 | HTTPS | Main server (SSL) | ✅ Yes |
| 8765 | HTTP | Attachments only | ❌ Local network |
| 8888 | HTTP | Simple server | ❌ Local network |
| 9003 | HTTP | Development/testing | ❌ Localhost |

---

**HTTP Server configured and ready to serve files to BlackBerry 10!** 🚀
