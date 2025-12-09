# Guía de Publicación de APKs - WhatsBerry

**Última actualización**: 7 de diciembre de 2025

---

## 🎯 IMPORTANTE: Ubicaciones de Publicación

### ⚠️ ERROR COMÚN
**NO** publicar APKs en `/var/www/html/downloads/` - ese directorio NO está configurado en nginx.

### ✅ UBICACIONES CORRECTAS

#### 1. Directorio Principal de Descarga (HTTPS)
```
Ruta física: /opt/whatsberry/public/downloads/
URL pública: https://whatsberry.descarga.media/downloads/
```

**Archivos a publicar aquí**:
- `whatsberry-new.apk` - Versión más reciente (siempre sobrescribir)
- `whatsberry-v2.7.0.apk` - Versión numerada (mantener histórico)

#### 2. Directorio Secundario (HTTP puerto 9003)
```
Ruta física: /opt/whatsberry/public/
URL pública: http://whatsberry.descarga.media:9003/
```

**Archivos a publicar aquí**:
- `whatsberry-v2.7.0.apk` - Versión numerada principal

---

## 📝 Configuración de Nginx

### Archivo: `/etc/nginx/sites-available/whatsberry`

**Puerto 80 (HTTP)**:
```nginx
server {
    listen 80;
    server_name whatsberry.descarga.media;

    # Downloads directory for APKs
    location /downloads/ {
        alias /opt/whatsberry/public/downloads/;
        autoindex on;
        add_header Content-Disposition "attachment";
    }
}
```

**Puerto 443 (HTTPS)**:
```nginx
server {
    listen 443 ssl;
    server_name whatsberry.descarga.media;

    # SSL certificates
    ssl_certificate /etc/letsencrypt/live/whatsberry.descarga.media/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/whatsberry.descarga.media/privkey.pem;

    # Downloads directory for APKs (MISMO que HTTP)
    location /downloads/ {
        alias /opt/whatsberry/public/downloads/;
        autoindex on;
        add_header Content-Disposition "attachment";
    }
}
```

### Archivo: `/etc/nginx/sites-available/whatsberry.descarga.media.conf`

**Puerto 9003 (HTTP alternativo)**:
```nginx
server {
    listen 9003;
    server_name localhost;

    # Serve static files (APK downloads)
    location ~* \.(apk|zip|tar\.gz)$ {
        root /opt/whatsberry/public;
        try_files $uri =404;
        add_header Content-Disposition "attachment";
    }
}
```

---

## 🚀 Proceso de Publicación de Nueva APK

### Paso 1: Build de la APK
```bash
cd /opt/whatsberry
./gradlew assembleDebug --no-daemon --stacktrace
```

**Output**: `/opt/whatsberry/app/build/outputs/apk/debug/app-debug.apk`

### Paso 2: Copiar a Ubicaciones Correctas

```bash
#!/bin/bash
# Script: publish-apk.sh

VERSION="2.7.0"
APK_SOURCE="/opt/whatsberry/app/build/outputs/apk/debug/app-debug.apk"

echo "📦 Publicando APK v${VERSION}..."

# 1. Directorio downloads (HTTPS/HTTP)
echo "  ✓ Copiando a /opt/whatsberry/public/downloads/"
sudo cp "$APK_SOURCE" /opt/whatsberry/public/downloads/whatsberry-new.apk
sudo cp "$APK_SOURCE" /opt/whatsberry/public/downloads/whatsberry-v${VERSION}.apk

# 2. Directorio principal (HTTP puerto 9003)
echo "  ✓ Copiando a /opt/whatsberry/public/"
sudo cp "$APK_SOURCE" /opt/whatsberry/public/whatsberry-v${VERSION}.apk

# 3. Ajustar permisos
echo "  ✓ Ajustando permisos..."
sudo chown batman:batman /opt/whatsberry/public/downloads/whatsberry-new.apk
sudo chown batman:batman /opt/whatsberry/public/downloads/whatsberry-v${VERSION}.apk
sudo chown batman:batman /opt/whatsberry/public/whatsberry-v${VERSION}.apk
sudo chmod 644 /opt/whatsberry/public/downloads/*.apk
sudo chmod 644 /opt/whatsberry/public/whatsberry-v${VERSION}.apk

echo "✅ Publicación completada"
echo ""
echo "URLs disponibles:"
echo "  • https://whatsberry.descarga.media/downloads/whatsberry-new.apk"
echo "  • https://whatsberry.descarga.media/downloads/whatsberry-v${VERSION}.apk"
echo "  • http://whatsberry.descarga.media/downloads/whatsberry-new.apk"
echo "  • http://whatsberry.descarga.media:9003/whatsberry-v${VERSION}.apk"
```

### Paso 3: Verificar Publicación

```bash
# Verificar HTTPS
curl -I https://whatsberry.descarga.media/downloads/whatsberry-new.apk

# Verificar HTTP
curl -I http://whatsberry.descarga.media/downloads/whatsberry-new.apk

# Verificar puerto 9003
curl -I http://whatsberry.descarga.media:9003/whatsberry-v2.7.0.apk

# Todos deben retornar: HTTP 200 OK
```

---

## 📂 Estructura de Directorios

```
/opt/whatsberry/
├── app/
│   └── build/outputs/apk/debug/
│       └── app-debug.apk               ← APK compilada (temporal)
│
└── public/
    ├── downloads/                       ← DIRECTORIO PRINCIPAL HTTPS/HTTP
    │   ├── whatsberry-new.apk          ← Versión más reciente (sobrescribir)
    │   ├── whatsberry-v2.7.0.apk       ← Versión específica
    │   ├── whatsberry-v2.6.0.apk       ← Versiones anteriores
    │   └── whatsberry-hardcoded.apk    ← Versiones especiales
    │
    ├── whatsberry-v2.7.0.apk           ← Para puerto 9003
    ├── whatsberry-v2.2.0.apk           ← Versiones anteriores
    ├── upload.php
    ├── convert_audio.php
    └── index.html

/var/www/html/downloads/                 ← ⚠️ NO USAR - No configurado en nginx
```

---

## 🌐 URLs Públicas de Descarga

### URLs Principales (Recomendadas)
```
✅ https://whatsberry.descarga.media/downloads/whatsberry-new.apk
   → Siempre apunta a la versión más reciente
   → HTTPS seguro
   → Autoindex habilitado (se puede navegar)

✅ https://whatsberry.descarga.media/downloads/whatsberry-v2.7.0.apk
   → Versión específica
   → HTTPS seguro
```

### URLs Alternativas
```
✅ http://whatsberry.descarga.media/downloads/whatsberry-new.apk
   → Mismo contenido que HTTPS
   → HTTP sin cifrar

✅ http://whatsberry.descarga.media:9003/whatsberry-v2.7.0.apk
   → Puerto alternativo
   → Directorio diferente (/opt/whatsberry/public/)
```

---

## 🔍 Verificación de Configuración

### Comando para verificar configuración activa de nginx
```bash
sudo nginx -T | grep -A 20 "location /downloads"
```

**Output esperado**:
```nginx
location /downloads/ {
    alias /opt/whatsberry/public/downloads/;
    autoindex on;
    add_header Content-Disposition "attachment";
}
```

### Comando para listar APKs publicadas
```bash
ls -lh /opt/whatsberry/public/downloads/*.apk
ls -lh /opt/whatsberry/public/whatsberry*.apk
```

---

## ❌ Errores Comunes y Soluciones

### Error 1: APK no accesible vía HTTPS
**Síntoma**: `curl -I https://whatsberry.descarga.media/downloads/whatsberry-new.apk` retorna 404

**Causas**:
- ❌ APK copiada a `/var/www/html/downloads/` (ubicación incorrecta)
- ❌ Permisos incorrectos

**Solución**:
```bash
# Verificar ubicación correcta
ls -la /opt/whatsberry/public/downloads/whatsberry-new.apk

# Si no existe, copiar de nuevo
sudo cp /opt/whatsberry/app/build/outputs/apk/debug/app-debug.apk \
        /opt/whatsberry/public/downloads/whatsberry-new.apk
sudo chown batman:batman /opt/whatsberry/public/downloads/whatsberry-new.apk
sudo chmod 644 /opt/whatsberry/public/downloads/whatsberry-new.apk
```

### Error 2: Permisos denegados
**Síntoma**: nginx retorna 403 Forbidden

**Solución**:
```bash
# Verificar permisos
ls -la /opt/whatsberry/public/downloads/

# Debe mostrar: -rw-r--r-- batman batman

# Corregir si es necesario
sudo chown batman:batman /opt/whatsberry/public/downloads/*.apk
sudo chmod 644 /opt/whatsberry/public/downloads/*.apk
```

### Error 3: Nginx no recarga configuración
**Síntoma**: Cambios en configuración no surten efecto

**Solución**:
```bash
# Verificar sintaxis
sudo nginx -t

# Recargar nginx
sudo systemctl reload nginx

# Si falla, reiniciar
sudo systemctl restart nginx
```

---

## 📊 Historial de Versiones Publicadas

| Versión | Fecha | Tamaño | Cambios Principales | URLs |
|---------|-------|--------|---------------------|------|
| v2.7.0  | 2025-12-07 | 57 MB | Debug logging para JID matching | [HTTPS](https://whatsberry.descarga.media/downloads/whatsberry-v2.7.0.apk) |
| v2.6.0  | 2025-12-07 | 56 MB | Typing indicators, emoji picker | - |
| v2.5.1  | 2025-12-07 | 52 MB | Bugfix version | downloads/ |
| v2.2.0  | 2025-12-07 | 53 MB | XMPP basic messaging | puerto 9003 |

---

## 🛠️ Script Automatizado de Publicación

**Ubicación**: `/opt/whatsberry/publish-apk.sh`

```bash
#!/bin/bash
# Publicar nueva versión de WhatsBerry APK
# Uso: ./publish-apk.sh [VERSION]
# Ejemplo: ./publish-apk.sh 2.7.0

set -e

VERSION=${1:-"2.7.0"}
APK_SOURCE="/opt/whatsberry/app/build/outputs/apk/debug/app-debug.apk"

echo "================================================"
echo "  📦 WhatsBerry APK Publication Script"
echo "================================================"
echo "Version: v${VERSION}"
echo ""

# Verificar que el APK existe
if [ ! -f "$APK_SOURCE" ]; then
    echo "❌ ERROR: APK no encontrada en $APK_SOURCE"
    echo "   Por favor ejecuta primero: ./gradlew assembleDebug"
    exit 1
fi

# Mostrar tamaño del APK
APK_SIZE=$(du -h "$APK_SOURCE" | cut -f1)
echo "📊 Tamaño del APK: $APK_SIZE"
echo ""

# Crear directorios si no existen
echo "📁 Verificando directorios..."
sudo mkdir -p /opt/whatsberry/public/downloads
echo "   ✓ /opt/whatsberry/public/downloads/"
echo ""

# Copiar APKs
echo "📦 Publicando APKs..."

echo "   → whatsberry-new.apk"
sudo cp "$APK_SOURCE" /opt/whatsberry/public/downloads/whatsberry-new.apk

echo "   → whatsberry-v${VERSION}.apk (downloads)"
sudo cp "$APK_SOURCE" /opt/whatsberry/public/downloads/whatsberry-v${VERSION}.apk

echo "   → whatsberry-v${VERSION}.apk (public)"
sudo cp "$APK_SOURCE" /opt/whatsberry/public/whatsberry-v${VERSION}.apk

echo ""

# Ajustar permisos
echo "🔐 Ajustando permisos..."
sudo chown batman:batman /opt/whatsberry/public/downloads/whatsberry-new.apk
sudo chown batman:batman /opt/whatsberry/public/downloads/whatsberry-v${VERSION}.apk
sudo chown batman:batman /opt/whatsberry/public/whatsberry-v${VERSION}.apk
sudo chmod 644 /opt/whatsberry/public/downloads/*.apk
sudo chmod 644 /opt/whatsberry/public/whatsberry-v${VERSION}.apk
echo "   ✓ Permisos: 644 (batman:batman)"
echo ""

# Verificar publicación
echo "🔍 Verificando publicación..."

check_url() {
    local url=$1
    local status=$(curl -s -o /dev/null -w "%{http_code}" "$url")
    if [ "$status" = "200" ]; then
        echo "   ✅ $url"
    else
        echo "   ❌ $url (HTTP $status)"
    fi
}

check_url "https://whatsberry.descarga.media/downloads/whatsberry-new.apk"
check_url "https://whatsberry.descarga.media/downloads/whatsberry-v${VERSION}.apk"
check_url "http://whatsberry.descarga.media/downloads/whatsberry-new.apk"
check_url "http://whatsberry.descarga.media:9003/whatsberry-v${VERSION}.apk"

echo ""
echo "================================================"
echo "  ✅ PUBLICACIÓN COMPLETADA"
echo "================================================"
echo ""
echo "🌐 URLs de descarga:"
echo "   • https://whatsberry.descarga.media/downloads/whatsberry-new.apk"
echo "   • https://whatsberry.descarga.media/downloads/whatsberry-v${VERSION}.apk"
echo "   • http://whatsberry.descarga.media/downloads/whatsberry-new.apk"
echo "   • http://whatsberry.descarga.media:9003/whatsberry-v${VERSION}.apk"
echo ""
echo "📂 Archivos locales:"
ls -lh /opt/whatsberry/public/downloads/whatsberry*.apk
echo ""
ls -lh /opt/whatsberry/public/whatsberry-v${VERSION}.apk
echo ""
echo "✨ Listo para descargar!"
