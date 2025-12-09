# Troubleshooting del XMPP STARTTLS Proxy - Lecciones Aprendidas

**Fecha**: 7 de diciembre de 2025
**Contexto**: Implementación de proxy STARTTLS para soportar clientes BlackBerry 10 con TLS 1.0

## Tabla de Contenidos

1. [Resumen Ejecutivo](#resumen-ejecutivo)
2. [Problema Original](#problema-original)
3. [Arquitectura de la Solución](#arquitectura-de-la-solución)
4. [Problemas Encontrados y Soluciones](#problemas-encontrados-y-soluciones)
5. [Lecciones Aprendidas Críticas](#lecciones-aprendidas-críticas)
6. [Configuración Final](#configuración-final)
7. [Comandos de Diagnóstico](#comandos-de-diagnóstico)
8. [Referencias](#referencias)

---

## Resumen Ejecutivo

**Objetivo**: Permitir que clientes BlackBerry 10 (que solo soportan TLS 1.0) se conecten a un servidor Prosody XMPP que requiere TLS 1.2+.

**Solución Implementada**: Proxy STARTTLS en Node.js que:
- Escucha en puerto 5222 con soporte para TLS 1.0-1.3
- Maneja la negociación STARTTLS con el cliente
- Crea una **nueva conexión TCP** a Prosody (puerto 5200) después del TLS handshake
- Realiza relay bidireccional de datos entre cliente TLS y Prosody plaintext

**Estado**: ✅ **FUNCIONANDO** - Relay bidireccional confirmado, cliente recibe respuestas de Prosody

---

## Problema Original

### Síntomas
- Clientes BlackBerry 10 no podían conectarse a Prosody directamente
- Error: TLS handshake failed (versión TLS no soportada)
- BlackBerry 10 solo soporta TLS 1.0, Prosody/OpenSSL 3 requiere TLS 1.2+ por defecto

### Requisitos Técnicos
1. Soporte para TLS 1.0 (protocolo legacy)
2. Negociación STARTTLS estándar de XMPP
3. Compatibilidad con arquitectura Prosody existente
4. Sin modificaciones en clientes BlackBerry

---

## Arquitectura de la Solución

```
┌─────────────────────┐
│   BlackBerry 10     │
│   Client (TLS 1.0)  │
└──────────┬──────────┘
           │ TLS 1.0 encrypted
           │ Port 5222
           ▼
┌─────────────────────────────────────┐
│  XMPP STARTTLS Proxy (Node.js)      │
│  - Acepta TLS 1.0-1.3               │
│  - Inyecta STARTTLS en features     │
│  - Crea NUEVA conexión después TLS  │
└──────────┬──────────────────────────┘
           │ Plaintext XMPP
           │ Port 5200
           ▼
┌─────────────────────┐
│   Prosody XMPP      │
│   (TLS disabled)    │
└─────────────────────┘
```

### Flujo de Conexión

1. **Cliente → Proxy**: Conexión TCP a puerto 5222
2. **Proxy → Prosody (conexión #1)**: Abre conexión a puerto 5200
3. **Prosody → Proxy**: Envía stream features (sin STARTTLS)
4. **Proxy → Cliente**: Inyecta `<starttls/>` en las features
5. **Cliente → Proxy**: Solicita `<starttls/>`
6. **Proxy**: Destruye conexión #1 a Prosody
7. **Proxy → Cliente**: Envía `<proceed/>`
8. **TLS Handshake**: Cliente y proxy negocian TLS 1.0
9. **Proxy → Prosody (conexión #2)**: Crea **NUEVA** conexión TCP
10. **Cliente → Proxy → Prosody**: Relay bidireccional de datos

---

## Problemas Encontrados y Soluciones

### Problema 1: Timing del TLS Handshake

**Síntoma**: TLS setup code dentro de callback de `write()` causaba que los listeners se registraran muy tarde.

**Código Problemático**:
```javascript
clientSocket.write(proceedResponse, () => {
  // TLS setup aquí - MAL
  const tlsSocket = new tls.TLSSocket(...);
  tlsSocket.on('secure', () => {
    prosodySocket.on('data', ...); // Muy tarde!
  });
});
```

**Solución**: Mover setup de TLS fuera del callback.

**Lección**: Los callbacks de `write()` se ejecutan DESPUÉS de que los datos se escriben al buffer, no cuando el cliente los recibe.

---

### Problema 2: Listener Registrado Pero Nunca Ejecutado

**Síntoma**:
- Logs mostraban "Client→Prosody: 144B (decrypted)"
- `write()` retornaba `true`
- Prosody **nunca recibía** los datos
- Listener count: 0→1 pero evento 'data' nunca disparaba

**Intentos Fallidos**:
1. ❌ `prosodySocket.read()` - retornaba null
2. ❌ `setImmediate()` para check buffer - sin datos
3. ❌ `prosodySocket.resume()` después de registrar listener - no ayudó

**Causa Raíz**: El stream de XMPP ya estaba abierto en Prosody. Cuando el cliente enviaba un segundo `<stream:stream>` después de TLS, Prosody lo **ignoraba** porque ya tenía un stream activo en esa conexión TCP.

**Solución**: Crear una **NUEVA conexión TCP** a Prosody después del TLS handshake.

```javascript
// ANTES DEL TLS: Cerrar conexión vieja
prosodySocket.removeAllListeners();
prosodySocket.destroy();

// DESPUÉS DEL TLS: Nueva conexión
tlsSocket.on('secure', () => {
  const newProsodySocket = net.createConnection({
    host: '127.0.0.1',
    port: 5200
  });

  // Setup bidirectional relay con NUEVA conexión
  newProsodySocket.on('data', (data) => {
    tlsSocket.write(data);
  });

  tlsSocket.on('data', (data) => {
    newProsodySocket.write(data);
  });
});
```

**Lección Crítica**: En XMPP, solo puedes abrir un nuevo stream después de un evento de reset (STARTTLS completo, SASL auth). Reutilizar la conexión TCP causaba conflictos de stream.

---

### Problema 3: Orden de resume() y Registro de Listeners

**Síntoma**:
- `prosodySocket.resume()` llamado antes de registrar listener
- Datos buffered se perdían

**Código Problemático**:
```javascript
prosodySocket.resume();  // Dispara 'data' events
console.log('Resumed');

prosodySocket.on('data', (data) => {
  // Listener registrado DESPUÉS - pierde datos!
});
```

**Solución**: Registrar listeners ANTES de resume().

```javascript
prosodySocket.on('data', (data) => {
  // Listener listo
});

prosodySocket.resume();  // Ahora sí dispara eventos
```

**Lección**: `pause()` buffers data, `resume()` immediately fires buffered data events to **existing** listeners.

---

### Problema 4: UFW Rate Limiting

**Síntoma**:
- Primeras 6 conexiones funcionaban
- Después conexiones dejaban de llegar al proxy
- No había errores en logs del proxy

**Causa**: Regla UFW con `LIMIT` bloqueaba IPs con >6 conexiones en 30 segundos.

```bash
# ANTES (causaba problemas)
5222/tcp    LIMIT    Anywhere

# DESPUÉS (solución)
5222/tcp    ALLOW    Anywhere
```

**Comando Fix**:
```bash
sudo ufw delete limit 5222/tcp
sudo ufw allow 5222/tcp
```

**Lección**: Los clientes XMPP hacen múltiples intentos de conexión rápidamente. `LIMIT` es para SSH, no para XMPP.

---

### Problema 5: Socket Event Listener Cascade

**Síntoma**: Al destruir `prosodySocket` viejo, el evento `close` destruía `clientSocket`, abortando el TLS handshake.

**Causa**: Event listeners del socket viejo todavía activos.

```javascript
prosodySocket.on('close', () => {
  clientSocket.destroy(); // ¡Esto abortaba TLS!
});

// Luego...
prosodySocket.destroy(); // Trigger 'close' event
```

**Solución**: Remover TODOS los listeners antes de destruir.

```javascript
prosodySocket.removeAllListeners();
prosodySocket.destroy();
```

**Lección**: `removeAllListeners()` es crítico al re-arquitecturar conexiones mid-stream.

---

### Problema 6: Prosody Cerrando Conexión TCP Después de Stream Close

**Síntoma**:
- Enviar `</stream:stream>` a Prosody
- Prosody respondía con `</stream:stream>` (16 bytes)
- Prosody **cerraba la conexión TCP completa**

**Código Problemático**:
```javascript
prosodySocket.write('</stream:stream>'); // Cierra stream
prosodySocket.pause(); // Inútil - conexión se cerrará
```

**Solución**: No intentar cerrar el stream viejo. Simplemente destruir la conexión y crear una nueva.

**Lección**: En XMPP, `</stream:stream>` es terminal para la conexión TCP. No se puede reutilizar.

---

### Problema 7: Prosody No Ofreciendo STARTTLS

**Contexto**: Deshabilitamos TLS en Prosody porque el proxy lo maneja.

**Configuración Prosody**:
```lua
VirtualHost "whatsberry.descarga.media"
    modules_disabled = { "tls" }  -- Sin STARTTLS
```

**Problema**: Cliente BB10 requiere ver `<starttls/>` en features.

**Solución**: Proxy inyecta STARTTLS en features de Prosody.

```javascript
let dataStr = data.toString('utf8');

if (dataStr.includes('<stream:features>') && !dataStr.includes('<starttls')) {
  console.log(`Injecting STARTTLS into Prosody features`);
  dataStr = dataStr.replace(
    '</stream:features>',
    "<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/></stream:features>"
  );
  data = Buffer.from(dataStr, 'utf8');
}

clientSocket.write(data);
```

**Lección**: El proxy actúa como TLS termination proxy, pero debe mantener la semántica XMPP esperada por el cliente.

---

## Lecciones Aprendidas Críticas

### 1. Arquitectura de Conexiones XMPP

**Clave**: XMPP streams son stateful dentro de una conexión TCP. No puedes simplemente "resetear" un stream sin cerrar la conexión.

**Implicación**: Después de STARTTLS, necesitas:
- **Opción A**: Nueva conexión TCP (lo que implementamos)
- **Opción B**: STARTTLS end-to-end (cliente→Prosody directo, pero requiere TLS 1.2+)

**Por qué Opción A**: Permite que el proxy maneje TLS 1.0 mientras Prosody trabaja en plaintext.

### 2. Node.js Stream Pausing/Buffering

**Comportamiento Documentado Pero No Obvio**:
- `pause()` detiene eventos 'data' PERO sigue buffering
- `resume()` dispara eventos 'data' con buffer completo **inmediatamente**
- Si no hay listener registrado, datos se pierden

**Best Practice**:
```javascript
socket.pause();           // 1. Pausar primero
socket.on('data', ...);   // 2. Registrar listener
socket.resume();          // 3. Reanudar último
```

### 3. OpenSSL 3 y TLS Legacy

**Cambio en OpenSSL 3**: TLS 1.0 y 1.1 deshabilitados por defecto por seguridad.

**Solución para Node.js**:
```javascript
process.env.OPENSSL_CONF = '/dev/null';  // Deshabilitar config
crypto.DEFAULT_MIN_VERSION = 'TLSv1';    // Permitir TLS 1.0+
```

**Trade-off**: Seguridad vs compatibilidad. Documentar claramente.

### 4. UFW y Servicios de Red de Alta Frecuencia

**LIMIT vs ALLOW**:
- `LIMIT`: Max 6 conexiones en 30 seg (bueno para SSH)
- `ALLOW`: Sin límite (necesario para XMPP, HTTP, etc.)

**Síntoma Confuso**: No hay error en logs del servicio, simplemente las conexiones no llegan.

**Diagnóstico**:
```bash
sudo journalctl -xe | grep UFW
# Buscar: [UFW BLOCK] SRC=<IP> DST=... DPT=5222
```

### 5. TLS Socket Wrapping

**Concepto Clave**: `tls.TLSSocket` puede wrappear un socket plaintext existente.

```javascript
const tlsSocket = new tls.TLSSocket(clientSocket, {
  isServer: true,
  ...tlsOptions
});
```

**Ventaja**: No necesitas crear un nuevo listener socket en otro puerto.

**Desventaja**: El socket subyacente sigue siendo el mismo - cuidado con event listeners.

### 6. Event Listener Cleanup

**Problema Común**: Listeners viejos interfieren con nueva lógica.

**Solución**: Siempre cleanup antes de re-arquitecturar:
```javascript
socket.removeAllListeners('data');
socket.removeAllListeners('close');
socket.removeAllListeners('error');
// O simplemente:
socket.removeAllListeners();
```

### 7. Debugging de Protocolos Binarios/Texto

**Herramientas Útiles**:
```javascript
// Hex dump
console.log('Hex:', data.toString('hex'));

// Preview de texto
console.log('Preview:', data.toString('utf8').substring(0, 100));

// Estado del socket
console.log('State:', {
  destroyed: socket.destroyed,
  writable: socket.writable,
  readable: socket.readable,
  readyState: socket.readyState
});
```

---

## Configuración Final

### Archivo: `/opt/whatsberry/xmpp-starttls-proxy.js`

**Características Clave**:
- Puerto: `0.0.0.0:5222`
- Backend: `127.0.0.1:5200`
- TLS: 1.0 - 1.3
- Certificados: Let's Encrypt en `/etc/prosody/certs/`

**Flujo de Manejo de Conexión**:
1. Cliente conecta → proxy crea conexión #1 a Prosody
2. Proxy recibe features de Prosody → inyecta `<starttls/>`
3. Cliente solicita STARTTLS
4. Proxy destruye conexión #1, envía `<proceed/>`
5. TLS handshake con cliente
6. En evento 'secure': proxy crea **nueva** conexión #2 a Prosody
7. Relay bidireccional: cliente(TLS) ↔ proxy ↔ Prosody(plaintext)

### Archivo: `/etc/prosody/prosody.cfg.lua`

**Cambios Clave**:
```lua
-- Puerto cambiado de 5222 a 5200 (backend)
c2s_ports = { 5200 }

-- TLS NO requerido (proxy lo maneja)
c2s_require_encryption = false
allow_unencrypted_plain_auth = true

-- VirtualHost específico
VirtualHost "whatsberry.descarga.media"
    -- CRÍTICO: Deshabilitar módulo TLS
    modules_disabled = { "tls" }

    enabled = true
    allow_registration = true
```

### Archivo: `/etc/systemd/system/xmpp-tls-proxy.service`

```ini
[Unit]
Description=XMPP STARTTLS Proxy for BlackBerry 10
Documentation=file:///opt/whatsberry/xmpp-starttls-proxy.js
After=network.target prosody.service
Requires=prosody.service

[Service]
Type=simple
User=batman
Group=batman
WorkingDirectory=/opt/whatsberry
ExecStart=/home/batman/.local/share/mise/installs/node/16.20.2/bin/node /opt/whatsberry/xmpp-starttls-proxy.js
Restart=always
RestartSec=5
StandardOutput=journal
StandardError=journal
SyslogIdentifier=xmpp-tls-proxy

# Security
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/opt/whatsberry
CapabilityBoundingSet=CAP_NET_BIND_SERVICE

[Install]
WantedBy=multi-user.target
```

### Firewall (UFW)

```bash
# IMPORTANTE: ALLOW, no LIMIT
sudo ufw allow 5222/tcp
sudo ufw status | grep 5222
# Debe mostrar: 5222/tcp    ALLOW    Anywhere
```

---

## Comandos de Diagnóstico

### Verificar Estado de Servicios

```bash
# Proxy
sudo systemctl status xmpp-tls-proxy.service

# Prosody
sudo systemctl status prosody.service

# Verificar puertos
ss -tlnp | grep 5222  # Proxy debe estar escuchando
ss -tlnp | grep 5200  # Prosody debe estar escuchando
```

### Ver Logs en Tiempo Real

```bash
# Proxy (muestra relay bidireccional)
sudo journalctl -u xmpp-tls-proxy.service -f

# Prosody
sudo journalctl -u prosody.service -f

# Buscar conexiones específicas
sudo journalctl -u xmpp-tls-proxy.service -n 100 | grep "NEW connection"
```

### Verificar TLS Handshake

```bash
# Desde el servidor
openssl s_client -connect whatsberry.descarga.media:5222 -starttls xmpp -tls1

# Debe mostrar:
# - Certificate chain
# - Protocol: TLSv1
# - Cipher: ECDHE-ECDSA-AES128-SHA
```

### Verificar Relay Bidireccional

Buscar en logs del proxy:
```bash
sudo journalctl -u xmpp-tls-proxy.service -n 50 | grep -E "(Client→Prosody|Prosody→Client)"

# Debe mostrar:
# Client→Prosody: 144B (decrypted)
# Prosody→Client: 650B
```

### Verificar UFW No Está Bloqueando

```bash
# Ver reglas
sudo ufw status verbose | grep 5222

# Ver últimos bloqueos
sudo journalctl -xe | grep UFW | grep 5222 | tail -20

# Si hay bloqueos, cambiar a ALLOW
sudo ufw delete limit 5222/tcp
sudo ufw allow 5222/tcp
```

### Debug de Conexión Específica

```bash
# Activar en proxy código de debug (ya está)
# Logs muestran:
# - [ID] NEW connection from IP:PORT
# - [ID] ✓ TLS established: TLSv1
# - [ID] Bidirectional proxy established
# - [ID] Client→Prosody: XB
# - [ID] Prosody→Client: YB

# Prosody también muestra:
sudo journalctl -u prosody.service | grep "Client sent opening"
# Debe haber DOS stream openings por conexión exitosa
```

---

## Referencias

### Documentación Técnica Relacionada

- [README-es.md](README-es.md) - Índice completo de documentación
- [SERVER_SETUP-es.md](SERVER_SETUP-es.md) - Configuración del servidor
- [HTTP_SERVER-es.md](HTTP_SERVER-es.md) - Configuración de Nginx
- [CLIENT_CONFIGURATION-es.md](CLIENT_CONFIGURATION-es.md) - Guía de configuración de clientes
- [ARCHITECTURE_DIAGRAMS.md](ARCHITECTURE_DIAGRAMS.md) - Diagramas de arquitectura
- [../TECHNICAL.md](../TECHNICAL.md) - Detalles técnicos completos del proxy (in root directory)

### RFCs y Especificaciones

- RFC 6120: XMPP Core (STARTTLS)
- RFC 5246: TLS 1.2 (para entender downgrade a 1.0)
- XEP-0170: XMPP STARTTLS

### Node.js APIs Usadas

- `net.createServer()` - TCP server
- `net.createConnection()` - TCP client
- `tls.TLSSocket()` - TLS wrapping
- `socket.pause()/resume()` - Flow control

### Comandos y Tools

- `ss -tlnp` - Ver puertos escuchando
- `journalctl` - Logs de systemd
- `openssl s_client` - Test TLS
- `ufw` - Firewall de Ubuntu

---

## Conclusiones

La implementación del XMPP STARTTLS proxy para BlackBerry 10 requirió:

1. **Comprensión profunda de XMPP streams**: Los streams son stateful y no pueden ser "reseteados" sin cerrar la conexión TCP.

2. **Nueva arquitectura de conexión**: La solución de crear una nueva conexión a Prosody después del TLS handshake fue la clave para resolver el problema de relay bidireccional.

3. **Atención a detalles de Node.js**: Orden de resume/pause, cleanup de listeners, timing de eventos.

4. **Configuración de sistema cuidadosa**: UFW ALLOW vs LIMIT, OpenSSL 3 legacy support, Prosody TLS disabled.

5. **Debugging sistemático**: Logs detallados, hex dumps, state inspection fueron críticos para identificar problemas.

**Estado Final**: ✅ **COMPLETAMENTE FUNCIONAL**
- TLS 1.0 handshake: ✅
- Proxy bidireccional: ✅
- Cliente recibe respuestas: ✅
- Listo para autenticación XMPP: ✅

**Próximos Pasos**:
- Verificar autenticación SASL completa
- Monitorear performance con múltiples clientes
- Considerar implementar métricas/monitoring
- Documentar casos de uso adicionales

---

**Autor**: Claude (Anthropic)
**Fecha de Documentación**: 7 de diciembre de 2025
**Versión**: 1.0
