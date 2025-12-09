package com.whatsberry.xmpp;

import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Custom SSLSocketFactory to enable TLS 1.2 on Android API 18 (BlackBerry 10)
 * Android API 18 supports TLS 1.2 but doesn't enable it by default
 */
public class TLSSocketFactory extends SSLSocketFactory {
    private static final String TAG = "TLSSocketFactory";
    private SSLSocketFactory internalSSLSocketFactory;

    public TLSSocketFactory() throws KeyManagementException, NoSuchAlgorithmException {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, null, null);
        internalSSLSocketFactory = context.getSocketFactory();
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return internalSSLSocketFactory.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return internalSSLSocketFactory.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket() throws IOException {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket());
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) 
            throws IOException {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket(s, host, port, autoClose));
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) 
            throws IOException {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port, localHost, localPort));
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port));
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) 
            throws IOException {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket(address, port, localAddress, localPort));
    }

    /**
     * Enable TLS 1.2 and TLS 1.1 on the socket
     */
    private Socket enableTLSOnSocket(Socket socket) {
        if (socket != null && (socket instanceof SSLSocket)) {
            SSLSocket sslSocket = (SSLSocket) socket;
            
            // Log supported protocols for debugging
            String[] supportedProtocols = sslSocket.getSupportedProtocols();
            Log.d(TAG, "Supported protocols: " + java.util.Arrays.toString(supportedProtocols));
            
            // Enable TLS 1.2 and 1.1 explicitly
            sslSocket.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.1"});
            
            String[] enabledProtocols = sslSocket.getEnabledProtocols();
            Log.d(TAG, "Enabled protocols: " + java.util.Arrays.toString(enabledProtocols));
        }
        return socket;
    }
}
