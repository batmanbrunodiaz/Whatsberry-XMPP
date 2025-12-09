# Guía de Build y Firmado de APKs - Aclaraciones Importantes

**Última actualización**: 7 de diciembre de 2025

## ⚠️ IMPORTANTE: Debug vs Release

### Concepto Clave

**TODOS los APKs deben estar firmados para instalarse en Android.** La diferencia está en QUÉ firma usan:

| Tipo | Firma | Cuándo Re-firmar | Uso |
|------|-------|------------------|-----|
| **Debug** | Automática (debug keystore) | ❌ NO necesario | Desarrollo, testing |
| **Release** | Manual (tu keystore) | ✅ SÍ necesario | Producción, Play Store |

### ⚠️ ERROR COMÚN

```bash
# ❌ INCORRECTO: Firmar un APK que ya está firmado con debug
./gradlew assembleDebug          # Genera APK debug-signed
# ... luego ...
apksigner sign ...                # ¡ERROR! Ya está firmado

# ✅ CORRECTO: Opción 1 - Usar debug build directamente
./gradlew assembleDebug           # Genera APK listo para usar

# ✅ CORRECTO: Opción 2 - Build release (sin firma) y firmar
./gradlew assembleRelease         # Genera APK sin firmar
apksigner sign ...                # Firmar manualmente
```

---

## Proyecto 1: BlackBerry Wrapper (~/blackberry-wrapper)

### Build Actual
```bash
#!/bin/bash
# Archivo: /home/batman/build-apk.sh
export ANDROID_HOME=~/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

cd ~/blackberry-wrapper

echo "Building APK..."
./gradlew assembleDebug --no-daemon --stacktrace
```

### ¿Qué genera?

**Output**: `app/build/outputs/apk/debug/app-debug.apk`

**Estado de firma**: ✅ **YA FIRMADO** con debug keystore

**Ubicación de debug keystore**: `~/.android/debug.keystore` (generado automáticamente)

### ¿Necesita firmarse de nuevo?

**❌ NO** - El APK debug ya está firmado y listo para:
- Instalar directamente en dispositivos
- Testing y desarrollo
- Distribución interna (no producción)

### ¿Cuándo usar Release Build?

**Solo si necesitas**:
- Publicar en Play Store
- Distribución pública/producción
- Firma personalizada para branding

```bash
# Para release:
./gradlew assembleRelease   # Genera APK sin firmar
# Luego firmar manualmente (ver sección de firmado abajo)
```

---

## Proyecto 2: WhatsBerry APK Modification (/opt/whatsberry/apk-mod)

### Proceso Actual

**Input**: `WhatsBerry_v0_11_1-beta.apk` (APK original)

**Pasos**:
1. Descompilar con apktool
2. Modificar URLs (.smali files)
3. Recompilar con apktool
4. **IMPORTANTE**: Apktool genera APK **SIN FIRMAR** o con firma temporal
5. ✅ **Debe firmarse manualmente**

### ¿Por qué necesita firmarse?

Cuando modificas un APK existente:
- `apktool b` genera APK **sin firma válida**
- Android **no lo instala** sin firma válida
- **Debes firmarlo manualmente** con apksigner

### Proceso Correcto

```bash
# 1. Descompilar
apktool d WhatsBerry_v0_11_1-beta.apk -o whatsberry-decompiled

# 2. Modificar
cd whatsberry-decompiled/smali/com/blackberry/whatsapp
sed -i 's|whatsberry.com|whatsberry.descarga.media|g' WhatsAppAPI.smali
sed -i 's|wss://whatsberry.com|wss://whatsberry.descarga.media|g' WebSocketManager.smali

# 3. Recompilar (genera APK SIN FIRMAR)
cd /opt/whatsberry/apk-mod
apktool b whatsberry-decompiled -o WhatsBerry-BB10-CUSTOM.apk

# 4. Alinear (ANTES de firmar)
~/Android/Sdk/build-tools/34.0.0/zipalign -f -v 4 \
  WhatsBerry-BB10-CUSTOM.apk \
  WhatsBerry-BB10-CUSTOM-aligned.apk

# 5. Firmar (DESPUÉS de alinear)
~/Android/Sdk/build-tools/34.0.0/apksigner sign \
  --ks whatsberry.keystore \
  --ks-key-alias whatsberry \
  --ks-pass pass:whatsberry123 \
  --key-pass pass:whatsberry123 \
  --out WhatsBerry-BB10-CUSTOM-final.apk \
  WhatsBerry-BB10-CUSTOM-aligned.apk

# 6. Verificar
~/Android/Sdk/build-tools/34.0.0/apksigner verify -v WhatsBerry-BB10-CUSTOM-final.apk
```

---

## Comparación de Herramientas de Firmado

### jarsigner (❌ Antigua, NO usar)

```bash
# Método antiguo - NO RECOMENDADO
jarsigner -verbose \
  -sigalg SHA1withRSA \
  -digestalg SHA1 \
  -keystore whatsberry.keystore \
  WhatsBerry-BB10-CUSTOM.apk \
  whatsberry
```

**Problemas**:
- Solo soporta v1 signature scheme (antiguo)
- No soporta v2/v3 (más seguros y eficientes)
- Android 7+ prefiere v2/v3

### apksigner (✅ Moderna, USAR SIEMPRE)

```bash
# Método moderno - RECOMENDADO
~/Android/Sdk/build-tools/34.0.0/apksigner sign \
  --ks whatsberry.keystore \
  --ks-key-alias whatsberry \
  --ks-pass pass:whatsberry123 \
  --key-pass pass:whatsberry123 \
  --out WhatsBerry-BB10-CUSTOM-final.apk \
  WhatsBerry-BB10-CUSTOM-aligned.apk
```

**Ventajas**:
- ✅ Soporta v1, v2, v3 signature schemes
- ✅ Más seguro y eficiente
- ✅ Optimizado para Android moderno
- ✅ Verificación integrada

### Verificar Firma

```bash
# Con apksigner
~/Android/Sdk/build-tools/34.0.0/apksigner verify -v WhatsBerry-BB10-CUSTOM-final.apk

# Output esperado:
Verifies
Verified using v1 scheme (JAR signing): true
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
```

---

## ⚠️ Orden CRÍTICO de Operaciones

### Para APK Modificado (apktool)

```
1. apktool d      (descompilar)
2. [modificar]    (editar archivos)
3. apktool b      (recompilar - genera APK SIN FIRMAR)
4. zipalign       (alinear - ANTES de firmar) ← IMPORTANTE
5. apksigner      (firmar - DESPUÉS de alinear) ← IMPORTANTE
6. verify         (verificar)
```

**❌ ERROR COMÚN**: Firmar antes de alinear
```bash
apksigner sign ... original.apk
zipalign ... signed.apk          # ¡ERROR! Rompe la firma
```

**✅ CORRECTO**: Alinear antes de firmar
```bash
zipalign ... original.apk aligned.apk
apksigner sign ... aligned.apk   # Correcto
```

### Para Gradle Build

#### Debug (No necesita firmado manual)
```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
# ✅ Ya firmado, listo para usar
```

#### Release (Necesita configuración)

**Opción 1**: Configurar en `build.gradle`
```gradle
android {
    signingConfigs {
        release {
            storeFile file("whatsberry.keystore")
            storePassword "whatsberry123"
            keyAlias "whatsberry"
            keyPassword "whatsberry123"
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
        }
    }
}
```

```bash
./gradlew assembleRelease
# Output firmado automáticamente
```

**Opción 2**: Firmar manualmente después
```bash
./gradlew assembleRelease  # Genera sin firmar
apksigner sign ...         # Firmar manualmente
```

---

## Rutas de APKs Publicadas

### Ubicación en Servidor

```
/opt/whatsberry/public/
├── whatsberry-v2.2.0.apk    (53.6 MB) - Versión 2.2.0
└── whatsberry-v2.7.0.apk    (57.9 MB) - Versión 2.7.0 (más reciente)
```

### URLs de Descarga

**Versión actual (2.7.0)**:
```
http://whatsberry.descarga.media:9003/whatsberry-v2.7.0.apk
https://whatsberry.descarga.media/whatsberry-v2.7.0.apk
```

**Versión anterior (2.2.0)**:
```
http://whatsberry.descarga.media:9003/whatsberry-v2.2.0.apk
```

### Actualizar APK Publicada

```bash
# Después de build exitoso:
sudo cp ~/blackberry-wrapper/app/build/outputs/apk/debug/app-debug.apk \
        /opt/whatsberry/public/whatsberry-v2.7.0.apk

# Ajustar permisos
sudo chown batman:batman /opt/whatsberry/public/whatsberry-v2.7.0.apk
sudo chmod 644 /opt/whatsberry/public/whatsberry-v2.7.0.apk

# Verificar
ls -lh /opt/whatsberry/public/*.apk
curl -I http://localhost:9003/whatsberry-v2.7.0.apk
```

---

## Keystore Management

### Ubicación Actual

```
/opt/whatsberry/apk-mod/whatsberry.keystore
```

### Credenciales

```
Store Password: whatsberry123
Key Alias:      whatsberry
Key Password:   whatsberry123
```

### ⚠️ SEGURIDAD

**Para producción**:
- ✅ Usar contraseñas fuertes
- ✅ Backup del keystore (si lo pierdes, no puedes actualizar la app)
- ✅ NO commitear keystore al repositorio
- ✅ Usar variables de entorno para passwords

**Para desarrollo/testing**:
- ✅ Usar debug keystore (automático)
- ✅ Passwords simples OK

### Backup del Keystore

```bash
# Backup crítico
cp /opt/whatsberry/apk-mod/whatsberry.keystore ~/whatsberry.keystore.backup
chmod 400 ~/whatsberry.keystore.backup

# Verificar integridad
keytool -list -v -keystore ~/whatsberry.keystore.backup
```

---

## Scripts Automatizados

### Script para WhatsBerry APK Modification

Crear `/opt/whatsberry/apk-mod/rebuild-and-sign.sh`:

```bash
#!/bin/bash
set -e

cd /opt/whatsberry/apk-mod

echo "=== 1. Descompilando ==="
apktool d WhatsBerry_v0_11_1-beta.apk -o whatsberry-decompiled -f

echo "=== 2. Modificando URLs ==="
cd whatsberry-decompiled/smali/com/blackberry/whatsapp
sed -i 's|https://whatsberry.com|https://whatsberry.descarga.media|g' WhatsAppAPI.smali
sed -i 's|wss://whatsberry.com|wss://whatsberry.descarga.media|g' WebSocketManager.smali
cd /opt/whatsberry/apk-mod

echo "=== 3. Recompilando ==="
apktool b whatsberry-decompiled -o WhatsBerry-BB10-CUSTOM.apk

echo "=== 4. Alineando (ANTES de firmar) ==="
~/Android/Sdk/build-tools/34.0.0/zipalign -f -v 4 \
  WhatsBerry-BB10-CUSTOM.apk \
  WhatsBerry-BB10-CUSTOM-aligned.apk

echo "=== 5. Firmando (DESPUÉS de alinear) ==="
~/Android/Sdk/build-tools/34.0.0/apksigner sign \
  --ks whatsberry.keystore \
  --ks-key-alias whatsberry \
  --ks-pass pass:whatsberry123 \
  --key-pass pass:whatsberry123 \
  --out WhatsBerry-BB10-CUSTOM-final.apk \
  WhatsBerry-BB10-CUSTOM-aligned.apk

echo "=== 6. Verificando ==="
~/Android/Sdk/build-tools/34.0.0/apksigner verify -v WhatsBerry-BB10-CUSTOM-final.apk

echo ""
echo "✅ APK firmada lista: WhatsBerry-BB10-CUSTOM-final.apk"
echo ""
echo "Para publicar:"
echo "  sudo cp WhatsBerry-BB10-CUSTOM-final.apk /opt/whatsberry/public/"
```

### Script para BlackBerry Wrapper Build

Actualizar `/home/batman/build-apk.sh`:

```bash
#!/bin/bash
set -e

export ANDROID_HOME=~/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

cd ~/blackberry-wrapper

echo "=== Building Debug APK ==="
./gradlew assembleDebug --no-daemon --stacktrace

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

echo ""
echo "✅ APK Debug generada (YA FIRMADA):"
echo "   $APK_PATH"
echo ""
echo "Tamaño:"
ls -lh "$APK_PATH"
echo ""
echo "❌ NO necesita firmarse de nuevo (ya tiene debug signature)"
echo ""
echo "Para publicar:"
echo "  sudo cp $APK_PATH /opt/whatsberry/public/whatsberry-v2.7.0.apk"
```

---

## FAQ - Preguntas Frecuentes

### ¿Necesito firmar un APK debug?

**❌ NO** - `./gradlew assembleDebug` genera APK YA FIRMADO con debug keystore.

### ¿Por qué apktool necesita firmado manual?

Porque `apktool b` genera APK **sin firma válida**. Debes firmarlo tú.

### ¿Cuál es mejor: jarsigner o apksigner?

**apksigner** - Siempre. Soporta v1/v2/v3 signatures. jarsigner es obsoleto.

### ¿Puedo firmar un APK ya firmado?

**Sí**, pero **perderás la firma anterior**. apksigner reemplaza todas las firmas existentes.

### ¿Qué pasa si firmo antes de zipalign?

❌ **ERROR** - zipalign modifica el APK y **rompe la firma**. Siempre:
1. zipalign primero
2. firmar después

### ¿Pierdo algo usando debug signature?

Para **desarrollo**: ❌ No
Para **producción**: ✅ Sí - Play Store requiere release signature

### ¿Cómo verifico si un APK está firmado?

```bash
~/Android/Sdk/build-tools/34.0.0/apksigner verify -v tu-app.apk
```

### ¿Dónde está el debug keystore?

```
~/.android/debug.keystore
```

**Credenciales debug** (estándar Android):
- Store Password: `android`
- Key Alias: `androiddebugkey`
- Key Password: `android`

---

## Resumen Rápido

| Escenario | Comando | ¿Necesita firmado manual? |
|-----------|---------|---------------------------|
| Gradle Debug | `./gradlew assembleDebug` | ❌ NO (auto-firmado) |
| Gradle Release (configurado) | `./gradlew assembleRelease` | ❌ NO (auto-firmado) |
| Gradle Release (sin config) | `./gradlew assembleRelease` | ✅ SÍ (firmar con apksigner) |
| apktool modificación | `apktool b ...` | ✅ SÍ (firmar con apksigner) |

**Regla de oro**: Si usaste `apktool b`, **SIEMPRE** zipalign + apksigner.

---

## Referencias

- [APK Signature Scheme](https://source.android.com/docs/security/features/apksigning)
- [apksigner Documentation](https://developer.android.com/studio/command-line/apksigner)
- [zipalign Documentation](https://developer.android.com/studio/command-line/zipalign)
- [Gradle Signing](https://developer.android.com/studio/publish/app-signing#gradle-sign)

---

**Última actualización**: 7 de diciembre de 2025
**Autor**: Documentación WhatsBerry Project
