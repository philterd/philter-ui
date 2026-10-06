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

/**
 * Builds clients for the Philter instance at {@code PHILTER_URL}. A person's clients send their browser's
 * address with each request, so Philter records their address, where an audit event has one, rather than
 * Philter UI's.
 */
@Component
public class PhilterClients {

    /** How long a document redaction may take, in seconds, unless {@code DOCUMENT_TIMEOUT_SECONDS} says otherwise. */
    public static final long DEFAULT_DOCUMENT_TIMEOUT_SECONDS = 300;

    private final String endpoint;
    private final long documentTimeoutSeconds;
    private final ClientAddresses addresses;
    private final PhilterClient anonymous;

    @Autowired
    public PhilterClients(@Value("${philter.url}") final String endpoint,
                          @Value("${philter.document-timeout-seconds}") final long documentTimeoutSeconds,
                          final ClientAddresses addresses) {
        this.endpoint = endpoint;
        this.documentTimeoutSeconds = documentTimeoutSeconds;
        this.addresses = addresses;
        this.anonymous = new PhilterClient.PhilterClientBuilder().withEndpoint(endpoint).build();
    }

    public PhilterClients(final String endpoint) {
        this(endpoint, DEFAULT_DOCUMENT_TIMEOUT_SECONDS, new ClientAddresses(""));
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
                .withApiKey("Bearer " + sessionKey)
                .withClientAddress(addresses::current)
                .build();
    }

}
