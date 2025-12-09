# 🌐 Servidor HTTP - WhatsBerry

## 📋 Resumen

WhatsBerry utiliza **Nginx** como servidor HTTP para servir:
- El APK para descargar en dispositivos BlackBerry 10
- Archivos adjuntos de WhatsApp (imágenes, videos, documentos)
- Endpoints PHP para subir archivos y convertir audio

---

## 🏗️ Arquitectura de Servidores Nginx

WhatsBerry tiene **4 configuraciones de Nginx** diferentes:

### 1. **Servidor Principal** (`whatsberry` - Puerto 80 y 443)
**Archivos**: `/etc/nginx/sites-available/whatsberry`
**Dominio**: `whatsberry.descarga.media`
**Propósito**: Servidor público con SSL para acceso desde Internet

**Endpoints**:

| Ruta | Propósito | Método HTTP | Tamaño Máx |
|------|-----------|-------------|------------|
| `/attachments` | Archivos adjuntos de WhatsApp (imágenes, videos, audio) | GET, PUT, DELETE, OPTIONS | 100MB |
| `/downloads/` | Descargas de APKs | GET | - |
| `/upload.php` | Subir archivos desde la app | POST | 100MB |
| `/convert_audio.php` | Convertir audio a formato WhatsApp | POST | 100MB |
| `/` | Página principal | GET | - |

**Características SSL**:
```nginx
ssl_certificate /etc/letsencrypt/live/whatsberry.descarga.media/fullchain.pem;
ssl_certificate_key /etc/letsencrypt/live/whatsberry.descarga.media/privkey.pem;
```

**WebDAV habilitado para `/attachments`**:
- Permite subir, borrar y mover archivos
- CORS habilitado para apps móviles
- Cache de 7 días para archivos GET

---

### 2. **Servidor Local** (`whatsberry.descarga.media.conf` - Puerto 9003)
**Archivos**: `/etc/nginx/sites-available/whatsberry.descarga.media.conf`
**Propósito**: Servidor local para desarrollo/pruebas

**Endpoints**:
- `/attachments/` - Archivos adjuntos de WhatsApp
- `/upload.php` - Subir archivos
- `/convert_audio.php` - Convertir audio
- `/*.apk` - Servir APKs directamente desde `/opt/whatsberry/public`
- `/login` - Página de instrucciones QR
- `/` - Archivos estáticos HTML

**Características**:
- No requiere SSL
- Accesible solo desde `localhost:9003`
- Útil para desarrollo

---

### 3. **Servidor de Attachments** (`whatsberry-attachments` - Puerto 8765)
**Archivos**: `/etc/nginx/sites-available/whatsberry-attachments`
**Propósito**: Servidor dedicado solo para archivos adjuntos

**Endpoints**:
- `/attachments/` - Solo lectura de archivos adjuntos
- `/` - Test endpoint

**Características**:
- Solo GET y OPTIONS
- CORS habilitado
- Cache de 7 días
- Accesible desde red local

---

### 4. **Servidor HTTP Simple** (`whatsberry_http.conf` - Puerto 8888)
**Archivos**: `/etc/nginx/sites-available/whatsberry_http.conf`
**Propósito**: Servidor HTTP básico sin dominio

**Endpoints**:
- `/upload.php` - Subir archivos
- `/convert_audio.php` - Convertir audio
- `/attachments/` - Archivos adjuntos (solo lectura)

---

## 📁 Estructura de Directorios

```
/opt/whatsberry/
├── public/
│   └── whatsberry-v3.3.1.apk       # APK publicado (56 MB)
│
/home/batman/.local/share/slidge/
└── attachments/                     # Archivos WhatsApp
    ├── images/
    ├── videos/
    ├── audio/
    └── documents/
```

---

## 🔧 Configuración Paso a Paso

### 1. Instalar Nginx y PHP-FPM

```bash
# Arch Linux
sudo pacman -S nginx php-fpm

# Ubuntu/Debian
sudo apt install nginx php-fpm

# Verificar instalación
nginx -v
php-fpm -v
```

---

### 2. Configurar PHP-FPM

Editar `/etc/php/php-fpm.d/www.conf`:

```ini
[www]
user = batman
group = batman
listen = /run/php-fpm/php-fpm.sock
listen.owner = batman
listen.group = http
listen.mode = 0660

# Aumentar límites para archivos grandes
php_admin_value[upload_max_filesize] = 100M
php_admin_value[post_max_size] = 100M
php_admin_value[max_execution_time] = 300
```

Iniciar PHP-FPM:

```bash
sudo systemctl start php-fpm
sudo systemctl enable php-fpm
```

---

### 3. Configurar Nginx Principal (Puerto 80/443)

Crear `/etc/nginx/sites-available/whatsberry`:

```nginx
server {
    listen 80;
    server_name whatsberry.descarga.media;

    # WhatsApp attachments con WebDAV
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

    # Descargas de APK
    location /downloads/ {
        alias /opt/whatsberry/public/downloads/;
        autoindex on;
        add_header Content-Disposition "attachment";
    }

    # Endpoint para subir archivos
    location /upload.php {
        root /opt/whatsberry/public;
        fastcgi_pass unix:/run/php-fpm/php-fpm.sock;
        include fastcgi_params;
        fastcgi_param SCRIPT_FILENAME $document_root$fastcgi_script_name;
        client_max_body_size 100M;
    }

    # Endpoint para convertir audio
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

# Versión HTTPS con SSL
server {
    listen 443 ssl;
    server_name whatsberry.descarga.media;

    ssl_certificate /etc/letsencrypt/live/whatsberry.descarga.media/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/whatsberry.descarga.media/privkey.pem;

    # ... mismas rutas que arriba ...
}
```

---

### 4. Habilitar Sitio

```bash
# Crear symlink
sudo ln -s /etc/nginx/sites-available/whatsberry /etc/nginx/sites-enabled/

# Verificar configuración
sudo nginx -t

# Reiniciar Nginx
sudo systemctl restart nginx
sudo systemctl enable nginx
```

---

### 5. Configurar Certificado SSL (Let's Encrypt)

```bash
# Instalar certbot
sudo pacman -S certbot certbot-nginx  # Arch
sudo apt install certbot python3-certbot-nginx  # Ubuntu

# Obtener certificado
sudo certbot --nginx -d whatsberry.descarga.media

# Auto-renovación
sudo systemctl enable certbot-renew.timer
```

---

### 6. Configurar Firewall

```bash
# Abrir puertos HTTP/HTTPS
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw reload

# Verificar
sudo ufw status | grep -E '80|443'
```

---

## 📂 Crear Estructura de Directorios

```bash
# Crear directorio público
sudo mkdir -p /opt/whatsberry/public/downloads
sudo chown -R batman:batman /opt/whatsberry/public

# Crear directorio de attachments
mkdir -p /home/batman/.local/share/slidge/attachments
chmod 755 /home/batman/.local/share/slidge/attachments

# Copiar APK
cp /opt/whatsberry/app/build/outputs/apk/debug/app-debug.apk \
   /opt/whatsberry/public/whatsberry-v3.3.1.apk
```

---

## 🔍 Verificar Configuración

### 1. Verificar que Nginx está corriendo

```bash
sudo systemctl status nginx
```

**Salida esperada**:
```
● nginx.service - nginx web server
     Loaded: loaded
     Active: active (running)
```

### 2. Verificar que PHP-FPM está corriendo

```bash
sudo systemctl status php-fpm
```

### 3. Probar endpoints

```bash
# Test servidor principal
curl http://whatsberry.descarga.media/
# Salida: "Whatsberry Server"

# Test descarga de APK (si existe)
curl -I http://whatsberry.descarga.media/whatsberry-v3.3.1.apk

# Test attachments (debe devolver 404 si no hay archivos)
curl http://whatsberry.descarga.media/attachments/
```

### 4. Verificar logs

```bash
# Logs de acceso
sudo tail -f /var/log/nginx/access.log

# Logs de error
sudo tail -f /var/log/nginx/error.log

# Logs específicos de Whatsberry
sudo tail -f /var/log/nginx/whatsberry_access.log
sudo tail -f /var/log/nginx/whatsberry_error.log
```

---

## 📱 Uso desde la App BlackBerry 10

### Descargar APK

1. Abrir navegador en BB10
2. Ir a: `https://whatsberry.descarga.media/whatsberry-v3.3.1.apk`
3. Descargar e instalar

### Descargar Attachments

La app automáticamente descarga archivos adjuntos desde:
```
https://whatsberry.descarga.media/attachments/[hash]/[filename]
```

Por ejemplo:
```
https://whatsberry.descarga.media/attachments/abc123/photo.jpg
```

---

## 🛠️ Endpoints PHP (Opcional)

Si necesitas endpoints para subir archivos o convertir audio, crea estos archivos:

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
        // Convertir a OGG Opus (formato WhatsApp)
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

**Nota**: Estos endpoints requieren `ffmpeg` instalado:
```bash
sudo pacman -S ffmpeg  # Arch
sudo apt install ffmpeg  # Ubuntu
```

---

## 🔒 Seguridad

### 1. Permisos de Archivos

```bash
# Nginx debe poder leer archivos
sudo chown -R batman:http /opt/whatsberry/public
sudo chmod -R 755 /opt/whatsberry/public

# Attachments debe ser escribible
sudo chown -R batman:http /home/batman/.local/share/slidge/attachments
sudo chmod -R 775 /home/batman/.local/share/slidge/attachments
```

### 2. Rate Limiting (Opcional)

Agregar a la configuración de Nginx:

```nginx
http {
    limit_req_zone $binary_remote_addr zone=upload:10m rate=10r/m;

    server {
        location /upload.php {
            limit_req zone=upload burst=5;
            # ... resto de configuración ...
        }
    }
}
```

### 3. Autenticación Básica (Opcional)

Para proteger `/attachments`:

```bash
# Crear archivo de contraseñas
sudo htpasswd -c /etc/nginx/.htpasswd whatsberry_user
```

Agregar a la configuración:

```nginx
location /attachments {
    auth_basic "Restricted";
    auth_basic_user_file /etc/nginx/.htpasswd;
    # ... resto de configuración ...
}
```

---

## 📊 Monitoreo

### Ver conexiones activas

```bash
# Conexiones Nginx
sudo ss -tlnp | grep nginx

# Ver quién está descargando
sudo tail -f /var/log/nginx/access.log | grep -E 'apk|attachments'
```

### Estadísticas de uso

```bash
# Total de descargas de APK
grep 'whatsberry.*apk' /var/log/nginx/access.log | wc -l

# Total de archivos adjuntos descargados
grep 'attachments' /var/log/nginx/access.log | wc -l
```

---

## ⚠️ Troubleshooting

### Error: "502 Bad Gateway" en endpoints PHP

**Causa**: PHP-FPM no está corriendo o el socket no existe

**Solución**:
```bash
# Verificar PHP-FPM
sudo systemctl status php-fpm

# Verificar socket
ls -la /run/php-fpm/php-fpm.sock

# Reiniciar PHP-FPM
sudo systemctl restart php-fpm
```

### Error: "404 Not Found" para APK

**Causa**: El archivo no existe en `/opt/whatsberry/public/`

**Solución**:
```bash
# Copiar APK
cp /opt/whatsberry/app/build/outputs/apk/debug/app-debug.apk \
   /opt/whatsberry/public/whatsberry-v3.3.1.apk

# Verificar permisos
sudo chown batman:http /opt/whatsberry/public/whatsberry-v3.3.1.apk
sudo chmod 644 /opt/whatsberry/public/whatsberry-v3.3.1.apk
```

### Error: "413 Request Entity Too Large"

**Causa**: Archivo demasiado grande

**Solución**: Aumentar `client_max_body_size` en la configuración de Nginx:
```nginx
http {
    client_max_body_size 200M;  # Aumentar límite
}
```

---

## 📚 Referencias

- [Nginx Documentation](https://nginx.org/en/docs/)
- [PHP-FPM Configuration](https://www.php.net/manual/en/install.fpm.php)
- [Let's Encrypt](https://letsencrypt.org/)
- [WebDAV Module](https://nginx.org/en/docs/http/ngx_http_dav_module.html)

---

## 🎯 Resumen de Puertos

| Puerto | Protocolo | Propósito | Público |
|--------|-----------|-----------|---------|
| 80 | HTTP | Servidor principal | ✅ Sí |
| 443 | HTTPS | Servidor principal (SSL) | ✅ Sí |
| 8765 | HTTP | Solo attachments | ❌ Red local |
| 8888 | HTTP | Servidor simple | ❌ Red local |
| 9003 | HTTP | Desarrollo/pruebas | ❌ Localhost |

---

**¡Servidor HTTP configurado y listo para servir archivos a BlackBerry 10!** 🚀
