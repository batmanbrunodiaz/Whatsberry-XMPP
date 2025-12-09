# 🚀 Guía de Inicio Rápido - WhatsBerry

**Tiempo estimado: 15-20 minutos**

Esta guía te llevará desde cero hasta tener WhatsApp funcionando en tu BlackBerry 10.

---

## 📋 Requisitos Previos

### Para el Servidor
- Un servidor Linux (Ubuntu 22.04+, Arch Linux, o Debian)
- 1GB RAM mínimo (2GB recomendado)
- Dominio o IP pública
- Puertos 5222, 80, 443 abiertos en el firewall

### Para el Cliente
- BlackBerry 10 device (BlackBerry Q10, Z10, Z30, Passport, Classic, etc.)
- Android Runtime habilitado
- Acceso a Internet (WiFi o datos móviles)

---

## 🖥️ Paso 1: Configurar el Servidor

### Opción A: Usando Docker (Recomendado - Solo Slidge)

```bash
# 1. Instalar Prosody
sudo apt update && sudo apt install prosody

# 2. Configurar Prosody
sudo nano /etc/prosody/prosody.cfg.lua
```

Copia el contenido de [`prosody-config/prosody.cfg.lua`](../prosody-config/prosody.cfg.lua) y cambia:
- `whatsberry.descarga.media` → tu dominio
- `component_secret` → genera uno con `openssl rand -base64 32`

```bash
# 3. Reiniciar Prosody
sudo systemctl restart prosody
sudo systemctl enable prosody

# 4. Instalar Node.js para el proxy
sudo apt install nodejs npm

# 5. Copiar el proxy STARTTLS
sudo mkdir -p /opt/whatsberry
sudo cp xmpp-starttls-proxy.js /opt/whatsberry/
```

Crea el servicio systemd para el proxy (`/etc/systemd/system/xmpp-tls-proxy.service`):

```ini
[Unit]
Description=XMPP STARTTLS Proxy for BlackBerry 10
After=network.target prosody.service
Requires=prosody.service

[Service]
Type=simple
User=tu-usuario
WorkingDirectory=/opt/whatsberry
ExecStart=/usr/bin/node /opt/whatsberry/xmpp-starttls-proxy.js
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
# 6. Iniciar el proxy
sudo systemctl daemon-reload
sudo systemctl start xmpp-tls-proxy
sudo systemctl enable xmpp-tls-proxy

# 7. Ejecutar Slidge WhatsApp con Docker
mkdir -p ~/.local/share/slidge
```

Crea `~/.local/share/slidge/slidge.conf`:

```ini
[global]
jid = whatsapp.localhost
secret = TU_SECRET_AQUI  # El mismo que pusiste en Prosody
server = localhost
port = 5347
home-dir = /data
debug = true

user-jid-validator = ^.*@(localhost|tu-dominio\.com)(/.*)?$
```

```bash
# 8. Iniciar Slidge con Docker
docker run -d \
  --name slidge-whatsapp \
  --network host \
  --restart unless-stopped \
  -v ~/.local/share/slidge:/data \
  --log-opt max-size=10m \
  --log-opt max-file=5 \
  codeberg.org/slidge/slidge-whatsapp:latest-amd64 \
  -c /data/slidge.conf

# 9. Configurar firewall
sudo ufw allow 5222/tcp
sudo ufw enable
```

### Opción B: Docker Compose Completo (No Probado)

Ver [`docker-compose.yml`](../docker-compose.yml) para configuración completa.

**⚠️ NOTA**: Esta opción no ha sido probada. Recomendamos la Opción A (Hybrid Setup).

---

## 📱 Paso 2: Instalar la App en BlackBerry 10

### Método 1: Descarga Directa (Más Fácil)

1. En tu BlackBerry 10, abre el navegador
2. Ve a: `https://tu-dominio.com/whatsberry-v3.3.1.apk`
3. Descarga el APK
4. Cuando termine, toca "Abrir" para instalar
5. Acepta los permisos

### Método 2: ADB (Desde PC)

```bash
# Conecta tu BB10 por USB
adb devices

# Instala el APK
adb install -r whatsberry-v3.3.1.apk
```

---

## 🔐 Paso 3: Configurar la App

1. **Abre WhatsBerry** en tu BlackBerry 10

2. **Introduce los datos del servidor**:
   ```
   Server: tu-dominio.com
   Port: 5222
   Domain: tu-dominio.com
   Username: tunombre
   Password: tupassword
   Gateway: whatsapp.localhost
   ```

3. **Registra una nueva cuenta**:
   - Toca "Register New Account" (primera vez)
   - O "Connect & Login" si ya tienes una cuenta

4. **Espera el QR Code**:
   - La app se conectará al servidor
   - En unos segundos aparecerá un código QR

---

## 📲 Paso 4: Vincular WhatsApp

1. **Abre WhatsApp** en tu teléfono principal

2. **Ve a Dispositivos Vinculados**:
   - Android: Menú → Dispositivos vinculados
   - iPhone: Configuración → Dispositivos vinculados

3. **Escanea el QR**:
   - Toca "Vincular un dispositivo"
   - Apunta la cámara al QR en tu BlackBerry 10
   - Espera la confirmación

4. **¡Listo!**:
   - La app mostrará "Connected"
   - Verás tus contactos de WhatsApp
   - Ya puedes enviar y recibir mensajes

---

## ✅ Verificación

### Comprobar que todo funciona:

1. **En el servidor**:
   ```bash
   # Verificar que todos los servicios están corriendo
   sudo systemctl status prosody
   sudo systemctl status xmpp-tls-proxy
   docker ps | grep slidge

   # Ver logs en tiempo real
   sudo journalctl -u xmpp-tls-proxy -f
   ```

2. **En la app**:
   - Deberías ver tus contactos de WhatsApp
   - Prueba enviar un mensaje a ti mismo
   - Verifica que llega al teléfono principal

---

## 🐛 Problemas Comunes

### "Connection failed"

**Solución**:
```bash
# Verificar que el proxy está corriendo
sudo systemctl status xmpp-tls-proxy

# Verificar que el puerto está abierto
sudo ufw status | grep 5222

# Ver logs para errores
sudo journalctl -u xmpp-tls-proxy -n 50
```

### "Authentication failed"

**Causas comunes**:
- Usuario no existe → Usar "Register New Account"
- Contraseña incorrecta → Verificar credenciales
- Domain incorrecto → Debe coincidir con tu Prosody VirtualHost

### QR Code no aparece

**Solución**:
```bash
# Verificar que Slidge está corriendo
docker logs slidge-whatsapp

# Reiniciar Slidge
docker restart slidge-whatsapp
```

### La app se desconecta constantemente

**Solución**:
- Asegúrate de usar WhatsBerry v3.1.9+ (tiene Foreground Service)
- Verifica configuración de batería en BB10
- Comprueba que el firewall no tenga regla LIMIT (debe ser ALLOW):
  ```bash
  sudo ufw delete limit 5222/tcp
  sudo ufw allow 5222/tcp
  ```

---

## 📚 Próximos Pasos

Ahora que tienes WhatsBerry funcionando:

- **[Configuración Avanzada](SERVER_SETUP-es.md)** - Opciones avanzadas del servidor
- **[Solución de Problemas](TROUBLESHOOTING-es.md)** - Guía completa de troubleshooting
- **[Configuración del Cliente](CLIENT_CONFIGURATION-es.md)** - Opciones avanzadas de la app
- **[Historial de Cambios](CHANGELOG-es.md)** - Novedades de cada versión

---

## 🆘 ¿Necesitas Ayuda?

Si tienes problemas:

1. **Revisa los logs**:
   ```bash
   sudo journalctl -u xmpp-tls-proxy -f
   sudo journalctl -u prosody -f
   docker logs -f slidge-whatsapp
   ```

2. **Consulta la documentación completa**:
   - [Solución de Problemas Detallada](TROUBLESHOOTING-es.md)
   - [Documentación Técnica](../TECHNICAL.md)

3. **Reporta un problema**:
   - GitHub Issues: [whatsberry/issues](https://github.com/yourusername/whatsberry/issues)

---

**¡Disfruta de WhatsApp en tu BlackBerry 10!** 🎉
