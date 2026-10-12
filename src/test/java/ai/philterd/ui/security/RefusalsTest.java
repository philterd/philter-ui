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

import ai.philterd.philter.model.exceptions.ClientException;
import ai.philterd.philter.model.exceptions.SignInLockedException;
import ai.philterd.philter.model.exceptions.SignInRateLimitedException;
import ai.philterd.philter.model.exceptions.UnauthorizedException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a person is told for each reason Philter gives when it refuses. */
class RefusalsTest {

    private static ClientException refused(final int status, final String reason) {
        return new ClientException("HTTP " + status, status, "Philter's message.", reason);
    }

    @Test
    void signInRefusalsAreReadByReason() {
        assertEquals(SignInMessages.MFA_LOCKED, SignInMessages.forFailure(refused(403, "mfa_locked")));
        assertEquals(SignInMessages.DISABLED, SignInMessages.forFailure(refused(404, "not_found")));
        assertEquals(SignInMessages.RATE_LIMITED, SignInMessages.forFailure(refused(429, "rate_limited")));
        assertEquals(SignInMessages.INVALID, SignInMessages.forFailure(
                new UnauthorizedException("HTTP 401", "Unauthorized.", "invalid_credentials", null)));
        assertEquals(SignInMessages.RATE_LIMITED, SignInMessages.forFailure(new SignInRateLimitedException("HTTP 429", 60)));
        assertTrue(SignInMessages.forFailure(new SignInLockedException("HTTP 429", 900))
                .startsWith("Too many failed sign-ins for this username. Try again in 15 minutes"));
        assertEquals(SignInMessages.UNAVAILABLE, SignInMessages.forFailure(new IOException("refused")));
    }

    @Test
    void aStatusWithoutItsReasonIsNotGuessedAt() {
        // A 403 is not taken to mean MFA is locked, nor a 404 that sign-in is off, without Philter saying so.
        assertEquals("Sign-in failed. Try again later.", SignInMessages.forFailure(refused(403, "forbidden")));
        assertEquals("Sign-in failed. Try again later.", SignInMessages.forFailure(refused(404, null)));
    }

    @Test
    void aDeactivatedAccountIsToldApartFromAnEndedSession() {
        assertEquals(Notice.DEACTIVATED, SessionErrorHandler.noticeFor(
                new UnauthorizedException("HTTP 401", "Deactivated.", "user_deactivated", null)));
        assertEquals(Notice.ENDED, SessionErrorHandler.noticeFor(
                new UnauthorizedException("HTTP 401", "Expired.", "session_expired", null)));
        assertEquals(Notice.ENDED, SessionErrorHandler.noticeFor(new RuntimeException(
                new UnauthorizedException("HTTP 401", "Unknown key.", "invalid_credentials", null))));
        assertTrue(Notice.DEACTIVATED.getMessage().startsWith("Your account has been deactivated."));
    }

    @Test
    void anAdministratorOnlyOrDisabledActionIsExplained() {
        assertEquals(PhilterFailures.ADMIN_REQUIRED, PhilterFailures.messageFor(refused(403, "admin_required")));
        assertEquals(PhilterFailures.FEATURE_DISABLED + " Philter's message.",
                PhilterFailures.messageFor(refused(403, "feature_disabled")));
    }

}
