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

import ai.philterd.philter.model.MfaEnrollment;
import ai.philterd.philter.model.exceptions.ClientException;
import ai.philterd.philter.model.exceptions.SignInLockedException;
import ai.philterd.ui.security.SignInMessages;
import com.vaadin.flow.component.textfield.PasswordField;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MfaEnrollmentStartTest {

    private static PasswordField typed(final String value) {
        final PasswordField password = MfaEnrollmentView.passwordField();
        password.setValue(value);
        return password;
    }

    @Test
    void thePasswordIsSentAndCleared() {
        final PasswordField password = typed("correct horse");
        final MfaEnrollment enrollment = new MfaEnrollment();
        final List<String> sent = new ArrayList<>();

        assertSame(enrollment, MfaEnrollmentView.startWith(password, p -> {
            sent.add(p);
            return enrollment;
        }));
        assertEquals(List.of("correct horse"), sent);
        assertEquals("", password.getValue());
        assertFalse(password.isInvalid());
    }

    @Test
    void aRefusalIsShownOnTheClearedField() {
        final PasswordField password = typed("wrong");
        assertNull(MfaEnrollmentView.startWith(password, p -> {
            throw new ClientException("HTTP 403", 403, "The password is not correct.", "wrong_password");
        }));
        assertEquals("", password.getValue());
        assertTrue(password.isInvalid());
        assertEquals(ChangePasswordView.WRONG_CURRENT_PASSWORD, password.getErrorMessage());
    }

    @Test
    void aLockoutSaysHowLongToWait() {
        final PasswordField password = typed("wrong");
        assertNull(MfaEnrollmentView.startWith(password, p -> {
            throw new SignInLockedException("Locked.", 900);
        }));
        assertTrue(password.isInvalid());
        assertTrue(password.getErrorMessage().contains("15 minutes"), password.getErrorMessage());
    }

    @Test
    void philterBeingUnreachableIsShownOnTheField() {
        final PasswordField password = typed("correct horse");
        assertNull(MfaEnrollmentView.startWith(password, p -> {
            throw new IOException("Connection refused");
        }));
        assertTrue(password.isInvalid());
        assertEquals(SignInMessages.UNAVAILABLE, password.getErrorMessage());
    }

    @Test
    void anEmptyPasswordIsNotSent() {
        final PasswordField password = typed("");
        assertNull(MfaEnrollmentView.startWith(password, p -> {
            throw new AssertionError("Sent an empty password.");
        }));
        assertTrue(password.isInvalid());
        assertEquals(MfaEnrollmentView.ENTER_PASSWORD, password.getErrorMessage());
    }

}
