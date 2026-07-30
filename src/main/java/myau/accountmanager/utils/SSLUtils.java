package myau.accountmanager.utils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;

/*
 * This file is derived from https://github.com/ksyzov/AccountManager.
 * Originally licensed under the GNU LGPL.
 *
 * This modified version is licensed under the GNU GPL v3.
 */
public final class SSLUtils {
    private static final SSLContext ctx = createSSLContext();

    private SSLUtils() {
    }

    public static SSLContext getSSLContext() {
        return ctx;
    }

    private static SSLContext createSSLContext() {
        try (InputStream stream = SSLUtils.class.getResourceAsStream("/ssl.jks")) {
            if (stream != null) {
                KeyStore jks = KeyStore.getInstance("JKS");
                jks.load(stream, "changeit".toCharArray());

                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(jks);

                SSLContext customContext = SSLContext.getInstance("TLS");
                customContext.init(null, tmf.getTrustManagers(), null);
                return customContext;
            }
        } catch (Exception e) {
            System.err.println("Unable to load the bundled SSL trust store; using the Java trust store instead: " + e.getMessage());
        }

        try {
            return SSLContext.getDefault();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to initialize the system SSL context", e);
        }
    }
}
