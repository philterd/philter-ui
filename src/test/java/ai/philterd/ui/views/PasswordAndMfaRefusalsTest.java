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

import ai.philterd.philter.model.exceptions.ClientException;
import ai.philterd.ui.security.SignInMessages;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordAndMfaRefusalsTest {

    private static ClientException refused(final int status, final String reason, final String field) {
        return new ClientException("HTTP " + status, status, "Philter's message.", reason, null, field);
    }

    @Test
    void aRefusedPasswordChangeIsReadByReason() {
        assertEquals(ChangePasswordView.PasswordRefusal.WRONG_CURRENT,
                ChangePasswordView.passwordRefusal(refused(403, "wrong_password", null)));
        assertEquals(ChangePasswordView.PasswordRefusal.CHANGED_ELSEWHERE,
                ChangePasswordView.passwordRefusal(refused(409, "changed_concurrently", null)));
        assertEquals(ChangePasswordView.PasswordRefusal.NEW_PASSWORD,
                ChangePasswordView.passwordRefusal(refused(400, "invalid_request", "newPassword")));
        assertEquals(ChangePasswordView.PasswordRefusal.WRONG_CURRENT,
                ChangePasswordView.passwordRefusal(refused(400, "invalid_request", "currentPassword")));
        // A 403 for another reason, such as a missing scope, is not taken for a wrong password.
        assertEquals(ChangePasswordView.PasswordRefusal.NEW_PASSWORD,
                ChangePasswordView.passwordRefusal(refused(403, "missing_scope", null)));
    }

    @Test
    void mfaEnrollmentRefusalsAreReadByReason() {
        assertTrue(MfaEnrollmentView.startFailure(refused(409, "mfa_unavailable", null)).contains("not available"));
        assertEquals("You have already set up multi-factor authentication.",
                MfaEnrollmentView.startFailure(refused(409, "mfa_already_enrolled", null)));
        assertEquals("Philter's message.", MfaEnrollmentView.startFailure(refused(500, "internal_error", null)));

        assertEquals(MfaEnrollmentView.INVALID_CODE, MfaEnrollmentView.confirmFailure(refused(400, "invalid_code", null)));
        assertEquals(MfaEnrollmentView.INVALID_CODE, MfaEnrollmentView.confirmFailure(refused(400, "invalid_request", "code")));
        assertEquals(SignInMessages.MFA_LOCKED, MfaEnrollmentView.confirmFailure(refused(403, "mfa_locked", null)));

        assertTrue(MfaEnrollmentView.endsEnrollment(refused(409, "mfa_already_enrolled", null)));
        assertTrue(MfaEnrollmentView.endsEnrollment(refused(409, "mfa_unavailable", null)));
        assertFalse(MfaEnrollmentView.endsEnrollment(refused(409, "changed_concurrently", null)));
    }

}
