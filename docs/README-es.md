# WhatsBerry - Documentación Completa

**Cliente de WhatsApp para BlackBerry 10 usando XMPP + Slidge-WhatsApp**

---

## 📚 Índice de Documentación

### Guías para Usuarios

1. **[🚀 Inicio Rápido (QUICKSTART-es.md)](QUICKSTART-es.md)**
   - Configuración completa en 15-20 minutos
   - Paso a paso desde cero hasta enviar mensajes
   - **Empieza aquí si es tu primera vez**

2. **[📱 Configuración del Cliente (CLIENT_CONFIGURATION-es.md)](CLIENT_CONFIGURATION-es.md)**
   - Cómo configurar la app en BB10
   - Crear cuenta XMPP
   - Vincular con WhatsApp
   - Solución de problemas comunes

3. **[🐛 Solución de Problemas (TROUBLESHOOTING-es.md)](TROUBLESHOOTING-es.md)**
   - Problemas comunes y soluciones
   - Debugging del proxy STARTTLS
   - Logs y diagnóstico
   - Lecciones aprendidas

### Guías para Administradores

4. **[🖥️ Configuración del Servidor (SERVER_SETUP-es.md)](SERVER_SETUP-es.md)**
   - Instalación paso a paso
   - Configuración Hybrid (Recomendada)
   - Docker Compose
   - Configuración de Prosody y Slidge

5. **[🌐 Servidor HTTP (HTTP_SERVER-es.md)](HTTP_SERVER-es.md)**
   - Configuración de Nginx
   - Servir APK y archivos adjuntos
   - Endpoints PHP para uploads
   - SSL con Let's Encrypt
   - Seguridad y monitoreo

6. **[📝 Historial de Cambios (CHANGELOG-es.md)](CHANGELOG-es.md)**
   - Versiones y novedades
   - Cambios por versión
   - Actualizaciones importantes

---

## 🎯 Arquitectura del Proyecto

```
┌─────────────────────┐
│  BlackBerry 10      │
│  Android App        │
│  (API Level 18)     │
└──────────┬──────────┘
           │ STARTTLS (TLS 1.0+)
           │ Puerto 5222
           ▼
┌─────────────────────────────┐
│ XMPP STARTTLS Proxy         │
│ - Node.js                   │
│ - Soporta TLS 1.0-1.3       │
│ - Inyecta STARTTLS          │
└──────────┬──────────────────┘
           │ Plaintext XMPP
           │ Puerto 5200
           ▼
┌─────────────────────────────┐
│ Prosody XMPP Server         │
│ - VirtualHost config        │
│ - Componente slidge         │
└──────────┬──────────────────┘
           │ Componente XMPP
           │ Puerto 5347
           ▼
┌─────────────────────────────┐
│ Slidge WhatsApp Gateway     │
│ - Docker container          │
│ - network_mode: host        │
│ - whatsmeow (Go)            │
└──────────┬──────────────────┘
           │ WhatsApp Protocol
           ▼
┌─────────────────────────────┐
│ Servidores de WhatsApp      │
└─────────────────────────────┘
```

---

## 📦 Componentes del Proyecto

### 1. Cliente Android (BlackBerry 10)

**Ubicación**: `/opt/whatsberry/app/`

**Tecnologías**:
- Android API Level 18 (4.3 Jelly Bean)
- Smack XMPP 4.1.9
- SQLite para mensajes
- Material Design adaptado

**Archivos Clave**:
- `MainActivity.java` - Login y configuración
- `MainTabsActivity.java` - Lista de chats/contactos
- `ChatActivity.java` - Ventana de chat
- `XMPPService.java` - Servicio en primer plano
- `DatabaseHelper.java` - Almacenamiento local

**Versión Actual**: v3.3.1
- Selector de ubicación de base de datos
- Credenciales configurables
- Servicio en primer plano para notificaciones
- Menú accesible desde anywhere

### 2. Proxy XMPP STARTTLS

**Ubicación**: `/opt/whatsberry/xmpp-starttls-proxy.js`

**Propósito**: Permitir que dispositivos BB10 (TLS 1.0) se conecten a Prosody moderno (TLS 1.2+)

**Características**:
- Escucha en puerto 5222
- Soporta TLS 1.0, 1.1, 1.2, 1.3
- Inyecta STARTTLS en features de Prosody
- Crea nueva conexión después del handshake TLS
- Relay bidireccional: Cliente(TLS) ↔ Proxy ↔ Prosody(plaintext)

**Gestión**:
```bash
sudo systemctl status xmpp-tls-proxy
sudo journalctl -u xmpp-tls-proxy -f
```

### 3. Prosody XMPP Server

**Ubicación**: `/etc/prosody/prosody.cfg.lua`

**Configuración**:
- Puerto c2s: 5200 (interno, sin TLS)
- Puerto componente: 5347
- VirtualHost con TLS deshabilitado
- Componente para slidge-whatsapp

**Gestión**:
```bash
sudo systemctl status prosody
sudo prosodyctl check
sudo prosodyctl adduser usuario@dominio
```

### 4. Slidge WhatsApp Gateway

**Ubicación**: Docker container + `~/.local/share/slidge/slidge.conf`

**Configuración**:
- JID: `whatsapp.localhost`
- Secret compartido con Prosody
- network_mode: host (acceso a localhost:5347)
- Almacenamiento en `/data`

**Gestión**:
```bash
docker ps | grep slidge
docker logs -f slidge-whatsapp
docker restart slidge-whatsapp
```

---

## 🚀 Opciones de Despliegue

### Opción 1: Hybrid Setup ✅ (Recomendado)

**Configuración probada en producción**

- **Prosody**: systemd service (puerto 5200)
- **Slidge**: Docker container con host network
- **Proxy**: systemd service (puerto 5222)

**Ventajas**:
- ✅ Probado y funcionando
- ✅ Mejor control de recursos
- ✅ Fácil debugging (logs separados)
- ✅ Actualizaciones independientes

**Ver**: [SERVER_SETUP-es.md - Hybrid Setup](SERVER_SETUP-es.md#hybrid-setup-tested--recommended)

### Opción 2: Docker Compose Completo ⚠️

**Todos los servicios en Docker**

- **Prosody**: Docker container
- **Slidge**: Docker container
- **Proxy**: Docker container

**Estado**: No probado

**Ver**: [`docker-compose.yml`](../docker-compose.yml)

### Opción 3: Manual Completo

**Todos los servicios con systemd**

- **Prosody**: systemd
- **Slidge**: systemd (con pipx)
- **Proxy**: systemd

**Ver**: [SERVER_SETUP-es.md - Manual Installation](SERVER_SETUP-es.md#manual-installation)

---

## 🔐 Seguridad y Privacidad

### Datos del Usuario

- ✅ **Auto-hospedable**: Controlas tus datos
- ✅ **Sin almacenamiento en servidor**: Mensajes solo en el dispositivo
- ✅ **TLS encryption**: Cliente ↔ Servidor siempre cifrado
- ✅ **Código abierto**: Transparencia total

### Consideraciones

- ⚠️ **TLS 1.0 habilitado**: Necesario para BB10, protocolo legacy
- ⚠️ **Proxy tiene acceso a tráfico**: Descifra entre cliente y Prosody
- ✅ **Mitigación**: Proxy y Prosody en mismo servidor (localhost)

---

## 📱 Versiones de la App

### v3.3.1 (8 de diciembre de 2024) - ACTUAL

**Novedades**:
- Database Settings accesible desde menú principal
- Menú: Refresh, Database Settings, Logout

### v3.3.0 (8 de diciembre de 2024)

**Novedades**:
- Selector de ubicación de base de datos (4 opciones)
- Auto-detección de BlackBerry 10
- Migración segura entre ubicaciones
- Diálogo de información de base de datos

### v3.2.0 (8 de diciembre de 2024)

**Novedades**:
- Credenciales XMPP configurables por usuario
- Campos de servidor visibles en login
- Auto-login solo si hay credenciales guardadas
- Soporte para self-hosting

### v3.1.9 (8 de diciembre de 2024)

**CRÍTICO**:
- **Foreground Service** para XMPP
- Notificaciones funcionando en BB10 Hub
- Previene que BB10 mate la app

**Ver historial completo**: [CHANGELOG-es.md](CHANGELOG-es.md)

---

## 🛠️ Stack Tecnológico

### Cliente (Android/BB10)

| Componente | Tecnología |
|------------|------------|
| Lenguaje | Java (Android SDK) |
| XMPP Library | Smack 4.1.9 |
| Base de Datos | SQLite |
| UI | Android XML Layouts |
| Min SDK | 18 (Android 4.3) |
| Target SDK | 18 |

### Servidor

| Componente | Tecnología |
|------------|------------|
| Proxy STARTTLS | Node.js 16+ |
| XMPP Server | Prosody 0.12+ |
| WhatsApp Gateway | Slidge (Python) |
| WhatsApp Protocol | whatsmeow (Go) |
| Containerización | Docker |

---

## 📂 Estructura del Repositorio

```
/opt/whatsberry/
├── app/                          # Código fuente Android
│   ├── src/main/java/com/whatsberry/xmpp/
│   │   ├── MainActivity.java
│   │   ├── MainTabsActivity.java
│   │   ├── ChatActivity.java
│   │   ├── XMPPService.java
│   │   ├── DatabaseHelper.java
│   │   └── ...
│   ├── src/main/res/             # Recursos UI
│   └── build.gradle
│
├── docs/                         # Documentación
│   ├── README-es.md              # Este archivo
│   ├── QUICKSTART-es.md
│   ├── SERVER_SETUP-es.md
│   ├── CLIENT_CONFIGURATION-es.md
│   ├── TROUBLESHOOTING-es.md
│   └── CHANGELOG-es.md
│
├── prosody-config/
│   └── prosody.cfg.lua           # Ejemplo config Prosody
│
├── xmpp-starttls-proxy.js        # Proxy STARTTLS
├── docker-compose.yml            # Full stack (no probado)
├── docker-compose-hybrid.yml     # Hybrid setup (recomendado)
├── .env.example                  # Plantilla variables
│
├── public/                       # APKs publicadas
│   └── whatsberry-v3.3.1.apk
│
├── build.gradle                  # Configuración build
└── README.md                     # README principal (bilingüe)
```

---

## 🤝 Contribuciones

### Cómo Contribuir

1. Fork del repositorio
2. Crea una rama para tu feature
3. Realiza tus cambios
4. Envía un Pull Request con descripción detallada

### Áreas de Contribución

- 🐛 **Bug fixes** - Reporta o fix bugs
- 📝 **Documentación** - Mejora las guías
- 🌐 **Traducciones** - Añade más idiomas
- ✨ **Features** - Nuevas funcionalidades
- 🧪 **Testing** - Pruebas en diferentes dispositivos

---

## 📄 Licencia

MIT License

Copyright (c) 2024 WhatsBerry Project

Ver [LICENSE](../LICENSE) para detalles completos.

---

## 🆘 Soporte

### Recursos de Ayuda

1. **Documentación**:
   - [Inicio Rápido](QUICKSTART-es.md) - Primeros pasos
   - [Solución de Problemas](TROUBLESHOOTING-es.md) - Problemas comunes
   - [Configuración de Servidor](SERVER_SETUP-es.md) - Setup avanzado

2. **Logs y Debugging**:
   ```bash
   # Proxy STARTTLS
   sudo journalctl -u xmpp-tls-proxy -f

   # Prosody
   sudo journalctl -u prosody -f

   # Slidge
   docker logs -f slidge-whatsapp
   ```

3. **Comunidad**:
   - GitHub Issues: [Reportar problema](https://github.com/yourusername/whatsberry/issues)
   - Discussions: [Hacer preguntas](https://github.com/yourusername/whatsberry/discussions)

---

## 🙏 Agradecimientos

- **Prosody** - Servidor XMPP robusto
- **Slidge** - Gateway XMPP ↔ WhatsApp
- **whatsmeow** - Librería de protocolo WhatsApp
- **Smack** - Librería XMPP para Java
- **Comunidad BB10** - Por mantener vivos estos dispositivos

---

**Construido con ❤️ para usuarios de BlackBerry 10**

[⬆️ Volver arriba](#whatsberry---documentación-completa)
