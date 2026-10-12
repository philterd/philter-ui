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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import java.net.http.HttpClient;

/**
 * Builds clients for the Philter instance at {@code PHILTER_URL}. A person's clients send their browser's
 * address with each request, so Philter records their address, where an audit event has one, rather than
 * Philter UI's. Every client uses the same TLS context, from the client certificate and trust store settings
 * when either is set, or the JDK's defaults when neither is.
 */
@Component
public class PhilterClients {

    /** How long a document redaction may take, in seconds, unless {@code DOCUMENT_TIMEOUT_SECONDS} says otherwise. */
    public static final long DEFAULT_DOCUMENT_TIMEOUT_SECONDS = 300;

    private final String endpoint;
    private final long documentTimeoutSeconds;
    private final ClientAddresses addresses;
    /** The TLS context for every client, or {@code null} for the JDK's defaults. */
    private final SSLContext sslContext;
    private final PhilterClient anonymous;

    /** Reads the client certificate and trust store now, so a keystore that cannot be used stops startup. */
    @Autowired
    public PhilterClients(@Value("${philter.url}") final String endpoint,
                          @Value("${philter.document-timeout-seconds}") final long documentTimeoutSeconds,
                          final ClientAddresses addresses,
                          @Value("${philter.client-certificate}") final String clientCertificate,
                          @Value("${philter.client-certificate-password}") final String clientCertificatePassword,
                          @Value("${philter.trust-store}") final String trustStore,
                          @Value("${philter.trust-store-password}") final String trustStorePassword) {
        this(endpoint, documentTimeoutSeconds, addresses, PhilterTlsSettings.sslContext(clientCertificate,
                clientCertificatePassword, trustStore, trustStorePassword));
    }

    PhilterClients(final String endpoint, final long documentTimeoutSeconds, final ClientAddresses addresses,
                   final SSLContext sslContext) {
        this.endpoint = endpoint;
        this.documentTimeoutSeconds = documentTimeoutSeconds;
        this.addresses = addresses;
        this.sslContext = sslContext;
        this.anonymous = new PhilterClient.PhilterClientBuilder().withEndpoint(endpoint)
                .withHttpClientBuilder(http(PhilterClient.DEFAULT_TIMEOUT_SEC)).build();
    }

    public PhilterClients(final String endpoint) {
        this(endpoint, DEFAULT_DOCUMENT_TIMEOUT_SECONDS, new ClientAddresses(""), null);
    }

    /** A client with no API key, for signing in. */
    public PhilterClient anonymous() {
        return anonymous;
    }

    /** A client that sends the person's session key, built once per signed-in person. */
    public PhilterClient forUser(final PhilterUser user) {
        return user.client(() -> forSessionKey(user.sessionKey()));
    }

    /**
     * A client for redacting a document synchronously, which can take longer than the SDK's default
     * timeout allows. Built once per signed-in person, like {@link #forUser}.
     */
    public PhilterClient forDocuments(final PhilterUser user) {
        return user.documentClient(() -> new PhilterClient.PhilterClientBuilder()
                .withEndpoint(endpoint)
                .withHttpClientBuilder(http(documentTimeoutSeconds))
                .withApiKey("Bearer " + user.sessionKey())
                .withTimeout(documentTimeoutSeconds)
                .withClientAddress(addresses::current)
                .build());
    }

    /**
     * A client that sends the session key. The browser's address is read for each request, since it can change
     * during a session; outside a browser request, such as signing out a session that timed out, none is sent.
     */
    PhilterClient forSessionKey(final String sessionKey) {
        return new PhilterClient.PhilterClientBuilder()
                .withEndpoint(endpoint)
                .withHttpClientBuilder(http(PhilterClient.DEFAULT_TIMEOUT_SEC))
                .withApiKey("Bearer " + sessionKey)
                .withClientAddress(addresses::current)
                .build();
    }

    /**
     * The HTTP client builder for a Philter client, which logs failed responses. The SDK's own TLS options cannot be
     * combined with a supplied builder, so the TLS context is set here.
     */
    private HttpClient.Builder http(final long timeoutSeconds) {
        final HttpClient.Builder builder = LoggingHttpClient.builder(timeoutSeconds);
        if (sslContext != null) {
            builder.sslContext(sslContext);
        }
        return builder;
    }

}
