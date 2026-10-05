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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Builds clients for the Philter instance at {@code PHILTER_URL}. */
@Component
public class PhilterClients {

    private final String endpoint;
    private final PhilterClient anonymous;

    public PhilterClients(@Value("${philter.url}") final String endpoint) {
        this.endpoint = endpoint;
        this.anonymous = new PhilterClient.PhilterClientBuilder().withEndpoint(endpoint).build();
    }

    /** A client with no API key, for signing in. */
    public PhilterClient anonymous() {
        return anonymous;
    }

    /** A client that sends the person's session key, built once per signed-in person. */
    public PhilterClient forUser(final PhilterUser user) {
        return user.client(() -> forSessionKey(user.sessionKey()));
    }

    PhilterClient forSessionKey(final String sessionKey) {
        return new PhilterClient.PhilterClientBuilder()
                .withEndpoint(endpoint)
                .withApiKey("Bearer " + sessionKey)
                .build();
    }

}
