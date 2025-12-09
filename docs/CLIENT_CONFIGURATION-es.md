# 🔧 Configuración de Conexión - WhatsApp XMPP Client

## ✅ Estado del Servidor

### Servicios Activos
- ✅ **XMPP STARTTLS Proxy** - Running (xmpp-starttls-proxy.js)
- ✅ **Prosody XMPP Server** - Running (Backend, port 5200)
- ✅ **Slidge-WhatsApp Gateway** - Running (Background ID: 99603d)
- ✅ **Puerto 5222** - Abierto en firewall UFW (ALLOW, not LIMIT)

### Arquitectura de Conexión
```
BlackBerry Client → STARTTLS Proxy (5222) → Prosody Backend (5200)
                    [TLS 1.0+ Support]      [No TLS]
```

### Puertos Configurados
- **5222/tcp** - STARTTLS Proxy (clientes XMPP - tu app BB10) ✅
- **5200/tcp** - Prosody Backend (sin TLS, solo interno) ✅
- **5347/tcp** - Componentes (slidge-whatsapp) ✅
- **5269/tcp** - Servidor a servidor (federación)

---

## 📱 Configuración en la App BlackBerry

### Configuración Recomendada (con STARTTLS Proxy)
Conecta a través del proxy STARTTLS que soporta dispositivos legacy:

```
XMPP Server Settings:
├─ Server IP/Host: whatsberry.descarga.media
├─ Port: 5222
└─ Domain: localhost

Account Credentials:
├─ Username: tunombre
└─ Password: tupassword

WhatsApp Gateway:
└─ Gateway JID: whatsapp.localhost
```

**Ventajas del Proxy:**
- ✅ Soporte para TLS 1.0+ (compatible con BlackBerry 10)
- ✅ STARTTLS automático
- ✅ Sin necesidad de configurar certificados manualmente
- ✅ Funciona con dispositivos legacy

### Opción Alternativa: Conexión Local (Mismo WiFi)
Si tu BlackBerry está en la misma red WiFi que el servidor:

```
XMPP Server Settings:
├─ Server IP/Host: 10.0.0.2  (o 10.1.1.2, depende de tu red)
├─ Port: 5222
└─ Domain: localhost
```

### Opción 3: Conexión desde Internet
Si necesitas conectarte desde fuera de tu red local, necesitarás:

1. Tu IP pública (buscar en: https://www.whatismyip.com/)
2. Configurar port forwarding en tu router: `5222 → 10.0.0.2:5222`
3. Usar tu IP pública o dominio (whatsberry.descarga.media) en la app

---

## 🔐 Crear Usuario XMPP

### Método 1: Desde la App (Recomendado)
1. Abrir app en BB10
2. Llenar campos con la configuración de arriba
3. Click **"Register New Account"**
4. ¡Listo! La app crea la cuenta automáticamente

### Método 2: Desde el Servidor
Si prefieres crear manualmente:

```bash
# Crear usuario
sudo prosodyctl adduser miusuario@localhost

# Te pedirá una contraseña
# Luego en la app usar "Connect & Login"
```

---

## 📋 Proceso de Login Completo

### Paso 1: Instalar APK
```bash
# Conectar BB10 por USB
adb devices

# Instalar
adb install -r /opt/whatsberry/app/build/outputs/apk/debug/app-debug.apk
```

### Paso 2: Primera Configuración
1. Abrir "WhatsApp XMPP Client"
2. Configurar según tu red (ver arriba)
3. Click "Register New Account" (primera vez)
   - O "Connect & Login" (si ya tienes cuenta)

### Paso 3: Obtener QR de WhatsApp
1. La app automáticamente pide el QR
2. Se muestra en pantalla
3. **Escanear con WhatsApp oficial**:
   - WhatsApp móvil → Configuración
   - Dispositivos vinculados
   - Vincular un dispositivo
   - Escanear QR

### Paso 4: ¡Listo!
1. Click "Continue" en la app
2. Verás tus contactos de WhatsApp
3. Click en contacto → Chat
4. Envía mensajes

---

## 🔍 Verificar Conexión

### Desde el Servidor
```bash
# Ver si el proxy STARTTLS está escuchando en 5222
ss -tlnp | grep 5222

# Ver si Prosody backend está escuchando en 5200
ss -tlnp | grep 5200

# Ver logs del proxy STARTTLS
pm2 logs xmpp-starttls-proxy

# Ver logs de Prosody
sudo journalctl -u prosody -f

# Ver si slidge está corriendo
ps aux | grep slidge

# Verificar configuración UFW
sudo ufw status | grep 5222
# Debe mostrar: 5222/tcp ALLOW (no LIMIT)
```

### Desde el Cliente (BB10)
Si la app no conecta, verificar:

1. **Red correcta**: BB10 y servidor en misma WiFi
2. **IP correcta**: Usar `10.0.0.2` o `10.1.1.2`
3. **Firewall**: Puerto 5222 debe estar abierto
4. **Ping test**: `ping 10.0.0.2` desde BB10 (si tiene terminal)

---

## ⚠️ Solución de Problemas

### Error: "Connection failed"
- **PRIMERO**: Verificar que el proxy STARTTLS esté corriendo: `pm2 list`
- Verificar IP/dominio del servidor (whatsberry.descarga.media)
- Verificar que puerto 5222 esté abierto en UFW
- Verificar que Prosody backend esté corriendo en puerto 5200
- Ver logs del proxy: `pm2 logs xmpp-starttls-proxy`

### Error: "Authentication failed"
- Primero registrar cuenta ("Register New Account")
- O verificar usuario/password si ya existe

### Error: "Connection timeout" o desconexiones frecuentes
- **CAUSA COMÚN**: UFW con regla LIMIT en lugar de ALLOW
- **SOLUCIÓN**: Cambiar a ALLOW para evitar rate limiting
  ```bash
  sudo ufw delete limit 5222/tcp
  sudo ufw allow 5222/tcp
  sudo ufw reload
  ```
- El rate limiting de UFW puede bloquear conexiones legítimas de dispositivos BB10

### Error: "TLS handshake failed" o "SSL error"
- El proxy STARTTLS soporta TLS 1.0+ para dispositivos legacy
- Verificar que el proxy esté corriendo: `pm2 list | grep xmpp-starttls-proxy`
- Ver logs del proxy para detalles: `pm2 logs xmpp-starttls-proxy`
- Reiniciar proxy si es necesario: `pm2 restart xmpp-starttls-proxy`

### Error: "pairphone command not found"
- **SOLUCIONADO en versión 2.0.1** - La app ahora registra automáticamente en el gateway
- Ver detalles en `FIX_GATEWAY_REGISTRATION.md`
- Asegurarse de usar el APK más reciente (08:00, 53MB)

### QR Code no aparece
- Verificar que slidge-whatsapp esté corriendo
- Ver logs: `sudo journalctl -u prosody -f`

### Mensajes no llegan
- Verificar que WhatsApp esté vinculado (escanear QR)
- Esperar unos segundos (sincronización inicial)

---

## 📊 Información del Sistema

**Servidor:**
- OS: Arch Linux
- Prosody: 13.0.2
- Slidge-WhatsApp: 0.3.8
- IPs disponibles: 10.0.0.2, 10.1.1.2

**Cliente:**
- Platform: BlackBerry 10
- Android API: 18 (4.3)
- App Version: 2.0.1 (con fix gateway registration)
- Package: com.whatsberry.xmpp
- APK: /opt/whatsberry/app/build/outputs/apk/debug/app-debug.apk (53MB)

---

## 🎯 Ejemplo Completo de Configuración

```
===========================================
  CONFIGURACIÓN WHATSAPP XMPP CLIENT
  (Con STARTTLS Proxy)
===========================================

XMPP Server Settings:
  Server IP/Host....: whatsberry.descarga.media
  Port..............: 5222
  Domain............: localhost

  NOTA: El proxy maneja STARTTLS automáticamente
        Soporta TLS 1.0+ para dispositivos legacy

Account Credentials:
  Username..........: batman
  Password..........: miBatPassword123

WhatsApp Gateway:
  Gateway JID.......: whatsapp.localhost

===========================================
```

**Pasos:**
1. Llenar campos con estos valores
2. Click "Register New Account"
3. Esperar QR code
4. Escanear con WhatsApp móvil
5. Click "Continue"
6. ¡A chatear!

**Configuración Alternativa (Red Local):**
Si prefieres conectar localmente, puedes usar:
- Server IP/Host: 10.0.0.2 (o 10.1.1.2)
- El resto de los parámetros son iguales

---

## 🔗 Comandos Útiles

```bash
# === Gestión del Proxy STARTTLS ===
# Ver estado
pm2 list

# Ver logs en tiempo real
pm2 logs xmpp-starttls-proxy

# Reiniciar proxy
pm2 restart xmpp-starttls-proxy

# Detener proxy
pm2 stop xmpp-starttls-proxy

# Iniciar proxy
pm2 start xmpp-starttls-proxy

# === Gestión de Prosody ===
# Reiniciar Prosody backend
sudo systemctl restart prosody

# Ver estado de Prosody
sudo systemctl status prosody

# === Gestión de Slidge-WhatsApp ===
# Reiniciar slidge-whatsapp (si está en background)
# Primero matar proceso
ps aux | grep slidge | grep -v grep
kill <PID>

# Luego iniciar de nuevo
slidge-whatsapp -c ~/.config/slidge/whatsapp.conf -d

# === Gestión de Usuarios XMPP ===
# Ver usuarios XMPP registrados
sudo prosodyctl list localhost

# Crear usuario manualmente
sudo prosodyctl adduser usuario@localhost

# Eliminar usuario
sudo prosodyctl deluser usuario@localhost

# === Configuración de Firewall ===
# Verificar reglas UFW
sudo ufw status verbose

# Asegurar que 5222 sea ALLOW (no LIMIT)
sudo ufw delete limit 5222/tcp
sudo ufw allow 5222/tcp
sudo ufw reload

# Ver si hay conexiones bloqueadas por rate limiting
sudo journalctl -k | grep UFW | grep 5222
```

---

## 🔧 Arquitectura Técnica del Proxy STARTTLS

### ¿Por qué usar un proxy?
Los dispositivos BlackBerry 10 tienen limitaciones con TLS moderno. El proxy STARTTLS resuelve esto:

**Problema:**
- BB10 soporta TLS 1.0/1.1 (considerados inseguros)
- Prosody moderno requiere TLS 1.2+
- Conexión directa BB10 → Prosody = incompatibilidad TLS

**Solución:**
```
BB10 Client (TLS 1.0+)
    ↓
STARTTLS Proxy (puerto 5222)
    - Acepta TLS 1.0+
    - Termina TLS aquí
    ↓
Prosody Backend (puerto 5200)
    - Sin TLS (localhost)
    - Procesamiento XMPP normal
```

### Características del Proxy
- **Ubicación**: `/opt/whatsberry/xmpp-starttls-proxy.js`
- **Puerto externo**: 5222 (clientes XMPP)
- **Puerto backend**: 5200 (Prosody sin TLS)
- **Protocolo**: XMPP con STARTTLS
- **Soporte TLS**: 1.0, 1.1, 1.2, 1.3
- **Gestión**: PM2 (`pm2 list`)

### Flujo de Conexión
1. Cliente BB10 conecta al proxy en puerto 5222
2. Cliente inicia STARTTLS
3. Proxy acepta TLS 1.0+ y termina la conexión TLS
4. Proxy reenvía tráfico XMPP sin cifrar a Prosody (puerto 5200)
5. Prosody procesa autenticación y mensajes normalmente
6. Respuestas regresan por el mismo camino

### Configuración UFW Crítica
```bash
# INCORRECTO (causa rate limiting):
sudo ufw limit 5222/tcp

# CORRECTO (sin rate limiting):
sudo ufw allow 5222/tcp
```

**¿Por qué ALLOW y no LIMIT?**
- LIMIT bloquea más de 6 conexiones por IP en 30 segundos
- BB10 puede reconectar frecuentemente durante sincronización
- Rate limiting causa desconexiones inesperadas
- ALLOW permite conexiones ilimitadas

---

## 📞 Soporte

Si tienes problemas:
1. Revisar logs del proxy: `pm2 logs xmpp-starttls-proxy`
2. Revisar logs de Prosody: `sudo journalctl -u prosody -f`
3. Revisar que slidge esté corriendo
4. Verificar configuración UFW (debe ser ALLOW, no LIMIT)
5. Verificar conectividad de red
6. Consultar README.md y PROYECTO_COMPLETADO.md

---

**¡Todo listo para conectar desde tu BlackBerry 10!** 🚀
