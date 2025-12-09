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
