<div align="center">

# <img src="public/img/apple-touch-icon.png" alt="WhatsBerry Logo" width="50" align="center"/> WhatsBerry - Documentación Técnica

Referencia técnica completa para desarrolladores, contribuyentes y revisores de código.

[![Discord](https://img.shields.io/badge/Discord-Join%20Community-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/MtU7JqrEnW) [![Buy Me A Coffee](https://img.shields.io/badge/Buy%20Me%20A%20Coffee-Support%20Dev-FFDD00?style=for-the-badge&logo=buy-me-a-coffee&logoColor=white)](https://buymeacoffee.com/danzkigg)

</div>

---

## Tabla de Contenidos

1. [Descripción General de la Arquitectura](#descripción-general-de-la-arquitectura)
2. [Arquitectura del Proxy XMPP STARTTLS](#arquitectura-del-proxy-xmpp-starttls)
3. [Stack Tecnológico](#stack-tecnológico)
4. [Autenticación y Seguridad](#autenticación-y-seguridad)
5. [Referencia de API](#referencia-de-api)
6. [Eventos WebSocket](#eventos-websocket)
7. [Gestión de Sesiones](#gestión-de-sesiones)
8. [Procesamiento de Medios](#procesamiento-de-medios)
9. [Configuración](#configuración)
10. [Manejo de Errores](#manejo-de-errores)
11. [Optimización de Rendimiento](#optimización-de-rendimiento)
12. [Debugging](#debugging)
13. [Contribución](#contribución)
14. [Licencia](#licencia)

---

## Descripción General de la Arquitectura

WhatsBerry utiliza una arquitectura multi-capa:

```
┌─────────────────┐
│  Cliente Android│
└────────┬────────┘
         │ (HTTP/WebSocket)
         ▼
┌─────────────────┐
│  Express Server │ ← Rutas API, Middleware
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Session Manager │ ← Ciclo de vida sesión, health checks
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ WhatsApp Client │ ← whatsapp-web.js + Puppeteer
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  WhatsApp Web   │
└─────────────────┘
```

### Componentes Clave

- **Express Server**: API HTTP y servicio de archivos estáticos
- **Socket.IO**: Comunicación bidireccional en tiempo real
- **Session Manager**: Maneja ciclo de vida de sesión, monitoreo de salud y reconexión
- **WhatsApp Client**: Automatización de WhatsApp Web basada en Puppeteer
- **Audio Converter**: Transcodificación de medios basada en FFmpeg

---

## Arquitectura del Proxy XMPP STARTTLS

### Descripción General

WhatsBerry incluye un proxy XMPP STARTTLS personalizado (`xmpp-starttls-proxy.js`) para permitir que dispositivos BlackBerry 10 se conecten al servidor XMPP Prosody con soporte TLS legacy. Este proxy se sitúa entre los clientes BB10 y Prosody, manejando la negociación TLS con soporte para versiones antiguas de TLS (TLSv1.0+) que las instalaciones modernas de Prosody ya no soportan directamente.

### Diagrama de Arquitectura

```
┌─────────────────┐
│  Cliente BB10   │ (Requiere TLS 1.0+)
└────────┬────────┘
         │ Puerto 5222 (XMPP)
         ▼
┌─────────────────────────────┐
│  Proxy STARTTLS             │
│  - Escucha: 0.0.0.0:5222    │
│  - Maneja STARTTLS          │
│  - Soporte TLS 1.0 - 1.3    │
│  - Gestión de certificados  │
└────────┬────────────────────┘
         │ Puerto 5200 (XMPP plano)
         ▼
┌─────────────────────────────┐
│  Servidor XMPP Prosody      │
│  - Escucha: 127.0.0.1:5200  │
│  - Módulo TLS DESHABILITADO │
│  - Maneja autenticación     │
└─────────────────────────────┘
```

### Especificaciones Técnicas

| Componente | Valor | Propósito |
|-----------|-------|-----------|
| Dirección de Escucha | `0.0.0.0:5222` | Puerto estándar cliente XMPP |
| Backend | `127.0.0.1:5200` | Puerto Prosody sin TLS |
| Versiones TLS | TLSv1.0 - TLSv1.3 | Compatibilidad BB10 legacy |
| Certificado | `/etc/prosody/certs/whatsberry.descarga.media.crt` | Certificado SSL |
| Clave Privada | `/etc/prosody/certs/whatsberry.descarga.media.key` | Clave privada SSL |
| Módulos Node.js | `net`, `tls`, `fs`, `crypto` | Networking principal |

### Detalles Clave de Implementación

#### 1. Soporte TLS Legacy

El proxy habilita soporte para TLS 1.0 (deshabilitado por defecto en OpenSSL 3 moderno) para acomodar dispositivos BlackBerry 10:

```javascript
// Habilitar protocolos TLS legacy en OpenSSL 3
process.env.OPENSSL_CONF = '/dev/null';
crypto.DEFAULT_MIN_VERSION = 'TLSv1';

tlsOptions = {
  minVersion: 'TLSv1',
  maxVersion: 'TLSv1.3',
  secureOptions: crypto.constants.SSL_OP_NO_SSLv2 | crypto.constants.SSL_OP_NO_SSLv3
};
```

**Por qué**: BlackBerry 10.3.3 solo soporta TLS 1.0 y 1.1, mientras que los servidores modernos requieren TLS 1.2+. Este proxy cierra la brecha de compatibilidad.

#### 2. Flujo de Negociación STARTTLS

1. **Conexión Inicial**: Cliente se conecta al proxy en puerto 5222
2. **Proxy → Prosody**: Proxy crea conexión inicial a Prosody en puerto 5200
3. **Inicio de Stream**: Cliente envía apertura de stream XMPP
4. **Anuncio de Features**: Proxy asegura que STARTTLS sea anunciado (inyectado si falta)
5. **Solicitud STARTTLS**: Cliente envía stanza `<starttls/>`
6. **Decisión Crítica**: Proxy destruye conexión Prosody inicial
7. **Actualización TLS**: Proxy envuelve socket del cliente con TLS
8. **Nueva Conexión Backend**: Proxy crea conexión Prosody FRESCA post-TLS
9. **Comunicación Cifrada**: Todo el tráfico subsecuente fluye a través del túnel cifrado

#### 3. Decisión Arquitectónica Crítica: Nueva Conexión Después de TLS

**Problema**: Inicialmente, el proxy intentaba reusar la misma conexión Prosody antes y después de la actualización TLS, causando conflictos de stream XMPP y fallos de autenticación.

**Solución**: El proxy ahora crea una conexión **completamente nueva** a Prosody después del handshake TLS:

```javascript
// Antes de TLS: Conexión Prosody inicial
const prosodySocket = net.createConnection({ port: 5200 });

// Cuando se solicita STARTTLS:
prosodySocket.removeAllListeners(); // Limpiar
prosodySocket.destroy();            // Cerrar conexión vieja

// Después del handshake TLS:
const newProsodySocket = net.createConnection({ port: 5200 }); // Conexión fresca
```

**Por Qué Funciona Esto**:
- El protocolo XMPP requiere un stream fresco después de STARTTLS
- Reusar la misma conexión backend creaba conflictos de estado de stream
- Cada conexión Prosody mantiene su propio contexto de stream XMPP
- Nueva conexión = stream XMPP limpio = flujo de autenticación correcto

**Resultado**: Elimina los fallos de autenticación y errores de stream que ocurrían con la reutilización de conexión.

#### 4. Inyección de Feature STARTTLS

El proxy monitorea activamente las features de stream de Prosody e inyecta STARTTLS si está ausente:

```javascript
if (dataStr.includes('<stream:features>') && !dataStr.includes('<starttls')) {
  dataStr = dataStr.replace(
    '</stream:features>',
    "<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/></stream:features>"
  );
}
```

**Por qué**: Asegura que los clientes siempre vean STARTTLS como disponible, incluso si la configuración de Prosody cambia.

#### 5. Flujo de Datos Bidireccional

**Pre-TLS** (texto plano):
```
Cliente ←→ Proxy ←→ Conexión Prosody Inicial
```

**Post-TLS** (cifrado):
```
Cliente ←(TLS)→ Proxy ←(texto plano)→ Nueva Conexión Prosody
       cifrado              descifrado
```

El proxy:
- Descifra datos del socket cliente envuelto en TLS
- Reenvía texto plano a nueva conexión Prosody
- Cifra respuestas de Prosody de vuelta al cliente

### Cambios de Configuración Prosody

Para trabajar con el proxy STARTTLS, el módulo TLS de Prosody debe estar **deshabilitado** para el VirtualHost de WhatsBerry:

**Archivo**: `/etc/prosody/prosody.cfg.lua`

```lua
VirtualHost "whatsberry.descarga.media"
  modules_disabled = { "tls" }  -- El proxy maneja TLS

  c2s_ports = { 5200 }          -- Puerto sin TLS para proxy
  legacy_ssl_ports = {}         -- Deshabilitar TLS directo
```

**Por qué**:
- Prosody ya no negocia TLS directamente
- El proxy maneja todas las operaciones TLS
- Prosody recibe conexiones de texto plano desde localhost
- Separación de responsabilidades: terminación TLS vs lógica XMPP

### Ciclo de Vida de Conexión

1. **Cliente Conecta**: Cliente BB10 se conecta a `whatsberry.descarga.media:5222`
2. **Handshake Inicial**: Proxy reenvía apertura de stream XMPP a Prosody
3. **Descubrimiento de Features**: Cliente recibe feature STARTTLS inyectado
4. **Trigger STARTTLS**: Cliente envía stanza `<starttls/>`
5. **Respuesta Proceed**: Proxy envía `<proceed xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>`
6. **Handshake TLS**: Negociación TLS 1.0-1.3 (BB10 típicamente usa TLS 1.0)
7. **Reset de Conexión**: Conexión Prosody vieja destruida
8. **Nuevo Backend**: Conexión Prosody fresca establecida
9. **Reinicio de Stream**: Cliente inicia nuevo stream XMPP (cifrado)
10. **Autenticación**: Autenticación SASL procede normalmente
11. **Sesión Activa**: Comunicación XMPP cifrada

### Logging y Monitoreo

Cada conexión se le asigna un ID único para seguimiento:

```
[1] NUEVA conexión desde 192.168.1.100:54321
[1] Conectado a Prosody
[1] STARTTLS solicitado por cliente
[1] Eliminados todos los listeners de Prosody
[1] Cerrada conexión Prosody vieja
[1] ✓ TLS establecido: TLSv1, Cipher: ECDHE-RSA-AES256-SHA
[1] Nueva conexión Prosody establecida después de TLS
[1] Prosody→Cliente: 256B
[1] Cliente→Prosody: 512B (descifrado)
[1] Cliente cerrado - Enviado: 2048B, Recibido: 4096B
```

**Métricas Registradas**:
- Establecimiento/cierre de conexión
- Protocolo TLS y suite cipher usada
- Bytes transferidos (cliente→proxy, proxy→Prosody)
- Dirección de flujo de datos y tamaño
- Condiciones de error

### Consideraciones de Seguridad

**Fortalezas**:
- Cifrado TLS para todo el tráfico del cliente
- Validación de certificado (cliente confía en cert del servidor)
- No exposición de credenciales sobre texto plano
- Binding Prosody solo en localhost (127.0.0.1)

**Trade-offs**:
- Soporte TLS 1.0 requerido para BB10 (protocolo deprecado)
- Proxy tiene acceso al tráfico XMPP descifrado
- Superficie de ataque adicional comparada con conexión Prosody directa

**Mitigación**:
- Proxy corre en el mismo servidor que Prosody (entorno confiable)
- Prosody solo accesible vía localhost
- Exposición externa limitada al puerto TLS cifrado 5222
- Actualizaciones de seguridad regulares para Node.js y OpenSSL

### Despliegue y Gestión

**Iniciar el Proxy**:
```bash
node /opt/whatsberry/xmpp-starttls-proxy.js
```

**Correr como Servicio** (systemd):
```ini
[Unit]
Description=Proxy XMPP STARTTLS para BlackBerry 10
After=network.target prosody.service

[Service]
Type=simple
User=whatsberry
ExecStart=/usr/bin/node /opt/whatsberry/xmpp-starttls-proxy.js
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

**Gestión de Procesos**:
- Apagado graceful en SIGINT/SIGTERM
- Limpieza automática de conexiones activas
- Recuperación de errores con destrucción de conexión

### Troubleshooting

**Problemas Comunes**:

| Problema | Síntoma | Solución |
|-------|---------|----------|
| Puerto en uso | Error `EADDRINUSE` | Verificar otros servicios en puerto 5222, matar o reconfigurar |
| Errores de certificado | Fallo en handshake TLS | Verificar que archivos cert/key existen y son legibles |
| Fallo de autenticación | Cliente se desconecta después de TLS | Asegurar que módulo TLS de Prosody está deshabilitado, verificar lógica de nueva conexión |
| No se ofrece STARTTLS | Cliente nunca intenta TLS | Verificar código de inyección de features, revisar features de Prosody |
| Conexión se cuelga | No hay flujo de datos post-TLS | Verificar establecimiento de nueva conexión Prosody, verificar handlers bidireccionales |

**Pasos de Debug**:
1. Verificar logs del proxy para seguimiento de ID de conexión
2. Verificar protocolo TLS negociado (debería mostrar TLSv1.x)
3. Confirmar que nueva conexión Prosody fue creada después de TLS
4. Monitorear contadores de bytes para flujo de datos
5. Verificar logs de Prosody para intentos de autenticación

### Características de Rendimiento

- **Latencia**: Mínima (~1-2ms) para conexión Prosody localhost
- **Throughput**: Line-rate para tráfico XMPP basado en texto
- **Memoria**: ~10-20MB por conexión activa (Node.js + buffers)
- **CPU**: Insignificante excepto durante handshake TLS
- **Conexiones Concurrentes**: Limitado por recursos del sistema, típicamente 1000+ soportado

### Mejoras Futuras

Mejoras potenciales:
- Connection pooling para backend Prosody
- Soporte WebSocket para clientes modernos
- Rate limiting y protección DoS
- Reanudación de sesión TLS para reconexiones más rápidas
- Export de métricas (Prometheus/StatsD)
- Endpoint de health check

---

## Stack Tecnológico

### Dependencias Core

| Paquete | Versión | Propósito |
|---------|---------|---------|
| Node.js | ≥16.0.0 | Entorno de ejecución |
| Express | ^4.18.2 | Framework de servidor web |
| Socket.IO | ^4.7.2 | Comunicación WebSocket |
| whatsapp-web.js | ^1.34.1 | Interfaz WhatsApp Web |
| Puppeteer | ^24.10.2 | Automatización Chrome headless |
| fluent-ffmpeg | ^2.1.3 | Conversión de audio |

### Herramientas de Desarrollo

- **nodemon**: Auto-reinicio durante desarrollo
- **PM2**: Gestión de procesos en producción (opcional)
- **dotenv**: Gestión de variables de entorno

---

## Autenticación y Seguridad

### Modelo de Seguridad

**Autenticación con API Key**: Todas las requests requieren una API key válida en el header `X-API-Key`.

### Flujo de Autenticación

```
┌─────────┐
│  Inicio │
└────┬────┘
     │
     ▼
┌──────────────────────┐
│ POST /create-session │ ← API Key requerida
│ Body: { deviceInfo } │
└──────────┬───────────┘
           │
           ▼ Retorna sessionId
┌────────────────────────────┐
│ POST /start-session/:id    │ ← API Key requerida
│ Lanza cliente WhatsApp     │
└──────────┬─────────────────┘
           │
           ▼ Emite QR vía WebSocket
┌──────────────────────────────┐
│ Usuario escanea código QR    │
│ Sesión WhatsApp establecida  │
└──────────┬───────────────────┘
           │
           ▼
┌────────────────────────┐
│ Requests Autenticadas  │ ← API Key requerida
│ GET /chats, /messages  │
└────────────────────────┘
```

### Características de Seguridad

- **Autenticación API Key**: Todas las requests API requieren una API key válida
- **Limpieza Automática**: Sesiones inactivas eliminadas después de 24 horas
- **Sin Almacenamiento de Datos**: Mensajes nunca se almacenan en el servidor
- **Auto-hospedado**: Tú controlas el servidor y tus datos

### Cadena de Middleware

```javascript
// API Key requerida para todos los endpoints
POST /create-session
  → apiKeyMiddleware

GET /session/:sessionId/chats
  → apiKeyMiddleware
```

---

## Referencia de API

### URL Base

Auto-hospedado: `http://tu-servidor:3000`

### Headers de Autenticación

```http
X-API-Key: tu-api-key-aqui
```

---

### Endpoints Públicos

Sin autenticación requerida.

#### `GET /`
Página de inicio con enlaces de descarga de la app.

#### `GET /health`
Health check del servidor con métricas.

**Respuesta:**
```json
{
  "status": "healthy",
  "uptime": 86400,
  "memory": {
    "used": 256000000,
    "total": 512000000
  },
  "activeSessions": 12,
  "readySessions": 10
}
```

#### `GET /status`
Verificación de estado simple.

**Respuesta:**
```json
{
  "status": "ok",
  "timestamp": 1234567890
}
```

---

### Endpoints de Gestión de Sesión

API Key requerida.

#### `POST /create-session`

Crea una nueva sesión o recupera una existente para un dispositivo.

**Request:**
```json
{
  "deviceInfo": {
    "deviceId": "id-dispositivo-unico",
    "deviceName": "BlackBerry Q10",
    "model": "Q10",
    "osVersion": "10.3.3"
  }
}
```

**Respuesta:**
```json
{
  "sessionId": "abc123-def456-ghi789",
  "userId": "id-usuario-hasheado",
  "message": "Sesión creada exitosamente"
}
```

**Códigos de Estado:**
- `200`: Sesión creada/recuperada
- `400`: Información de dispositivo inválida
- `401`: API key inválida
- `500`: Error del servidor

---

#### `POST /start-session/:sessionId`

Inicializa cliente WhatsApp y genera código QR.

**Parámetros:**
- `sessionId`: ID de sesión desde create-session

**Respuesta:**
```json
{
  "message": "Inicialización de sesión iniciada"
}
```

**Eventos WebSocket:**
Después de llamar a este endpoint, escuchar:
- `qr`: Datos de código QR (imagen base64)
- `ready`: Sesión autenticada y lista
- `loading_screen`: Actualizaciones de progreso de carga

**Códigos de Estado:**
- `200`: Inicialización iniciada
- `404`: Sesión no encontrada
- `410`: Sesión reemplazada por una más nueva
- `500`: Fallo en inicialización

---

#### `GET /session/:sessionId/status`

Verificar estado de sesión actual.

**Respuesta:**
```json
{
  "sessionId": "abc123",
  "isReady": true,
  "hasQR": false,
  "phoneNumber": "1234567890",
  "lastActivity": 1234567890000
}
```

---

#### `GET /session/:sessionId/qr`

Recuperar código QR actual (si está disponible).

**Respuesta:**
```json
{
  "qr": "data:image/png;base64,iVBORw0KG..."
}
```

**Códigos de Estado:**
- `200`: Código QR disponible
- `404`: Sesión o QR no encontrado

---

### Endpoints de Chat

API Key requerida.

#### `GET /session/:sessionId/chats`

Recuperar lista de chats.

**Parámetros de Query:**
- `includeProfilePics` (default: `true`): Incluir fotos de perfil
- `limit`: Número de chats a retornar
- `offset` (default: `0`): Offset de paginación

**Respuesta:**
```json
{
  "chats": [
    {
      "id": "1234567890@c.us",
      "name": "Juan Pérez",
      "isGroup": false,
      "unreadCount": 3,
      "timestamp": 1234567890,
      "profilePic": "https://...",
      "lastMessage": {
        "body": "¡Hola!",
        "timestamp": 1234567890,
        "fromMe": false,
        "ack": 1
      }
    }
  ],
  "total": 50,
  "offset": 0,
  "limit": 50,
  "hasMore": false
}
```

**Códigos de Estado:**
- `200`: Éxito
- `400`: Cliente no está listo
- `404`: Sesión no encontrada
- `503`: Sesión desconectada, reconectando

---

#### `GET /session/:sessionId/chat/:chatId/messages`

Obtener mensajes de un chat específico.

**Parámetros de Query:**
- `limit` (default: `50`): Número de mensajes
- `includeMedia` (default: `false`): Incluir datos de medios
- `includeContacts` (default: `true`): Incluir información de contacto

**Respuesta:**
```json
{
  "messages": [
    {
      "id": "msg123",
      "body": "Hola",
      "timestamp": 1234567890,
      "fromMe": false,
      "hasMedia": false,
      "type": "chat",
      "ack": 2,
      "from": "1234567890@c.us",
      "to": "0987654321@c.us"
    }
  ]
}
```

---

#### `POST /session/:sessionId/send-message`

Enviar un mensaje de texto.

**Request:**
```json
{
  "to": "1234567890@c.us",
  "message": "¡Hola, mundo!"
}
```

**Respuesta:**
```json
{
  "success": true,
  "messageId": "msg123_serialized",
  "timestamp": 1234567890
}
```

**Códigos de Estado:**
- `200`: Mensaje enviado
- `400`: Parámetros faltantes o cliente no listo
- `404`: Sesión no encontrada
- `503`: Sesión no saludable, reconectando

---

#### `POST /session/:sessionId/send-media`

Enviar mensaje de medios (imagen, video, documento, audio).

**Request:**
```json
{
  "to": "1234567890@c.us",
  "media": "data:image/png;base64,iVBORw0KG...",
  "caption": "¡Mira esto!",
  "filename": "foto.png"
}
```

**Respuesta:**
```json
{
  "success": true,
  "messageId": "msg456_serialized",
  "timestamp": 1234567890
}
```

---

#### `POST /session/:sessionId/chat/:chatId/mark-read`

Marcar todos los mensajes en un chat como leídos.

**Respuesta:**
```json
{
  "success": true,
  "chatId": "1234567890@c.us",
  "message": "Chat marcado como leído"
}
```

---

#### `GET /session/:sessionId/contacts`

Obtener contactos del usuario.

**Parámetros de Query:**
- `includeProfilePics` (default: `true`)
- `limit`: Número de contactos
- `offset` (default: `0`)

**Respuesta:**
```json
{
  "contacts": [
    {
      "id": "1234567890@c.us",
      "name": "Juan Pérez",
      "number": "1234567890",
      "isMyContact": true,
      "profilePic": "https://..."
    }
  ],
  "total": 100,
  "offset": 0,
  "limit": 100,
  "hasMore": false
}
```

---

#### `GET /session/:sessionId/group/:groupId/participants`

Obtener participantes de un chat grupal.

**Respuesta:**
```json
{
  "groupId": "123456789@g.us",
  "groupName": "Chat del Equipo",
  "participants": [
    {
      "id": "1234567890@c.us",
      "phoneId": "1234567890@c.us",
      "name": "Juan Pérez",
      "number": "1234567890",
      "isAdmin": true,
      "isSuperAdmin": false
    }
  ],
  "participantCount": 5
}
```

---

### Endpoints de Medios

API Key requerida.

#### `GET /session/:sessionId/message/:messageId/media`

Descargar medios de un mensaje.

**Parámetros de Query:**
- `download` (default: `false`): Forzar descarga vs inline
- `format` (default: `original`): Formato de conversión (ej., `mp3` para audio)

**Headers de Respuesta:**
```
Content-Type: audio/mpeg
Content-Length: 1234567
Content-Disposition: inline; filename="audio.mp3"
X-Media-Type: ptt
X-Original-Mimetype: audio/ogg
X-Converted: true
```

**Respuesta:** Datos de medios binarios

---

#### `GET /session/:sessionId/chat/:chatId/media/:messageIndex`

Descargar medios por índice de mensaje en chat.

**Parámetros de Query:**
- `download` (default: `false`)
- `limit` (default: `50`): Mensajes a recuperar para indexar
- `format` (default: `original`)

**Respuesta:** Datos de medios binarios (mismos headers que arriba)

---

#### `GET /formats/:mimetype`

Obtener formatos de conversión soportados para un tipo de medio.

**Ejemplo:** `/formats/audio%2Fogg`

**Respuesta:**
```json
{
  "inputMimetype": "audio/ogg",
  "supportedFormats": ["original", "mp3"],
  "conversionInfo": {
    "mp3": {
      "description": "Convertir a MP3",
      "quality": "128kbps, 44.1kHz, Estéreo",
      "usage": "Agregar ?format=mp3 a URL de descarga de medios"
    }
  }
}
```

---

#### `GET /ffmpeg/status`

Verificar disponibilidad e instalación de FFmpeg.

**Respuesta:**
```json
{
  "available": true,
  "path": "/usr/bin/ffmpeg",
  "platform": "linux",
  "audioConversionEnabled": true
}
```

---

#### `GET /audio-cache/stats`

Obtener estadísticas de caché de conversión de audio.

**Respuesta:**
```json
{
  "totalEntries": 15,
  "expiredEntries": 3,
  "totalCacheSizeKB": 5120,
  "cacheTTLHours": 2,
  "entries": [...]
}
```

---

### Endpoints de Debug

API Key requerida. Solo para desarrollo/debugging.

#### `GET /debug/sessions`

Listar todas las sesiones activas.

**Respuesta:**
```json
{
  "sessions": [
    {
      "sessionId": "abc123",
      "userId": "user123",
      "isReady": true,
      "lastActivity": 1234567890000
    }
  ]
}
```

---

#### `GET /debug/session-details`

Información detallada de sesión incluyendo números de teléfono.

**Respuesta:**
```json
{
  "sessions": [
    {
      "sessionId": "abc123",
      "userId": "user123",
      "phoneNumber": "1234567890",
      "isReady": true,
      "isAuthenticated": true,
      "lastActivity": 1234567890000,
      "hasQR": false
    }
  ],
  "totalSessions": 1
}
```

---

#### `POST /session/:sessionId/logout`

Cerrar sesión y destruir sesión.

**Headers:** API Key requerida

**Respuesta:**
```json
{
  "message": "Sesión destruida exitosamente"
}
```

---

## Eventos WebSocket

Conectar al servidor Socket.IO en la URL base.

### Eventos Cliente → Servidor

#### `join_session`
Unirse a una sala de sesión para recibir eventos.

```javascript
socket.emit('join_session', sessionId);
```

**Respuesta:**
```javascript
socket.on('session_joined', (data) => {
  // { sessionId: 'abc123', socketId: 'xyz789' }
});
```

---

#### `request_qr`
Solicitar código QR actual.

```javascript
socket.emit('request_qr', sessionId);
```

---

#### `request_session_status`
Solicitar estado de sesión.

```javascript
socket.emit('request_session_status', sessionId);
```

**Respuesta:**
```javascript
socket.on('session_status', (data) => {
  // { sessionId, isReady, hasQR, lastActivity, phoneNumber }
});
```

---

#### `ping`
Ping al servidor (para pruebas de conexión).

```javascript
socket.emit('ping');
```

**Respuesta:**
```javascript
socket.on('pong', (data) => {
  // { timestamp: 1234567890 }
});
```

---

### Eventos Servidor → Cliente

#### `qr`
Código QR generado o actualizado.

```javascript
socket.on('qr', (qrCodeData) => {
  // qrCodeData: string de imagen base64
});
```

---

#### `ready`
Sesión WhatsApp autenticada y lista.

```javascript
socket.on('ready', (data) => {
  // { phoneNumber: '1234567890', sessionId: 'abc123' }
});
```

---

#### `authenticated`
Autenticación WhatsApp exitosa.

```javascript
socket.on('authenticated', () => {
  console.log('¡Autenticado!');
});
```

---

#### `auth_failure`
Falló autenticación.

```javascript
socket.on('auth_failure', (message) => {
  console.error('Fallo de autenticación:', message);
});
```

---

#### `disconnected`
Cliente WhatsApp desconectado.

```javascript
socket.on('disconnected', (reason) => {
  console.log('Desconectado:', reason);
});
```

---

#### `loading_screen`
Actualizaciones de progreso de carga.

```javascript
socket.on('loading_screen', (percent, message) => {
  console.log(`Cargando: ${percent}%`);
});
```

---

#### `message`
Nuevo mensaje recibido.

```javascript
socket.on('message', (message) => {
  // objeto mensaje
});
```

---

#### `message_ack`
Reconocimiento de mensaje actualizado.

```javascript
socket.on('message_ack', (message, ack) => {
  // valores ack: 0=error, 1=pendiente, 2=enviado, 3=entregado, 4=leído
});
```

---

## Gestión de Sesiones

### Ciclo de Vida de Sesión

1. **Creación**: `POST /create-session` genera ID de sesión
2. **Inicialización**: `POST /start-session` lanza navegador Puppeteer
3. **Generación QR**: WhatsApp Web muestra QR, emitido vía WebSocket
4. **Autenticación**: Usuario escanea QR, sesión se vuelve "autenticada"
5. **Estado Ready**: Sesión está completamente lista para mensajería
6. **Uso Activo**: Llamadas API mantienen sesión viva vía `lastActivity`
7. **Limpieza**: Sesiones inactivas eliminadas después de timeout

### Estados de Sesión

```javascript
{
  sessionId: "abc123",
  userId: "id-usuario-hasheado",
  isAuthenticated: false,  // QR de WhatsApp escaneado
  isReady: false,          // WhatsApp completamente cargado
  client: null,            // Instancia de cliente WhatsApp
  qrCode: null,            // Código QR actual (si hay)
  phoneNumber: null,       // Número de teléfono del usuario
  lastActivity: timestamp, // Última llamada API
  deviceInfo: {}           // Metadatos del dispositivo
}
```

### Monitoreo de Salud

Las sesiones se monitorean cada 5 minutos:

- **Health Check**: Verifica estado de navegador y WhatsApp
- **Auto-Reconexión**: Se reconecta si la sesión se vuelve no saludable
- **Limpieza**: Elimina sesiones muertas

### Timeouts

| Timeout | Duración | Propósito |
|---------|----------|---------|
| Sesión Activa | 24 horas | Eliminar sesiones autenticadas inactivas |
| Sesión Inacabada | 15 minutos | Eliminar sesiones no autenticadas |
| Caché de Audio | 2 horas | Limpiar archivos de audio convertidos |
| Health Check | 5 minutos | Monitorear salud de sesión |

---

## Procesamiento de Medios

### Conversión de Audio

WhatsBerry convierte automáticamente formatos de audio incompatibles con Android 4.3 a MP3.

**Formatos de Entrada Soportados:**
- audio/ogg (códec Opus)
- audio/opus
- audio/webm
- audio/aac
- audio/m4a
- audio/wav
- audio/flac

**Formato de Salida:**
- **Códec**: MP3
- **Bitrate**: 128kbps
- **Frecuencia de Muestreo**: 44.1kHz
- **Canales**: Estéreo

### Proceso de Conversión

1. App solicita medios con `?format=mp3`
2. Servidor verifica caché para archivo convertido
3. Si no está en caché:
   - Descarga original desde WhatsApp
   - Convierte usando FFmpeg
   - Cachea archivo convertido (TTL 2 horas)
4. Sirve MP3 convertido

### Estrategia de Caché

- **Key**: ID de mensaje
- **TTL**: 2 horas
- **Almacenamiento**: Sistema de archivos (`src/audio_cache/`)
- **Limpieza**: Automática cada 30 minutos

---

## Configuración

### Configuración de Constantes

`src/config/constants.js` para configuraciones avanzadas:

```javascript
module.exports = {
  // Timeouts
  SESSION_TIMEOUT: 24 * 60 * 60 * 1000,        // 24 horas
  UNFINISHED_SESSION_TIMEOUT: 15 * 60 * 1000,  // 15 minutos

  // Configuraciones de audio
  AUDIO_CONVERSION_TTL: 2 * 60 * 60 * 1000,    // 2 horas
  AUDIO_BITRATE: 128,
  AUDIO_FREQUENCY: 44100,
  AUDIO_CHANNELS: 2,

  // Intervalos de limpieza
  SESSION_CLEANUP_INTERVAL: 60 * 60 * 1000,    // 1 hora
  HEALTH_CHECK_INTERVAL: 5 * 60 * 1000,        // 5 minutos
};
```

### Variables de Entorno

Variables de entorno requeridas en `.env`:

```env
# Puerto del Servidor
PORT=3000

# Seguridad - Autenticación API Key
API_KEY=tu_api_key_segura_aqui

# Configuraciones Opcionales
NODE_ENV=production
```

**Generar una API key segura:**
```bash
node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
```

---

## Manejo de Errores

### Códigos de Estado HTTP

| Código | Significado | Causas Comunes |
|------|---------|---------------|
| 200 | Éxito | Request completado exitosamente |
| 400 | Bad Request | Parámetros faltantes, entrada inválida |
| 401 | No Autorizado | API key inválida o faltante |
| 404 | No Encontrado | Sesión o recurso no encontrado |
| 410 | Desaparecido | Sesión reemplazada por una más nueva |
| 500 | Error del Servidor | Error interno, verificar logs |
| 503 | Servicio No Disponible | Sesión reconectando, intentar de nuevo |

### Respuestas de Error Comunes

#### Sesión No Lista
```json
{
  "error": "Cliente WhatsApp no está listo"
}
```

**Solución**: Esperar evento WebSocket `ready` antes de hacer requests.

---

#### Sesión Desconectada
```json
{
  "error": "Sesión desconectada, reconexión en progreso. Por favor intente de nuevo en un momento.",
  "reconnecting": true
}
```

**Solución**: Reintentar request después de unos segundos. Servidor está auto-reconectando.

---

### Estrategia de Reconexión

Cuando una sesión se vuelve no saludable:

1. Servidor detecta problema (error o fallo en health check)
2. Establece estado de sesión a "reconectando"
3. Retorna `503 Service Unavailable` con `reconnecting: true`
4. Intenta reinicializar cliente WhatsApp
5. Una vez reconectado, llamadas API se reanudan normalmente

**La app debería:**
- Detectar respuesta `503` + `reconnecting: true`
- Mostrar UI "Reconectando..."
- Reintentar request después de 3-5 segundos
- Escuchar evento WebSocket `ready`

---

## Optimización de Rendimiento

### Gestión de Sesiones
- Sesiones usan instancias Puppeteer aisladas
- Limpieza automática previene fugas de memoria
- Health checks previenen sesiones zombie

### Manejo de Medios
- Audio convertido cacheado por 2 horas
- Fotos de perfil cacheadas durante recuperación
- Timeouts agresivos previenen requests colgados

### Tiempos de Respuesta API
- Procesamiento paralelo para contactos/chats
- Soporte de paginación para listas grandes
- Límites de timeout en todas las operaciones WhatsApp

---

## Debugging

### Endpoints de Debug

Usar `/debug/sessions` y `/debug/session-details` para inspeccionar:
- Estados de sesión
- Números de teléfono
- Tiempos de última actividad
- Estado de autenticación

### Logging

Todos los errores se registran en consola con timestamps:
```
[CREATE-SESSION] Request recibido en 2025-01-15T10:30:00.000Z
[START-SESSION] Sesión: abc123 - Timestamp: 1234567890
[WebSocket] Cliente conectado: xyz789
```

---

## Contribución

Este repositorio es abierto para transparencia. Para contribuir:

1. Revisar esta documentación técnica
2. Hacer fork del repositorio
3. Crear rama de feature
4. Realizar tus cambios
5. Enviar pull request con descripción detallada

---

## Licencia

**WhatsBerry** está liberado bajo la Licencia Apache-2.0 con Commons Clause, que permite inspección de código y contribución mientras previene uso comercial. Ver [LICENSE.md](LICENSE.md) para detalles completos.

---

**Última Actualización**: Octubre 2025
**Versión**: 0.10.3-beta
