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

import ai.philterd.philter.model.WebhookDelivery;
import ai.philterd.philter.model.WebhookTestResult;
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
        assertNull(AccountView.webhookProblem("https://hooks.example.com/philter", secret, 16, false));
        assertNull(AccountView.webhookProblem("http://10.0.0.5:8080/hook", secret, 16, false));
        assertEquals("Enter a URL.", AccountView.webhookProblem(" ", secret, 16, false));
        assertEquals("The URL must be an http or https address.", AccountView.webhookProblem("ftp://example.com/x", secret, 16, false));
        assertEquals("The URL must be an http or https address.", AccountView.webhookProblem("not a url", secret, 16, false));
        assertTrue(AccountView.webhookProblem("https://hooks.example.com/philter", "short", 16, false).startsWith("Enter a secret of at least 16"));
        assertEquals("Enter a secret; Philter requires one for a new webhook.",
                AccountView.webhookProblem("https://hooks.example.com/philter", "", 16, false));
        assertTrue(AccountView.webhookProblem("https://hooks.example.com/philter", null, 16, false).startsWith("Enter a secret"));
    }

    @Test
    void anUnknownMinimumLeavesTheSecretLengthToPhilter() {
        assertNull(AccountView.webhookProblem("https://hooks.example.com/philter", "short", null, false));
        assertTrue(AccountView.webhookProblem("https://hooks.example.com/philter", "", null, false).startsWith("Enter a secret"));
    }

    @Test
    void generatedSecretsAreLongEnoughAndDifferent() {
        final Set<String> secrets = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            final String secret = AccountView.generateSecret(16);
            assertEquals(AccountView.GENERATED_SECRET_LENGTH, secret.length());
            assertTrue(secret.matches("[A-Za-z0-9]+"), secret);
            assertNull(AccountView.webhookProblem("https://hooks.example.com/philter", secret, 16, false));
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

    @Test
    void aSetSecretIsKeptWhenTheFieldIsLeftBlank() {
        assertNull(AccountView.webhookProblem("https://hooks.example.com/new", "", 16, true));
        assertNull(AccountView.webhookProblem("https://hooks.example.com/new", null, 16, true));
        assertNull(AccountView.secretToSend(""));
        assertNull(AccountView.secretToSend(null));
        assertEquals("x".repeat(16), AccountView.secretToSend("x".repeat(16)));
        // A new secret entered over a set one must still meet the minimum.
        assertTrue(AccountView.webhookProblem("https://hooks.example.com/new", "short", 16, true)
                .startsWith("Enter a secret of at least 16"));
        assertTrue(AccountView.secretHelp(16, true).contains("Leave blank to keep the current secret."));
        assertTrue(AccountView.secretHelp(16, false).contains("Required for a new webhook."));
        assertEquals("A webhook is set, with a secret.", AccountView.webhookStatus(true, true));
        assertEquals("No webhook is set.", AccountView.webhookStatus(false, false));
    }

    private static WebhookTestResult result(final boolean delivered, final Integer status, final String error) {
        final WebhookTestResult result = new WebhookTestResult();
        result.setDelivered(delivered);
        result.setStatusCode(status);
        result.setError(error);
        result.setDurationMillis(142);
        result.setDeliveryId("d-1");
        return result;
    }

    @Test
    void aTestEventReportsHowTheReceiverAnswered() {
        assertEquals("Delivered: the receiver answered HTTP 204 in 142 ms. Delivery ID: d-1.",
                AccountView.testOutcome(result(true, 204, null)));
        assertEquals("Not delivered: the receiver answered HTTP 401 in 142 ms. Delivery ID: d-1.",
                AccountView.testOutcome(result(false, 401, "Webhook responded with HTTP 401")));
        assertEquals("Not delivered after 142 ms. Connection refused. Delivery ID: d-1.",
                AccountView.testOutcome(result(false, null, "Connection refused")));
    }

    private static WebhookDelivery delivery(final String status) {
        final WebhookDelivery delivery = new WebhookDelivery();
        delivery.setStatus(status);
        delivery.setEvent("DOCUMENT_REDACTION_COMPLETE");
        delivery.setLastError("Connection refused");
        delivery.setNextAttemptAt("2026-10-11T14:05:00Z");
        return delivery;
    }

    @Test
    void deliveriesShowWhyAndWhenForUnsettledOnes() {
        assertEquals("Connection refused", AccountView.lastError(delivery(WebhookDelivery.PENDING)));
        assertEquals("2026-10-11 14:05 UTC", AccountView.nextAttempt(delivery(WebhookDelivery.PENDING)));
        assertEquals("Connection refused", AccountView.lastError(delivery(WebhookDelivery.FAILED)));
        assertEquals("", AccountView.lastError(delivery(WebhookDelivery.DELIVERED)));
        assertEquals("", AccountView.nextAttempt(delivery(WebhookDelivery.DELIVERED)));
        assertEquals("Pending", AccountView.statusLabel(WebhookDelivery.PENDING));
        assertEquals("Sending", AccountView.statusLabel(WebhookDelivery.PROCESSING));
        assertEquals("Failed", AccountView.statusLabel(WebhookDelivery.FAILED));
        assertEquals("SOMETHING_NEW", AccountView.statusLabel("SOMETHING_NEW"));
        assertEquals("Redaction complete", AccountView.eventLabel("DOCUMENT_REDACTION_COMPLETE"));
        assertEquals("Redaction failed", AccountView.eventLabel("DOCUMENT_REDACTION_FAILED"));
    }

    @Test
    void sessionsNameTheirBrowserAndSystem() {
        assertEquals("Firefox on Windows", AccountView.device(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0"));
        assertEquals("Chrome on macOS", AccountView.device("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"));
        assertEquals("Edge on Windows", AccountView.device("Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36 Edg/129.0.0.0"));
        assertEquals("Safari on iOS", AccountView.device("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) "
                + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1"));
        assertEquals("Chrome on Android", AccountView.device("Mozilla/5.0 (Linux; Android 14; Pixel 8) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36"));
        assertEquals("Java HTTP client", AccountView.device("Java-http-client/21.0.4"));
        // A client that is not a browser is shown as it named itself.
        assertEquals("curl/8.7.1", AccountView.device("curl/8.7.1"));
        assertEquals("", AccountView.device(null));
    }

    @Test
    void signingOutOtherSessionsSaysHowMany() {
        assertEquals("1 other session signed out.", AccountView.otherSessionsSignedOut(1));
        assertEquals("3 other sessions signed out.", AccountView.otherSessionsSignedOut(3));
        assertEquals("There were no other sessions to sign out.", AccountView.otherSessionsSignedOut(0));
        assertEquals("This session", AccountView.THIS_SESSION);
    }

}
