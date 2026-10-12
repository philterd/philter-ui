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

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class LoggingHttpClientTest {

    @Test
    void failedResponsesAreLoggedWithTheirRequestId() {
        assertEquals("Philter answered PUT /api/policies/claims with HTTP 409 (request id req-7f3a)",
                LoggingHttpClient.describe("PUT", "/api/policies/claims", 409, "req-7f3a"));
        assertEquals("Philter answered GET /api/limits with HTTP 503 (request id none)",
                LoggingHttpClient.describe("GET", "/api/limits", 503, null));
        assertNull(LoggingHttpClient.describe("GET", "/api/limits", 200, "req-1"));
        assertNull(LoggingHttpClient.describe("DELETE", "/api/policies/claims", 204, "req-2"));
    }

    @Test
    void theClientIsConfiguredAsTheSdkConfiguresItsOwn() {
        final HttpClient client = LoggingHttpClient.builder(12).build();
        assertInstanceOf(LoggingHttpClient.class, client);
        assertEquals(Optional.of(Duration.ofSeconds(12)), client.connectTimeout());
        assertEquals(HttpClient.Redirect.NORMAL, client.followRedirects());
        assertEquals(HttpClient.Version.HTTP_1_1, client.version());
    }

}
