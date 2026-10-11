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
package ai.philterd.ui.views;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountViewTest {

    @Test
    void scopesCanBeRemovedButNotAdded() {
        final Set<String> current = Set.of("redact", "policies:read", "contexts:read");
        assertNull(AccountView.scopeChangeProblem(current, Set.of("redact")));
        assertNull(AccountView.scopeChangeProblem(current, current));
        assertEquals("A sign-in session cannot add scopes to a key: audit:read.",
                AccountView.scopeChangeProblem(current, Set.of("redact", "audit:read")));
        assertEquals("A key needs at least one scope. To stop using it, revoke it.",
                AccountView.scopeChangeProblem(current, Set.of()));
    }

    @Test
    void webhooksNeedAnHttpUrlAndALongEnoughSecret() {
        final String secret = "x".repeat(16);
        assertNull(AccountView.webhookProblem("https://hooks.example.com/philter", secret, 16));
        assertNull(AccountView.webhookProblem("http://10.0.0.5:8080/hook", secret, 16));
        assertEquals("Enter a URL.", AccountView.webhookProblem(" ", secret, 16));
        assertEquals("The URL must be an http or https address.", AccountView.webhookProblem("ftp://example.com/x", secret, 16));
        assertEquals("The URL must be an http or https address.", AccountView.webhookProblem("not a url", secret, 16));
        assertTrue(AccountView.webhookProblem("https://hooks.example.com/philter", "short", 16).startsWith("Enter a secret of at least 16"));
        assertTrue(AccountView.webhookProblem("https://hooks.example.com/philter", null, 16).startsWith("Enter a secret"));
    }

    @Test
    void anUnknownMinimumLeavesTheSecretLengthToPhilter() {
        assertNull(AccountView.webhookProblem("https://hooks.example.com/philter", "short", null));
        assertTrue(AccountView.webhookProblem("https://hooks.example.com/philter", "", null).startsWith("Enter a secret"));
    }

    @Test
    void generatedSecretsAreLongEnoughAndDifferent() {
        final Set<String> secrets = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            final String secret = AccountView.generateSecret(16);
            assertEquals(AccountView.GENERATED_SECRET_LENGTH, secret.length());
            assertTrue(secret.matches("[A-Za-z0-9]+"), secret);
            assertNull(AccountView.webhookProblem("https://hooks.example.com/philter", secret, 16));
            secrets.add(secret);
        }
        assertEquals(20, secrets.size());
        assertNotEquals(AccountView.generateSecret(16), AccountView.generateSecret(16));
    }

    @Test
    void generatedSecretsMeetALargerMinimum() {
        assertEquals(64, AccountView.generateSecret(64).length());
        assertEquals(AccountView.GENERATED_SECRET_LENGTH, AccountView.generateSecret(null).length());
    }

    @Test
    void scopesAreSummarized() {
        assertEquals("redact, policies:read", AccountView.scopeSummary(List.of("redact", "policies:read")));
        assertEquals("25 scopes", AccountView.scopeSummary(java.util.Collections.nCopies(25, "s")));
        assertEquals("", AccountView.scopeSummary(null));
    }

}
