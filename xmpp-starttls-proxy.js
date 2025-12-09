#!/usr/bin/env node

/**
 * XMPP STARTTLS Proxy for BlackBerry 10
 * 
 * This proxy handles the STARTTLS negotiation with support for old TLS versions
 */

const net = require('net');
const tls = require('tls');
const fs = require('fs');
const crypto = require('crypto');

// Enable legacy TLS protocols in OpenSSL 3
process.env.OPENSSL_CONF = '/dev/null'; // Disable OpenSSL config restrictions
crypto.DEFAULT_MIN_VERSION = 'TLSv1'; // Allow TLS 1.0+

// Configuration
const CONFIG = {
  listenPort: 5222,
  listenHost: '0.0.0.0',
  certFile: '/etc/prosody/certs/whatsberry.descarga.media.crt',
  keyFile: '/etc/prosody/certs/whatsberry.descarga.media.key',
  prosodyHost: '127.0.0.1',
  prosodyPort: 5200,
  minTLSVersion: 'TLSv1',
  maxTLSVersion: 'TLSv1.3'
};

// Load TLS credentials
let tlsOptions;
try {
  tlsOptions = {
    cert: fs.readFileSync(CONFIG.certFile),
    key: fs.readFileSync(CONFIG.keyFile),
    minVersion: CONFIG.minTLSVersion,
    maxVersion: CONFIG.maxTLSVersion,
    requestCert: false,
    rejectUnauthorized: false,
    // OpenSSL 3 specific options to allow old protocols
    secureOptions: crypto.constants.SSL_OP_NO_SSLv2 | crypto.constants.SSL_OP_NO_SSLv3
  };
  console.log('[INIT] Loaded TLS certificate from:', CONFIG.certFile);
} catch (err) {
  console.error('[FATAL] Failed to load TLS certificates:', err.message);
  process.exit(1);
}

let connectionId = 0;

const server = net.createServer((clientSocket) => {
  const connId = ++connectionId;
  const clientAddr = `${clientSocket.remoteAddress}:${clientSocket.remotePort}`;
  
  console.log(`[${connId}] NEW connection from ${clientAddr}`);
  
  let tlsUpgraded = false;
  let clientBytesSent = 0;
  let prosodyBytesSent = 0;
  let prosodyDataHandler = null;

  const prosodySocket = net.createConnection({
    host: CONFIG.prosodyHost,
    port: CONFIG.prosodyPort
  }, () => {
    console.log(`[${connId}] Connected to Prosody`);
  });

  // Add drain and error monitoring
  prosodySocket.on('drain', () => {
    console.log(`[${connId}] Prosody socket drained`);
  });

  prosodySocket.on('error', (err) => {
    console.error(`[${connId}] Prosody error:`, err.message);
    if (!clientSocket.destroyed) clientSocket.destroy();
  });

  clientSocket.on('error', (err) => {
    console.error(`[${connId}] Client error:`, err.message);
    if (!prosodySocket.destroyed) prosodySocket.destroy();
  });

  prosodySocket.on('close', () => {
    console.log(`[${connId}] Prosody closed`);
    if (!clientSocket.destroyed) clientSocket.destroy();
  });

  clientSocket.on('close', () => {
    console.log(`[${connId}] Client closed - Sent: ${clientBytesSent}B, Received: ${prosodyBytesSent}B`);
    if (!prosodySocket.destroyed) prosodySocket.destroy();
  });

  // Data from client (before TLS upgrade)
  clientSocket.on('data', (data) => {
    if (tlsUpgraded) return;

    const dataStr = data.toString('utf8');

    if (dataStr.includes('<starttls')) {
      console.log(`[${connId}] STARTTLS requested by client`);

      // Remove the old Prosody data handler before TLS upgrade
      if (prosodyDataHandler) {
        prosodySocket.removeListener('data', prosodyDataHandler);
        console.log(`[${connId}] Removed old Prosody data handler`);
      }

      // Remove all event listeners from old Prosody socket to prevent interference
      prosodySocket.removeAllListeners();
      console.log(`[${connId}] Removed all Prosody listeners`);

      // Close the old Prosody connection - we'll create a new one after TLS
      prosodySocket.destroy();
      console.log(`[${connId}] Closed old Prosody connection`);

      // Create TLS socket wrapping the existing socket
      const tlsSocket = new tls.TLSSocket(clientSocket, {
        isServer: true,
        ...tlsOptions
      });

      tlsSocket.on('secure', () => {
        try {
          const protocol = tlsSocket.getProtocol();
          const cipher = tlsSocket.getCipher();
          console.log(`[${connId}] ✓ TLS established: ${protocol}, Cipher: ${cipher.name}`);
          tlsUpgraded = true;

          // Create NEW connection to Prosody for post-TLS communication
          const newProsodySocket = net.createConnection({
            host: CONFIG.prosodyHost,
            port: CONFIG.prosodyPort
          }, () => {
            console.log(`[${connId}] New Prosody connection established after TLS`);
          });

          newProsodySocket.on('error', (err) => {
            console.error(`[${connId}] New Prosody error:`, err.message);
            if (!tlsSocket.destroyed) tlsSocket.destroy();
          });

          newProsodySocket.on('close', () => {
            console.log(`[${connId}] New Prosody closed`);
            if (!tlsSocket.destroyed) tlsSocket.destroy();
          });

          // Prosody → Client
          newProsodySocket.on('data', (prosodyData) => {
            prosodyBytesSent += prosodyData.length;
            console.log(`[${connId}] Prosody→Client: ${prosodyData.length}B`);
            if (!tlsSocket.destroyed) {
              tlsSocket.write(prosodyData);
            }
          });

          // Client → Prosody
          tlsSocket.on('data', (tlsData) => {
            clientBytesSent += tlsData.length;
            console.log(`[${connId}] Client→Prosody: ${tlsData.length}B (decrypted)`);
            console.log(`[${connId}] Data preview: ${tlsData.toString('utf8').substring(0, 100)}...`);
            if (!newProsodySocket.destroyed && !newProsodySocket.writableEnded) {
              newProsodySocket.write(tlsData);
              console.log(`[${connId}] Wrote to new Prosody connection`);
            } else {
              console.error(`[${connId}] Cannot write to new Prosody! Socket not writable`);
            }
          });

          console.log(`[${connId}] Bidirectional proxy established with new Prosody connection`);

          tlsSocket.on('error', (err) => {
            console.error(`[${connId}] TLS socket error:`, err.message);
            if (!newProsodySocket.destroyed) newProsodySocket.destroy();
          });

          tlsSocket.on('close', () => {
            console.log(`[${connId}] TLS socket closed`);
            if (!newProsodySocket.destroyed) newProsodySocket.destroy();
          });
        } catch (err) {
          console.error(`[${connId}] Error in secure event:`, err.message);
          if (!clientSocket.destroyed) clientSocket.destroy();
        }
      });

      tlsSocket.on('error', (err) => {
        console.log(`[${connId}] TLS upgrade error:`, err.message);
        // prosodySocket already destroyed before TLS upgrade
        if (!clientSocket.destroyed) clientSocket.destroy();
      });

      // Send <proceed/> to client to start TLS handshake
      const proceedResponse = "<proceed xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>";
      console.log(`[${connId}] Sending PROCEED to client...`);
      clientSocket.write(proceedResponse);
    } else {
      // Forward to Prosody
      clientBytesSent += data.length;
      if (!prosodySocket.destroyed) {
        prosodySocket.write(data);
      }
    }
  });

  // Data from Prosody (before TLS) - store handler reference for removal
  prosodyDataHandler = (data) => {
    if (!tlsUpgraded) {
      prosodyBytesSent += data.length;

      let dataStr = data.toString('utf8');

      // If Prosody sends stream features WITHOUT starttls, inject it
      if (dataStr.includes('<stream:features>') && !dataStr.includes('<starttls')) {
        console.log(`[${connId}] Injecting STARTTLS into Prosody features`);
        // Insert starttls feature before closing </stream:features>
        dataStr = dataStr.replace(
          '</stream:features>',
          "<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/></stream:features>"
        );
        data = Buffer.from(dataStr, 'utf8');
      } else if (dataStr.includes('<starttls')) {
        console.log(`[${connId}] Prosody offered STARTTLS`);
      }

      if (!clientSocket.destroyed) {
        clientSocket.write(data);
      }
    }
  };

  prosodySocket.on('data', prosodyDataHandler);
});

server.on('error', (err) => {
  console.error('[ERROR] Server error:', err.message);
  if (err.code === 'EADDRINUSE') {
    console.error(`[FATAL] Port ${CONFIG.listenPort} is already in use`);
    process.exit(1);
  }
});

server.listen(CONFIG.listenPort, CONFIG.listenHost, () => {
  console.log('='.repeat(60));
  console.log('XMPP STARTTLS Proxy for BlackBerry 10');
  console.log('='.repeat(60));
  console.log(`Listening on: ${CONFIG.listenHost}:${CONFIG.listenPort}`);
  console.log(`Backend: ${CONFIG.prosodyHost}:${CONFIG.prosodyPort}`);
  console.log(`TLS support: ${CONFIG.minTLSVersion} - ${CONFIG.maxTLSVersion}`);
  console.log(`Certificate: ${CONFIG.certFile}`);
  console.log(`OpenSSL legacy protocols: ENABLED`);
  console.log('='.repeat(60));
});

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);

function shutdown() {
  console.log('\n[SHUTDOWN] Closing server...');
  server.close(() => {
    console.log('[SHUTDOWN] Server closed');
    process.exit(0);
  });
}
