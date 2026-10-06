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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SessionKeyRevokerTest {

    @Test
    void revokesAKeyOnce() throws Exception {
        try (FakePhilter philter = new FakePhilter()) {
            final SessionKeyRevoker revoker = new SessionKeyRevoker(new PhilterClients(philter.url()));
            final PhilterUser user = new PhilterUser("jordan", false, PhilterUser.Restriction.NONE, "sk_jordan", "id-jordan");
            revoker.revoke(user);
            revoker.revoke(user);
            assertEquals(List.of("sk_jordan"), philter.revokedKeys);
        }
    }

    @Test
    void anAlreadyRejectedKeyIsNotAnError() throws Exception {
        try (FakePhilter philter = new FakePhilter()) {
            final SessionKeyRevoker revoker = new SessionKeyRevoker(new PhilterClients(philter.url()));
            final PhilterUser user = new PhilterUser("jordan", false, PhilterUser.Restriction.NONE, "sk_expired", "id-expired");
            revoker.revoke(user);
            assertFalse(user.markEnded());
        }
    }

    @Test
    void theSessionKeyAndItsIdAreNotInThePrincipalsDescription() {
        final PhilterUser user = new PhilterUser("jordan", true, PhilterUser.Restriction.NONE, "sk_secret", "id-secret");
        assertFalse(user.toString().contains("sk_secret"));
        assertFalse(user.toString().contains("id-secret"));
    }

    @Test
    void aPersonsRequestsCarryTheirAddress() throws Exception {
        try (FakePhilter philter = new FakePhilter()) {
            final PhilterClients clients = new PhilterClients(philter.url(),
                    PhilterClients.DEFAULT_DOCUMENT_TIMEOUT_SECONDS, new ClientAddresses("10.0.0.0/8"));
            final PhilterUser user = new PhilterUser("jordan", false, PhilterUser.Restriction.NONE, "sk_jordan", "id-jordan");

            // Through a trusted proxy, from 203.0.113.7, and then from 198.51.100.4 with the same client.
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request("203.0.113.7")));
            clients.forUser(user).getCurrentUser();
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request("198.51.100.4")));
            clients.forUser(user).getCurrentUser();

            // Outside a browser request, as when a timed-out session is signed out, none is sent.
            RequestContextHolder.resetRequestAttributes();
            new SessionKeyRevoker(clients).revoke(user);

            assertEquals(List.of("/api/users/me 203.0.113.7", "/api/users/me 198.51.100.4",
                    "/api/api-keys/current none"), philter.sessionRequests);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    private static MockHttpServletRequest request(final String browser) {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.2");
        request.addHeader("X-Forwarded-For", browser);
        return request;
    }

}
