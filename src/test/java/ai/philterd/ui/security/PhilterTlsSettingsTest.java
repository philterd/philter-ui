/*
 *     Copyright 2026 Philterd, LLC @ https://www.philterd.ai
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.philterd.ui.security;

import ai.philterd.philter.PhilterClient;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Connects Philter UI's clients to a local HTTPS server, some of whose tests require a client certificate. The
 * keystores are generated with the JDK's {@code keytool} for each run, so no private key is kept in the repository.
 */
class PhilterTlsSettingsTest {

    private static final String PASSWORD = "ui-test-store-password";

    @TempDir
    static Path dir;

    /** The server's key, for {@code localhost}. */
    private static Path serverKeyStore;
    /** A server key for another host, to show that hostname verification stays on. */
    private static Path otherHostKeyStore;
    /** The client certificate the server trusts, as PKCS12 and JKS. */
    private static Path clientKeyStore;
    private static Path clientJks;
    /** What Philter UI trusts: both server certificates. */
    private static Path clientTrustStore;
    /** The same, as JKS, whose certificates can be read without its password. */
    private static Path clientTrustJks;
    /** What the server trusts: the client certificate. */
    private static Path serverTrustStore;

    private HttpsServer server;
    /** The status the server answers with. */
    private volatile int status = 200;

    @BeforeAll
    static void generateKeyStores() throws Exception {

        serverKeyStore = keyPair("server.p12", "CN=localhost", "SAN=dns:localhost,ip:127.0.0.1");
        otherHostKeyStore = keyPair("other-host.p12", "CN=philter.invalid", "SAN=dns:philter.invalid");
        clientKeyStore = keyPair("client.p12", "CN=philter-ui-test-client", null);

        clientTrustStore = dir.resolve("client-trust.p12");
        trust(clientTrustStore, "server", exportCertificate(serverKeyStore, "server.cer"));
        trust(clientTrustStore, "other-host", exportCertificate(otherHostKeyStore, "other-host.cer"));

        clientTrustJks = dir.resolve("client-trust.jks");
        keytool("-importcert", "-noprompt", "-alias", "server", "-file", dir.resolve("server.cer").toString(),
                "-keystore", clientTrustJks.toString(), "-storetype", "JKS", "-storepass", PASSWORD);

        serverTrustStore = dir.resolve("server-trust.p12");
        trust(serverTrustStore, "client", exportCertificate(clientKeyStore, "client.cer"));

        clientJks = dir.resolve("client.jks");
        keytool("-importkeystore", "-srckeystore", clientKeyStore.toString(), "-srcstoretype", "PKCS12",
                "-srcstorepass", PASSWORD, "-destkeystore", clientJks.toString(), "-deststoretype", "JKS",
                "-deststorepass", PASSWORD, "-destkeypass", PASSWORD, "-noprompt");

    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    // Startup.

    @Test
    void withNoSettingsTheJdkDefaultsApply() throws Exception {
        assertNull(PhilterTlsSettings.sslContext("", "", "", ""));
        assertNull(PhilterTlsSettings.sslContext(null, null, null, null));
        // Not even a context of Philter UI's own, so -Djavax.net.ssl.trustStore is honored as before.
        final PhilterClients clients = clients("https://localhost:8080", "", "", "", "");
        assertSame(HttpClient.newHttpClient().sslContext(), httpClientOf(clients.anonymous()).sslContext());
    }

    @Test
    void aClientCertificateAloneIsRead() {
        assertInstanceOf(SSLContext.class, PhilterTlsSettings.sslContext(clientKeyStore.toString(), PASSWORD, "", ""));
        assertInstanceOf(SSLContext.class, PhilterTlsSettings.sslContext(clientJks.toString(), PASSWORD, "", ""));
    }

    @Test
    void aTrustStoreAloneIsRead() {
        assertInstanceOf(SSLContext.class, PhilterTlsSettings.sslContext("", "", clientTrustStore.toString(), PASSWORD));
        assertInstanceOf(SSLContext.class, PhilterTlsSettings.sslContext("", "", clientTrustJks.toString(), PASSWORD));
        // Its password is optional for a store whose certificates can be read without it.
        assertInstanceOf(SSLContext.class, PhilterTlsSettings.sslContext("", "", clientTrustJks.toString(), ""));
    }

    @Test
    void aPkcs12TrustStoreWithoutItsPasswordAsksForIt() {
        // Its certificates are encrypted with its password, so without it the store reads as empty.
        final IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> clients("https://localhost:8080", "", "", clientTrustStore.toString(), ""));
        assertRefusal(ex, "PHILTER_TRUST_STORE_PASSWORD", clientTrustStore);
    }

    @Test
    void aMissingClientCertificateStopsStartup() {
        final Path missing = dir.resolve("missing.p12");
        final IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> clients("https://localhost:8080", missing.toString(), PASSWORD, "", ""));
        assertRefusal(ex, "PHILTER_CLIENT_CERTIFICATE", missing);
    }

    @Test
    void aClientCertificateWithTheWrongPasswordStopsStartup() {
        final IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> clients("https://localhost:8080", clientKeyStore.toString(), "not-the-password", "", ""));
        assertRefusal(ex, "PHILTER_CLIENT_CERTIFICATE", clientKeyStore);
        assertNoPassword(ex, "not-the-password");
    }

    @Test
    void aClientCertificateWithoutItsPasswordStopsStartup() {
        final IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> clients("https://localhost:8080", clientKeyStore.toString(), "", "", ""));
        assertRefusal(ex, "PHILTER_CLIENT_CERTIFICATE_PASSWORD", clientKeyStore);
    }

    @Test
    void aClientCertificateWithoutAPrivateKeyStopsStartup() {
        final IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> clients("https://localhost:8080", clientTrustStore.toString(), PASSWORD, "", ""));
        assertRefusal(ex, "PHILTER_CLIENT_CERTIFICATE", clientTrustStore);
    }

    @Test
    void anUnreadableTrustStoreStopsStartup() throws Exception {
        final Path notAKeyStore = Files.writeString(dir.resolve("not-a-keystore.p12"), "not a keystore");
        final IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> clients("https://localhost:8080", "", "", notAKeyStore.toString(), PASSWORD));
        assertRefusal(ex, "PHILTER_TRUST_STORE", notAKeyStore);
        assertNoPassword(ex, PASSWORD);
    }

    @Test
    void aTrustStoreWithTheWrongPasswordStopsStartup() {
        // A good client certificate does not hide a bad trust store.
        final IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> clients("https://localhost:8080", clientKeyStore.toString(), PASSWORD,
                        clientTrustStore.toString(), "not-the-password"));
        assertRefusal(ex, "PHILTER_TRUST_STORE", clientTrustStore);
        assertFalse(ex.getMessage().contains("PHILTER_CLIENT_CERTIFICATE"), ex.getMessage());
        assertNoPassword(ex, "not-the-password");
    }

    // Connections.

    @Test
    void everyClientPresentsTheClientCertificate() throws Exception {

        startServer(serverKeyStore, true);

        final PhilterClients clients = clients(url(), clientKeyStore.toString(), PASSWORD,
                clientTrustStore.toString(), PASSWORD);
        final PhilterUser user = new PhilterUser("jordan", false, PhilterUser.Restriction.NONE, "sk_jordan", "id-jordan");

        for (final PhilterClient client : List.of(clients.anonymous(), clients.forUser(user), clients.forDocuments(user))) {
            assertEquals("UP", client.health().getStatus());
            assertInstanceOf(LoggingHttpClient.class, httpClientOf(client));
        }

        // One context for all of them.
        final SSLContext context = httpClientOf(clients.anonymous()).sslContext();
        assertSame(context, httpClientOf(clients.forUser(user)).sslContext());
        assertSame(context, httpClientOf(clients.forDocuments(user)).sslContext());

    }

    @Test
    void withoutAClientCertificateTheServerRefuses() {
        startServer(serverKeyStore, true);
        final PhilterClients clients = clients(url(), "", "", clientTrustStore.toString(), PASSWORD);
        assertThrows(IOException.class, () -> clients.anonymous().health());
    }

    @Test
    void aTrustStoreAloneReachesAServerWithAPrivateCertificate() throws Exception {
        startServer(serverKeyStore, false);
        final PhilterClients clients = clients(url(), "", "", clientTrustStore.toString(), PASSWORD);
        assertEquals("UP", clients.anonymous().health().getStatus());
    }

    @Test
    void withoutATrustStoreAPrivateCertificateIsRefused() {
        startServer(serverKeyStore, false);
        // The JVM's default trust store does not hold the test server's self-signed certificate.
        assertThrows(IOException.class, () -> new PhilterClients(url()).anonymous().health());
    }

    @Test
    void aClientCertificateAloneUsesTheJvmsTrust() {
        startServer(serverKeyStore, true);
        // The client certificate is read, but the server's certificate is not in the JVM's default trust store.
        final PhilterClients clients = clients(url(), clientKeyStore.toString(), PASSWORD, "", "");
        assertThrows(IOException.class, () -> clients.anonymous().health());
    }

    @Test
    void aCertificateForAnotherHostIsRefused() {
        // Trusted, but issued to philter.invalid rather than localhost.
        startServer(otherHostKeyStore, false);
        final PhilterClients clients = clients(url(), "", "", clientTrustStore.toString(), PASSWORD);
        final IOException ex = assertThrows(IOException.class, () -> clients.anonymous().health());
        assertTrue(causedBy(ex, CertificateException.class), String.valueOf(ex));
    }

    @Test
    void failedResponsesAreStillLogged() {

        startServer(serverKeyStore, true);
        status = 503;

        final Logger logger = (Logger) LoggerFactory.getLogger(LoggingHttpClient.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            final PhilterClients clients = clients(url(), clientKeyStore.toString(), PASSWORD,
                    clientTrustStore.toString(), PASSWORD);
            assertThrows(Exception.class, () -> clients.anonymous().health());
        } finally {
            logger.detachAppender(appender);
        }

        assertEquals(List.of("Philter answered GET /api/health with HTTP 503 (request id req-tls-1)"),
                appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList());

    }

    private static PhilterClients clients(final String url, final String clientCertificate,
                                          final String clientCertificatePassword, final String trustStore,
                                          final String trustStorePassword) {
        return new PhilterClients(url, PhilterClients.DEFAULT_DOCUMENT_TIMEOUT_SECONDS, new ClientAddresses(""),
                clientCertificate, clientCertificatePassword, trustStore, trustStorePassword);
    }

    private String url() {
        return "https://localhost:" + server.getAddress().getPort();
    }

    /** Starts an HTTPS server answering {@code /api/health}, requiring a client certificate when asked. */
    private void startServer(final Path keyStore, final boolean requireClientCertificate) {

        try {

            server = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
            server.setHttpsConfigurator(new HttpsConfigurator(serverContext(keyStore)) {
                @Override
                public void configure(final HttpsParameters parameters) {
                    final SSLParameters ssl = getSSLContext().getDefaultSSLParameters();
                    ssl.setNeedClientAuth(requireClientCertificate);
                    parameters.setSSLParameters(ssl);
                }
            });

            server.createContext("/", exchange -> {
                final byte[] body = (status == 200 ? "{\"status\":\"UP\"}" : "{\"message\":\"Unavailable\"}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.getResponseHeaders().add(LoggingHttpClient.REQUEST_ID, "req-tls-1");
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });

            server.start();

        } catch (final Exception ex) {
            throw new IllegalStateException(ex);
        }

    }

    private static SSLContext serverContext(final Path keyStore) throws Exception {

        final KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(KeyStore.getInstance(keyStore.toFile(), PASSWORD.toCharArray()), PASSWORD.toCharArray());

        final TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(KeyStore.getInstance(serverTrustStore.toFile(), PASSWORD.toCharArray()));

        final SSLContext context = SSLContext.getInstance("TLS");
        context.init(keys.getKeyManagers(), trust.getTrustManagers(), null);
        return context;

    }

    private static HttpClient httpClientOf(final PhilterClient client) throws Exception {
        final Field field = PhilterClient.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        return (HttpClient) field.get(client);
    }

    private static boolean causedBy(final Throwable ex, final Class<? extends Throwable> type) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }

    private static void assertRefusal(final IllegalStateException ex, final String setting, final Path file) {
        assertTrue(ex.getMessage().contains(setting), ex.getMessage());
        assertTrue(ex.getMessage().contains(file.toString()), ex.getMessage());
    }

    private static void assertNoPassword(final Throwable ex, final String password) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            assertFalse(String.valueOf(t.getMessage()).contains(password), "The password appears in " + t);
        }
    }

    private static Path keyPair(final String name, final String dname, final String san) throws Exception {

        final Path keyStore = dir.resolve(name);
        final List<String> args = new ArrayList<>(Arrays.asList("-genkeypair", "-alias", "key", "-keyalg", "RSA",
                "-keysize", "2048", "-validity", "2", "-dname", dname, "-keystore", keyStore.toString(),
                "-storetype", "PKCS12", "-storepass", PASSWORD, "-keypass", PASSWORD));
        if (san != null) {
            args.add("-ext");
            args.add(san);
        }
        keytool(args.toArray(new String[0]));
        return keyStore;

    }

    private static Path exportCertificate(final Path keyStore, final String name) throws Exception {
        final Path certificate = dir.resolve(name);
        keytool("-exportcert", "-alias", "key", "-keystore", keyStore.toString(), "-storepass", PASSWORD,
                "-file", certificate.toString());
        return certificate;
    }

    private static void trust(final Path trustStore, final String alias, final Path certificate) throws Exception {
        keytool("-importcert", "-noprompt", "-alias", alias, "-file", certificate.toString(),
                "-keystore", trustStore.toString(), "-storetype", "PKCS12", "-storepass", PASSWORD);
    }

    private static void keytool(final String... args) throws Exception {

        final Path bin = Path.of(System.getProperty("java.home"), "bin");
        final Path keytool = Files.exists(bin.resolve("keytool")) ? bin.resolve("keytool") : bin.resolve("keytool.exe");

        final List<String> command = new ArrayList<>();
        command.add(keytool.toString());
        command.addAll(Arrays.asList(args));

        final Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        final String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException("keytool " + args[0] + " failed: " + output);
        }

    }

}
