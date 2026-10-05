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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SessionKeyRevokerTest {

    @Test
    void revokesAKeyOnce() throws Exception {
        try (FakePhilter philter = new FakePhilter()) {
            final SessionKeyRevoker revoker = new SessionKeyRevoker(new PhilterClients(philter.url()));
            final PhilterUser user = new PhilterUser("jordan", false, PhilterUser.Restriction.NONE, "sk_jordan");
            revoker.revoke(user);
            revoker.revoke(user);
            assertEquals(List.of("sk_jordan"), philter.revokedKeys);
        }
    }

    @Test
    void anAlreadyRejectedKeyIsNotAnError() throws Exception {
        try (FakePhilter philter = new FakePhilter()) {
            final SessionKeyRevoker revoker = new SessionKeyRevoker(new PhilterClients(philter.url()));
            final PhilterUser user = new PhilterUser("jordan", false, PhilterUser.Restriction.NONE, "sk_expired");
            revoker.revoke(user);
            assertFalse(user.markEnded());
        }
    }

    @Test
    void theSessionKeyIsNotInThePrincipalsDescription() {
        final PhilterUser user = new PhilterUser("jordan", true, PhilterUser.Restriction.NONE, "sk_secret");
        assertFalse(user.toString().contains("sk_secret"));
    }

}
