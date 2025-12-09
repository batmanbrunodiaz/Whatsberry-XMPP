# 📚 Índice Maestro de Documentación - WhatsBerry Project

**Última actualización**: 7 de diciembre de 2025

Este documento sirve como índice central de toda la documentación del proyecto WhatsBerry.

---

## 🚀 Inicio Rápido

**Si eres nuevo en el proyecto, lee en este orden**:

1. **README.md** (9.5K) - Overview general del proyecto
2. **CONFIGURACION_CONEXION.md** (10K) - Configurar conexión XMPP/cliente
3. **APK_BUILD_SIGNING_GUIDE.md** (13K) - 🆕 Build y firmado de APKs

---

## 📖 Documentación por Categoría

### 🏗️ Arquitectura y Diseño

| Documento | Tamaño | Descripción |
|-----------|--------|-------------|
| **README.md** | 9.5K | Overview general, arquitectura, instalación |
| **TECHNICAL.md** | 31K | 🔥 Detalles técnicos profundos, decisiones arquitectónicas |
| **TROUBLESHOOTING_STARTTLS_PROXY.md** | 17K | 🆕 Lecciones aprendidas del proxy STARTTLS |

**Recomendación**: Empieza con README.md, luego TECHNICAL.md para profundizar.

---

### 🔧 Configuración y Deployment

| Documento | Tamaño | Descripción |
|-----------|--------|-------------|
| **CONFIGURACION_CONEXION.md** | 10K | Configurar servidor, clientes, troubleshooting |
| **PROYECTO_COMPLETADO.md** | 7.4K | Estado del proyecto, features completadas |
| **PASOS_SIGUIENTES.md** | 8.4K | Roadmap, mejoras futuras |

**Recomendación**: CONFIGURACION_CONEXION.md es esencial para setup.

---

### 📱 APK Build y Modificación

| Documento | Tamaño | Descripción |
|-----------|--------|-------------|
| **APK_BUILD_SIGNING_GUIDE.md** | 13K | 🆕 **CLAVE**: Debug vs Release, firmado, apksigner |
| **APK_PUBLICATION_GUIDE.md** | 15K | 🆕 **IMPORTANTE**: Dónde y cómo publicar APKs |
| **apk-mod/METODO_MODIFICAR_APK.md** | 7.3K | Modificar APK existente con apktool |
| **CAMBIOS_ICONO_NOMBRE.md** | 3.9K | Cambiar icono y nombre de la app |
| **REVERSE_ENGINEERING.md** | 3.1K | Análisis de APK original |

**⚠️ IMPORTANTE**: Lee en este orden:
1. **APK_BUILD_SIGNING_GUIDE.md** - Cómo compilar y firmar
   - Por qué `./gradlew assembleDebug` NO necesita firmado manual
   - Por qué `apktool b` SÍ necesita firmado manual
   - Diferencia entre apksigner y jarsigner
   - Orden correcto: zipalign → apksigner

2. **APK_PUBLICATION_GUIDE.md** - Dónde publicar la APK
   - ✅ Ubicaciones CORRECTAS: `/opt/whatsberry/public/downloads/`
   - ❌ Ubicaciones INCORRECTAS: `/var/www/html/downloads/`
   - Configuración de nginx
   - Script automatizado: `./publish-apk.sh`

**Rutas de APKs publicadas** (CORRECTAS):
```
/opt/whatsberry/public/downloads/whatsberry-new.apk       (57 MB) - Última versión
/opt/whatsberry/public/downloads/whatsberry-v2.7.0.apk    (57 MB) - v2.7.0
/opt/whatsberry/public/whatsberry-v2.7.0.apk              (57 MB) - v2.7.0 (puerto 9003)
```

**URLs de descarga** (TODAS FUNCIONANDO):
```
https://whatsberry.descarga.media/downloads/whatsberry-new.apk       ← Principal (HTTPS)
https://whatsberry.descarga.media/downloads/whatsberry-v2.7.0.apk
http://whatsberry.descarga.media/downloads/whatsberry-new.apk
http://whatsberry.descarga.media:9003/whatsberry-v2.7.0.apk          ← Alternativa
```

**Script de publicación automatizado**:
```bash
cd /opt/whatsberry
./publish-apk.sh 2.7.0
```

---

### 🌐 XMPP y Gateway WhatsApp

| Documento | Tamaño | Descripción |
|-----------|--------|-------------|
| **FIX_GATEWAY_REGISTRATION.md** | 7.4K | Fix de registro con gateway WhatsApp |
| **OBTENER_QR_MANUAL.md** | 4.3K | Obtener código QR manualmente |
| **WEB_QR_READY.md** | 6.0K | Implementación de QR web |
| **NUEVO_FLUJO_MENSAJES.md** | 5.5K | Flujo de mensajes actualizado |
| **PASOS_GAJIM.md** | 8.3K | Testing con cliente Gajim |

**Flujo típico**:
1. FIX_GATEWAY_REGISTRATION.md - Registrar con gateway
2. OBTENER_QR_MANUAL.md - Obtener QR si es necesario
3. WEB_QR_READY.md - Usar interfaz web para QR

---

### 🔍 Troubleshooting y Debugging

| Documento | Tamaño | Descripción | Problemas que Resuelve |
|-----------|--------|-------------|------------------------|
| **TROUBLESHOOTING_STARTTLS_PROXY.md** | 17K | 🔥 **MÁS IMPORTANTE** | 7 problemas críticos del proxy documentados |
| **CONFIGURACION_CONEXION.md** | 10K | Troubleshooting de conexión | Conexión, timeouts, UFW |
| **APK_BUILD_SIGNING_GUIDE.md** | 13K | Troubleshooting de build | Firmado, zipalign, apksigner |

**Problemas Comunes Cubiertos**:

#### Proxy STARTTLS (TROUBLESHOOTING_STARTTLS_PROXY.md)
- ✅ TLS handshake timing
- ✅ Listeners no ejecutándose
- ✅ UFW rate limiting bloqueando conexiones
- ✅ Socket event listener cascade
- ✅ Prosody cerrando conexión TCP
- ✅ XMPP stream conflicts
- ✅ pause/resume ordering

#### APK Build (APK_BUILD_SIGNING_GUIDE.md)
- ✅ "APK no se instala" → zipalign antes de firmar
- ✅ "Ya está firmado con debug" → No re-firmar assembleDebug
- ✅ "Firma inválida" → Usar apksigner, no jarsigner
- ✅ "Version incompatible" → Java 17

#### Conexión (CONFIGURACION_CONEXION.md)
- ✅ Connection timeout → UFW LIMIT → ALLOW
- ✅ TLS handshake failed → Verificar proxy TLS 1.0
- ✅ No recibe mensajes → Verificar relay bidireccional

---

## 🎯 Escenarios de Uso

### "Quiero entender el proyecto"
1. README.md - Overview
2. TECHNICAL.md - Profundidad
3. TROUBLESHOOTING_STARTTLS_PROXY.md - Lecciones aprendidas

### "Quiero configurar el servidor"
1. README.md - Instalación base
2. CONFIGURACION_CONEXION.md - Configuración detallada
3. TROUBLESHOOTING_STARTTLS_PROXY.md - Si hay problemas

### "Quiero compilar la APK"
1. **APK_BUILD_SIGNING_GUIDE.md** ← **EMPIEZA AQUÍ**
2. build-apk.sh script (para debug builds)
3. apk-mod/METODO_MODIFICAR_APK.md (para modificar APKs existentes)

### "Quiero modificar la APK original"
1. **APK_BUILD_SIGNING_GUIDE.md** - Entender firmado
2. apk-mod/METODO_MODIFICAR_APK.md - Proceso de modificación
3. CAMBIOS_ICONO_NOMBRE.md - Personalización

### "Tengo un error"
1. Busca el error en **TROUBLESHOOTING_STARTTLS_PROXY.md** (proxy)
2. Busca en **APK_BUILD_SIGNING_GUIDE.md** (APK)
3. Busca en **CONFIGURACION_CONEXION.md** (conexión)

### "Quiero contribuir"
1. TECHNICAL.md - Entender arquitectura
2. TROUBLESHOOTING_STARTTLS_PROXY.md - Aprender de errores pasados
3. PASOS_SIGUIENTES.md - Ver qué falta

---

## 📊 Documentación Completa

```
Total: 14 archivos .md
Tamaño total: ~134 KB

Desglose:
├── Arquitectura/Diseño: 57.5K (README, TECHNICAL, TROUBLESHOOTING_STARTTLS_PROXY)
├── APK Build/Mod: 27.5K (APK_BUILD_SIGNING, METODO_MODIFICAR, CAMBIOS_ICONO, REVERSE)
├── Config/Deploy: 25.8K (CONFIGURACION, PROYECTO_COMPLETADO, PASOS_SIGUIENTES)
├── XMPP/Gateway: 31.5K (FIX_GATEWAY, OBTENER_QR, WEB_QR, NUEVO_FLUJO, PASOS_GAJIM)
```

---

## 🆕 Documentos Nuevos (7 de diciembre de 2025)

**Creados hoy**:
1. **APK_BUILD_SIGNING_GUIDE.md** (13K)
   - Clarifica debug vs release
   - Explica cuándo firmar y cuándo no
   - Documenta apksigner vs jarsigner
   - Incluye rutas de APKs publicadas

2. **TROUBLESHOOTING_STARTTLS_PROXY.md** (17K)
   - 7 problemas críticos resueltos
   - 7 lecciones aprendidas
   - Código examples de cada problema
   - Configuración final completa

**Actualizados hoy**:
1. **README.md** - Sección proxy STARTTLS
2. **TECHNICAL.md** - Nueva sección completa de proxy (276 líneas)
3. **CONFIGURACION_CONEXION.md** - Arquitectura proxy, UFW troubleshooting
4. **apk-mod/METODO_MODIFICAR_APK.md** - Referencia a nueva guía

---

## 🔑 Conceptos Clave por Documento

### README.md
- Arquitectura general (BB10 → Proxy → Prosody → slidge → WhatsApp)
- Stack tecnológico
- Instalación base

### TECHNICAL.md
- Decisión arquitectónica: Nueva conexión post-TLS
- Flujo STARTTLS de 11 pasos
- Performance characteristics
- Security considerations

### TROUBLESHOOTING_STARTTLS_PROXY.md
- **Problema más difícil**: Listener registrado pero nunca ejecutado
- **Solución clave**: Nueva conexión TCP a Prosody después de TLS
- **UFW rate limiting**: Cambiar LIMIT a ALLOW
- **Socket cleanup**: removeAllListeners() antes de destroy()

### APK_BUILD_SIGNING_GUIDE.md
- **Debug builds**: YA firmados automáticamente
- **apktool builds**: Necesitan firmado manual
- **Orden correcto**: zipalign → apksigner
- **apksigner > jarsigner**: v1/v2/v3 schemes

### CONFIGURACION_CONEXION.md
- Conexión recomendada: whatsberry.descarga.media:5222
- UFW: ALLOW (no LIMIT) para 5222
- Troubleshooting de timeouts y TLS

---

## 💡 Tips de Navegación

### Búsqueda Rápida

```bash
# Buscar en toda la documentación
grep -r "palabra clave" /opt/whatsberry/*.md

# Buscar solo en troubleshooting
grep -i "error" /opt/whatsberry/TROUBLESHOOTING_*.md

# Listar todos los documentos
ls -lh /opt/whatsberry/*.md
```

### Documentos Más Útiles

**Top 5 por utilidad**:
1. **APK_BUILD_SIGNING_GUIDE.md** - Evita errores de firmado
2. **TROUBLESHOOTING_STARTTLS_PROXY.md** - Resuelve problemas complejos
3. **TECHNICAL.md** - Entendimiento profundo
4. **CONFIGURACION_CONEXION.md** - Setup rápido
5. **README.md** - Overview general

**Top 3 por lecciones aprendidas**:
1. **TROUBLESHOOTING_STARTTLS_PROXY.md** - 7 problemas documentados
2. **APK_BUILD_SIGNING_GUIDE.md** - Clarifica confusiones comunes
3. **TECHNICAL.md** - Decisiones arquitectónicas explicadas

---

## 📞 Soporte

Si no encuentras lo que buscas:

1. **Busca en este índice** la categoría relevante
2. **Lee el documento específico** de esa categoría
3. **Verifica troubleshooting** en los 3 documentos principales
4. **Revisa logs** según comandos en TROUBLESHOOTING_STARTTLS_PROXY.md

**Comandos útiles de diagnóstico**:
```bash
# Ver logs del proxy
sudo journalctl -u xmpp-tls-proxy.service -f

# Ver estado de servicios
sudo systemctl status xmpp-tls-proxy prosody

# Verificar APK firmada
~/Android/Sdk/build-tools/34.0.0/apksigner verify -v tu-app.apk

# Ver puertos escuchando
ss -tlnp | grep -E "(5222|5200|9003)"
```

---

## 📝 Mantenimiento de Documentación

**Cuando actualices el código**:
- ✅ Actualiza TECHNICAL.md con cambios arquitectónicos
- ✅ Actualiza TROUBLESHOOTING si resuelves un nuevo problema
- ✅ Actualiza APK_BUILD_SIGNING_GUIDE.md si cambias proceso de build
- ✅ Actualiza este índice si agregas nuevos documentos

**Cuando encuentres un bug**:
- ✅ Documenta en TROUBLESHOOTING_STARTTLS_PROXY.md
- ✅ Incluye: síntoma, causa, solución, lección aprendida

---

**Última actualización**: 7 de diciembre de 2025
**Documentos totales**: 14 archivos .md
**Tamaño total**: ~134 KB
**Estado**: ✅ Completamente documentado
