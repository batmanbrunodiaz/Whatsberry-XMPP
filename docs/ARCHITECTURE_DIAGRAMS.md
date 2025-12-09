# Diagramas de Arquitectura - WhatsBerry

Diagramas técnicos y de flujo para entender el funcionamiento completo del proyecto.

---

## 📱 Flujo de Usuario en la App (BlackBerry 10)

### Primera Vez - Registro y Vinculación

```
┌─────────────────────────────────────────────────────────────┐
│                    PRIMERA VEZ - FLUJO                      │
└─────────────────────────────────────────────────────────────┘

1. Usuario abre app
   │
   ├─> [MainActivity.java]
   │   ├─ Muestra campos de configuración
   │   │  ├─ Server: whatsberry.descarga.media
   │   │  ├─ Port: 5222
   │   │  ├─ Domain: whatsberry.descarga.media
   │   │  ├─ Username: tunombre
   │   │  ├─ Password: tupassword
   │   │  └─ Gateway: whatsapp.localhost
   │   │
   │   └─ Usuario click "Register New Account"
   │
   ├─> [XMPPManager.java]
   │   ├─ Conecta a servidor XMPP (puerto 5222)
   │   ├─ Negocia STARTTLS con proxy
   │   ├─ Registra cuenta en Prosody
   │   └─ Login automático
   │
   ├─> [WhatsAppManager.java]
   │   ├─ Ejecuta comando ad-hoc "register"
   │   ├─ Gateway crea sesión
   │   ├─ Ejecuta comando ad-hoc "PairPhone"
   │   └─ Recibe QR code (base64)
   │
   ├─> [MainActivity.java]
   │   ├─ Muestra QR code en ImageView
   │   └─ Espera vinculación
   │
   ├─> Usuario escanea QR con WhatsApp oficial
   │
   ├─> [WhatsAppManager.java]
   │   ├─ Gateway detecta vinculación exitosa
   │   ├─ Descarga roster (contactos)
   │   └─ Envía presence (disponible)
   │
   ├─> [XMPPService.java]
   │   ├─ Guarda credenciales en SharedPreferences
   │   ├─ Inicia Foreground Service
   │   └─ Mantiene conexión activa
   │
   └─> [MainTabsActivity.java]
       ├─ Carga contactos de WhatsApp
       ├─ Carga chats recientes
       └─ ¡Listo para chatear!
```

---

## 🔄 Flujo de Usuario Existente - Auto-Login

```
┌─────────────────────────────────────────────────────────────┐
│                 USUARIO EXISTENTE - FLUJO                   │
└─────────────────────────────────────────────────────────────┘

1. Usuario abre app
   │
   ├─> [MainActivity.java]
   │   ├─ Lee credenciales de SharedPreferences
   │   ├─ Encuentra credenciales guardadas
   │   └─ Inicia XMPPService automáticamente
   │
   ├─> [XMPPService.java]
   │   ├─ autoLogin() ejecutado
   │   ├─ Conecta a servidor (puerto 5222)
   │   ├─ Negocia STARTTLS
   │   ├─ Login con credenciales guardadas
   │   └─ Inicia como Foreground Service
   │
   ├─> [MainActivity.java] - Skip automático
   │   └─ Intent → MainTabsActivity
   │
   ├─> [MainTabsActivity.java]
   │   ├─ Carga contactos (desde DB local + XMPP)
   │   ├─ Carga chats recientes (desde DB local)
   │   └─ Sincroniza con servidor
   │
   └─> Usuario ve sus chats y puede chatear inmediatamente
```

---

## 💬 Flujo de Envío de Mensaje

```
┌─────────────────────────────────────────────────────────────┐
│                   ENVIAR MENSAJE - FLUJO                    │
└─────────────────────────────────────────────────────────────┘

1. Usuario en ChatActivity
   │
   ├─> Usuario escribe mensaje en EditText
   ├─> Usuario presiona "Send"
   │
   ├─> [ChatActivity.java]
   │   ├─ Captura texto del mensaje
   │   ├─ Obtiene JID del contacto (ej: +1234567890@whatsapp.localhost)
   │   └─ Llama a sendMessage()
   │
   ├─> [XMPPManager.java]
   │   ├─ Crea objeto Message
   │   ├─ message.setTo(contactJid)
   │   ├─ message.setBody(textoMensaje)
   │   ├─ message.setFrom(miJid)
   │   └─ connection.sendStanza(message)
   │
   ├─> [Red XMPP]
   │   Cliente → Proxy STARTTLS (TLS encriptado)
   │            │
   │            ├─> Proxy → Prosody (plaintext localhost)
   │                       │
   │                       ├─> Prosody → Slidge (componente)
   │                                    │
   │                                    ├─> Slidge → WhatsApp Servers
   │
   ├─> [DatabaseHelper.java]
   │   ├─ Guarda mensaje en DB local
   │   ├─ Estado: "enviando"
   │   └─ Timestamp actual
   │
   ├─> [ChatActivity.java]
   │   ├─ Agrega mensaje a ListView
   │   ├─ Scroll automático al final
   │   └─ Limpia EditText
   │
   └─> Confirmación desde WhatsApp (ack)
       │
       ├─> [XMPPManager.java] - MessageListener
       │   └─ Recibe confirmación de entrega
       │
       └─> [DatabaseHelper.java]
           ├─ Actualiza estado del mensaje
           └─ Estado: "entregado" o "leído"
```

---

## 📥 Flujo de Recepción de Mensaje

```
┌─────────────────────────────────────────────────────────────┐
│                   RECIBIR MENSAJE - FLUJO                   │
└─────────────────────────────────────────────────────────────┘

1. WhatsApp Server envía mensaje
   │
   ├─> Slidge Gateway recibe del servidor WhatsApp
   ├─> Slidge convierte a formato XMPP
   ├─> Slidge envía stanza a Prosody
   ├─> Prosody enruta a usuario conectado
   ├─> Proxy STARTTLS reenvía (encriptado)
   │
   ├─> [XMPPService.java] - MessageListener
   │   ├─ onMessageReceived() disparado
   │   ├─ Extrae: from, body, timestamp
   │   └─ Procesa mensaje
   │
   ├─> [DatabaseHelper.java]
   │   ├─ Guarda mensaje en DB local
   │   │  ├─ chat_id
   │   │  ├─ from_jid
   │   │  ├─ body
   │   │  ├─ timestamp
   │   │  └─ is_read = 0 (no leído)
   │   └─ Retorna messageId
   │
   ├─> [Notificación]
   │   │
   │   ├─ Si app en background:
   │   │  ├─> NotificationManager
   │   │  ├─> Crea notificación
   │   │  ├─> Muestra en BB10 Hub ✅
   │   │  └─> Sonido/vibración
   │   │
   │   └─ Si app en foreground:
   │       └─> Solo actualiza UI
   │
   ├─> [MainTabsActivity.java]
   │   ├─ Si está visible:
   │   │  ├─> Actualiza lista de chats
   │   │  ├─> Mueve chat al tope
   │   │  └─> Incrementa contador no leídos
   │   │
   │   └─ Si no está visible:
   │       └─> Actualiza en background
   │
   └─> [ChatActivity.java]
       └─ Si chat está abierto:
          ├─> Agrega mensaje a ListView
          ├─> Scroll automático al final
          └─> markMessagesAsRead()
              └─> DB: is_read = 1
```

---

## 🔌 Arquitectura de Conexión XMPP Detallada

```
┌──────────────────────────────────────────────────────────────────┐
│                    ARQUITECTURA DE RED                           │
└──────────────────────────────────────────────────────────────────┘

BlackBerry 10 Device (10.1.1.2 - red local)
  │
  │ [Smack XMPP 4.1.9 Library]
  │ - XMPPTCPConnection
  │ - ConnectionConfiguration
  │ - SecurityMode: required (STARTTLS)
  │
  ├─ TCP Socket abierto a whatsberry.descarga.media:5222
  │
  ▼
┌─────────────────────────────────────────┐
│  XMPP STARTTLS Proxy (Node.js)          │
│  - IP: 0.0.0.0 (escucha en todas)       │
│  - Port: 5222                           │
│  - TLS Versions: 1.0, 1.1, 1.2, 1.3    │
│  - Certs: /etc/prosody/certs/           │
└─────────────────────────────────────────┘
  │
  │ FASE 1: Conexión inicial (plaintext)
  │ ├─> Cliente: <stream:stream>
  │ ├─> Proxy → Prosody: forward
  │ ├─> Prosody → Proxy: <stream:features>
  │ └─> Proxy → Cliente: <stream:features> + <starttls/> INYECTADO
  │
  │ FASE 2: STARTTLS Negotiation
  │ ├─> Cliente → Proxy: <starttls/>
  │ ├─> Proxy destruye conexión vieja a Prosody
  │ ├─> Proxy → Cliente: <proceed/>
  │ └─> TLS Handshake (TLS 1.0 negociado)
  │
  │ FASE 3: Post-TLS (nueva conexión)
  │ ├─> Proxy crea NUEVA conexión TCP a Prosody:5200
  │ ├─> Cliente → Proxy: datos encriptados (TLS)
  │ ├─> Proxy → Prosody: datos plaintext (localhost)
  │ └─> Relay bidireccional establecido ✅
  │
  ├─ Plaintext connection a localhost:5200
  │
  ▼
┌─────────────────────────────────────────┐
│  Prosody XMPP Server                    │
│  - IP: 127.0.0.1 (localhost only)       │
│  - c2s Port: 5200 (NO TLS)              │
│  - Component Port: 5347                 │
│  - VirtualHost: whatsberry.descarga...  │
│  - modules_disabled = { "tls" }         │
└─────────────────────────────────────────┘
  │
  │ XMPP Stanzas:
  │ - <iq> (info queries - ad-hoc commands)
  │ - <message> (chat messages)
  │ - <presence> (online status)
  │
  ├─ Component connection (XEP-0114)
  │
  ▼
┌─────────────────────────────────────────┐
│  Slidge WhatsApp Gateway                │
│  - Docker container                     │
│  - network_mode: host                   │
│  - JID: whatsapp.localhost              │
│  - Secret: compartido con Prosody       │
│  - Port: 5347 (localhost)               │
│  - Config: ~/.local/share/slidge/       │
└─────────────────────────────────────────┘
  │
  │ [whatsmeow Library - Go]
  │ - WebSocket a WhatsApp
  │ - Protobuf messages
  │ - E2E encryption (Signal protocol)
  │
  ├─ WebSocket/HTTPS
  │
  ▼
┌─────────────────────────────────────────┐
│  WhatsApp Servers                       │
│  - web.whatsapp.com                     │
│  - *.whatsapp.net                       │
└─────────────────────────────────────────┘
```

---

## 🗂️ Estructura de Clases de la App Android

```
┌──────────────────────────────────────────────────────────────┐
│                   CLASES PRINCIPALES                         │
└──────────────────────────────────────────────────────────────┘

MainActivity
├─ Responsabilidades:
│  ├─ Login screen
│  ├─ Registro de cuenta XMPP
│  ├─ Configuración de servidor
│  ├─ Database settings dialog
│  └─ Mostrar QR code para vinculación
│
├─ Campos:
│  ├─ EditText: etServer, etPort, etDomain
│  ├─ EditText: etUsername, etPassword, etGateway
│  ├─ ImageView: ivQrCode
│  └─ Buttons: btnLogin, btnRegister, btnDatabaseSettings
│
└─ Métodos clave:
   ├─ loadSavedSettings() - Lee SharedPreferences
   ├─ saveSettings() - Guarda configuración
   ├─ showDatabaseSettings() - Dialogo ubicación DB
   └─ startMainActivity() - Intent a MainTabsActivity

─────────────────────────────────────────────────────────────

MainTabsActivity
├─ Responsabilidades:
│  ├─ Lista de chats recientes
│  ├─ Lista de contactos
│  ├─ Menú de opciones
│  └─ Navegación principal
│
├─ Componentes UI:
│  ├─ ListView: lvChats
│  ├─ ListView: lvContacts
│  ├─ Buttons: btnChats, btnContacts, btnMenu
│  └─ PopupMenu: Refresh, Database Settings, Logout
│
└─ Métodos clave:
   ├─ loadChats() - Carga desde DB
   ├─ loadContacts() - Carga desde DB + XMPP
   ├─ showOptionsMenu() - Muestra menú
   └─ logout() - Cierra sesión

─────────────────────────────────────────────────────────────

ChatActivity
├─ Responsabilidades:
│  ├─ Mostrar conversación con contacto
│  ├─ Enviar mensajes
│  ├─ Recibir mensajes en tiempo real
│  └─ Marcar como leído
│
├─ Componentes UI:
│  ├─ ListView: lvMessages
│  ├─ EditText: etMessage
│  ├─ Button: btnSend
│  └─ MessageAdapter (custom)
│
└─ Métodos clave:
   ├─ loadMessages() - Carga desde DB
   ├─ sendMessage() - Envía vía XMPP
   ├─ onNewMessage() - BroadcastReceiver
   └─ markMessagesAsRead() - Actualiza DB

─────────────────────────────────────────────────────────────

XMPPService (extends Service)
├─ Responsabilidades:
│  ├─ Mantener conexión XMPP activa
│  ├─ Foreground service (evita que BB10 lo mate)
│  ├─ Escuchar mensajes entrantes
│  └─ Sincronizar estado
│
├─ Componentes:
│  ├─ XMPPManager: gestión de conexión
│  ├─ MessageListener: recibe mensajes
│  ├─ PresenceListener: estado de contactos
│  └─ NotificationManager: notificaciones
│
└─ Métodos clave:
   ├─ onCreate() - Inicia foreground service
   ├─ autoLogin() - Login automático
   ├─ onMessageReceived() - Procesa mensaje
   └─ sendLocalBroadcast() - Notifica UI

─────────────────────────────────────────────────────────────

XMPPManager
├─ Responsabilidades:
│  ├─ Gestionar conexión XMPP (Smack)
│  ├─ Autenticación
│  ├─ Envío de stanzas
│  └─ Registro de listeners
│
├─ Objetos Smack:
│  ├─ XMPPTCPConnection
│  ├─ XMPPTCPConnectionConfiguration
│  ├─ ReconnectionManager
│  └─ ChatManager
│
└─ Métodos clave:
   ├─ connect() - Conecta al servidor
   ├─ login() - Autenticación SASL
   ├─ register() - Registra cuenta nueva
   ├─ sendMessage() - Envía mensaje
   └─ addMessageListener() - Escucha mensajes

─────────────────────────────────────────────────────────────

DatabaseHelper (extends SQLiteOpenHelper)
├─ Responsabilidades:
│  ├─ Crear y migrar esquema de DB
│  ├─ CRUD de mensajes
│  ├─ CRUD de chats
│  └─ Gestión de ubicación de DB
│
├─ Tablas:
│  ├─ messages (id, chat_id, from_jid, body, timestamp, is_read)
│  ├─ chats (id, jid, name, last_message, unread_count)
│  └─ contacts (id, jid, name, phone)
│
└─ Métodos clave:
   ├─ insertMessage() - Guardar mensaje
   ├─ getMessages() - Obtener mensajes de chat
   ├─ markMessagesAsRead() - Marcar como leído
   ├─ getDatabasePath() - Ubicación de DB
   └─ migrateDatabase() - Mover DB entre ubicaciones
```

---

## 📊 Diagrama de Secuencia: Primera Conexión

```
Usuario    MainActivity    XMPPManager    Proxy STARTTLS    Prosody    Slidge    WhatsApp
  │              │              │                │             │          │          │
  │ Abre app     │              │                │             │          │          │
  ├─────────────>│              │                │             │          │          │
  │              │              │                │             │          │          │
  │ Ingresa      │              │                │             │          │          │
  │ credenciales │              │                │             │          │          │
  ├─────────────>│              │                │             │          │          │
  │              │              │                │             │          │          │
  │ Click        │              │                │             │          │          │
  │ "Register"   │              │                │             │          │          │
  ├─────────────>│ connect()    │                │             │          │          │
  │              ├─────────────>│                │             │          │          │
  │              │              │ TCP SYN        │             │          │          │
  │              │              ├───────────────>│             │          │          │
  │              │              │                │ TCP to 5200 │          │          │
  │              │              │                ├────────────>│          │          │
  │              │              │                │             │          │          │
  │              │              │ <stream>       │             │          │          │
  │              │              ├───────────────>│─────────────>          │          │
  │              │              │                │<────────────│          │          │
  │              │              │<───────────────│ features    │          │          │
  │              │              │ + STARTTLS     │             │          │          │
  │              │              │                │             │          │          │
  │              │              │ <starttls/>    │             │          │          │
  │              │              ├───────────────>│             │          │          │
  │              │              │                │ [Destroy    │          │          │
  │              │              │                │  old conn]  │          │          │
  │              │              │<───────────────│ <proceed/>  │          │          │
  │              │              │                │             │          │          │
  │              │              │ [TLS 1.0       │             │          │          │
  │              │              │  Handshake]    │             │          │          │
  │              │              │<──────────────>│             │          │          │
  │              │              │ ✅ Encrypted   │             │          │          │
  │              │              │                │             │          │          │
  │              │              │                │ [New TCP    │          │          │
  │              │              │                │  to 5200]   │          │          │
  │              │              │                ├────────────>│          │          │
  │              │              │                │             │          │          │
  │              │ register()   │ (encriptado)   │ (plaintext) │          │          │
  │              ├─────────────>├───────────────>├────────────>│          │          │
  │              │              │                │             │ [Crea    │          │
  │              │              │                │             │  cuenta] │          │
  │              │              │                │             │<─────────│          │
  │              │<─────────────│<───────────────│<────────────│ Success  │          │
  │              │              │                │             │          │          │
  │ "Connected!" │              │                │             │          │          │
  │<─────────────│              │                │             │          │          │
  │              │              │                │             │          │          │
  │              │ PairPhone    │                │             │          │          │
  │              │ (ad-hoc cmd) │                │             │          │          │
  │              ├─────────────>├───────────────>├────────────>├─────────>│          │
  │              │              │                │             │          │ Start    │
  │              │              │                │             │          │ pairing  │
  │              │              │                │             │          ├─────────>│
  │              │              │                │             │          │<─────────│
  │              │              │                │             │          │ QR data  │
  │              │              │                │             │<─────────│          │
  │              │<─────────────│<───────────────│<────────────│ QR (b64) │          │
  │              │              │                │             │          │          │
  │ [Muestra QR] │              │                │             │          │          │
  │<─────────────│              │                │             │          │          │
  │              │              │                │             │          │          │
  │ Escanea QR   │              │                │             │          │          │
  │ con WhatsApp │              │                │             │          │          │
  ├──────────────┼──────────────┼────────────────┼─────────────┼──────────┼─────────>│
  │              │              │                │             │          │<─────────│
  │              │              │                │             │          │ Paired!  │
  │              │              │                │             │<─────────│          │
  │              │<─────────────│<───────────────│<────────────│ Success  │          │
  │              │              │                │             │          │          │
  │ "¡Vinculado!"│              │                │             │          │          │
  │<─────────────│              │                │             │          │          │
  │              │              │                │             │          │          │
  │ → MainTabs   │              │                │             │          │          │
  │              │              │                │             │          │          │
```

---

## 🔐 Base de Datos - Esquema Actual (v4)

```sql
-- Tabla: messages
CREATE TABLE messages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    chat_id TEXT NOT NULL,              -- JID del chat (ej: +1234@whatsapp.localhost)
    from_jid TEXT NOT NULL,             -- JID del remitente
    to_jid TEXT,                        -- JID del destinatario
    body TEXT,                          -- Contenido del mensaje
    timestamp INTEGER NOT NULL,         -- Unix timestamp
    is_from_me INTEGER DEFAULT 0,      -- 1 si es mensaje propio, 0 si es recibido
    is_read INTEGER DEFAULT 0,          -- 0 = no leído, 1 = leído
    message_id TEXT,                    -- ID único del mensaje (opcional)
    has_media INTEGER DEFAULT 0         -- 1 si tiene media, 0 si es solo texto
);

-- Tabla: chats
CREATE TABLE chats (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    jid TEXT UNIQUE NOT NULL,           -- JID del chat
    name TEXT,                          -- Nombre del contacto/grupo
    last_message TEXT,                  -- Preview del último mensaje
    last_message_timestamp INTEGER,     -- Timestamp del último mensaje
    unread_count INTEGER DEFAULT 0      -- Cantidad de mensajes no leídos
);

-- Tabla: contacts
CREATE TABLE contacts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    jid TEXT UNIQUE NOT NULL,           -- JID del contacto
    name TEXT,                          -- Nombre del contacto
    phone TEXT,                         -- Número de teléfono
    is_my_contact INTEGER DEFAULT 1     -- 1 si está en mi lista, 0 si no
);

-- Índices para performance
CREATE INDEX idx_messages_chat_id ON messages(chat_id);
CREATE INDEX idx_messages_timestamp ON messages(timestamp DESC);
CREATE INDEX idx_messages_is_read ON messages(is_read);
CREATE INDEX idx_chats_timestamp ON chats(last_message_timestamp DESC);
```

**Ubicaciones de la DB** (configurables desde v3.3.0):

1. **Internal Storage** (default Android):
   - Path: `/data/data/com.whatsberry.xmpp/databases/whatsberry.db`
   - Secure: ✅ Borrada al desinstalar
   - Accessible: ❌ No desde file manager

2. **External Standard**:
   - Path: `/sdcard/Whatsberry/whatsberry.db`
   - Secure: ⚠️ Persiste al desinstalar
   - Accessible: ✅ Desde file manager

3. **BB10 External SD**:
   - Path: `/mnt/sdcard/external_sd/Whatsberry/whatsberry.db`
   - Secure: ⚠️ En SD card física
   - Accessible: ✅ Removible

4. **Custom Path**:
   - Path: Definido por usuario
   - Secure: Depende de la ubicación
   - Accessible: Depende de la ubicación

---

## 🔔 Sistema de Notificaciones (v3.1.9+)

```
┌──────────────────────────────────────────────────────────────┐
│         SISTEMA DE NOTIFICACIONES - BB10 HUB                │
└──────────────────────────────────────────────────────────────┘

Mensaje entrante
  │
  ├─> [XMPPService.java] - onMessageReceived()
  │   │
  │   ├─ Guardar en DatabaseHelper
  │   │
  │   ├─ Verificar estado de app:
  │   │  │
  │   │  ├─ Si ChatActivity está abierta con este chat:
  │   │  │  ├─> NO mostrar notificación
  │   │  │  ├─> Actualizar ListView directamente
  │   │  │  └─> markMessagesAsRead() automático
  │   │  │
  │   │  └─ Si app en background o chat diferente:
  │   │     │
  │   │     └─> Crear notificación ▼
  │   │
  │   └─> createNotification()
  │       │
  │       ├─ NotificationCompat.Builder
  │       │  ├─ setSmallIcon(R.drawable.ic_notification)
  │       │  ├─ setContentTitle("Nombre del contacto")
  │       │  ├─ setContentText("Preview del mensaje")
  │       │  ├─ setTicker("Nuevo mensaje")  ← Importante para BB10
  │       │  ├─ setDefaults(Notification.DEFAULT_ALL)
  │       │  │  ├─> DEFAULT_SOUND
  │       │  │  ├─> DEFAULT_VIBRATE
  │       │  │  └─> DEFAULT_LIGHTS
  │       │  └─ setContentIntent(openChatIntent)
  │       │
  │       └─ NotificationManager.notify(notificationId, notification)
  │          │
  │          └─> ✅ Aparece en BlackBerry Hub
  │              ├─ Sonido configurable
  │              ├─ Vibración
  │              ├─ LED notification
  │              └─ Preview del mensaje
```

**Foreground Service** (CRÍTICO para BB10):

```java
// XMPPService.java - onCreate()

// Crear notificación persistente
Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
    .setContentTitle("WhatsBerry")
    .setContentText("Conectado")
    .setSmallIcon(R.drawable.ic_notification)
    .setPriority(NotificationCompat.PRIORITY_LOW)
    .build();

// Iniciar como Foreground Service
startForeground(FOREGROUND_NOTIFICATION_ID, notification);
```

**Por qué es crítico**:
- BB10 mata servicios en background agresivamente
- Foreground Service tiene prioridad alta
- Garantiza que XMPPService sigue corriendo
- Permite recibir mensajes en tiempo real

---

## 🎨 Conclusión

Estos diagramas cubren:

1. ✅ **Flujo de usuario** - Primera vez y usuarios existentes
2. ✅ **Envío/recepción de mensajes** - Flujo completo
3. ✅ **Arquitectura de red** - Detalle técnico de conexiones
4. ✅ **Estructura de clases** - Organización del código
5. ✅ **Diagrama de secuencia** - Primera conexión paso a paso
6. ✅ **Esquema de base de datos** - Estructura y ubicaciones
7. ✅ **Sistema de notificaciones** - Cómo funcionan en BB10

---

**Archivo**: `docs/ARCHITECTURE_DIAGRAMS.md`
**Versión**: 1.0
**Fecha**: Diciembre 8, 2024
