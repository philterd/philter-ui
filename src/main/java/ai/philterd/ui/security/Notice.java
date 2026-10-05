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

import java.util.Arrays;
import java.util.Optional;

/**
 * Why a person was returned to the sign-in page. Only these fixed messages are shown, so the query
 * parameter cannot be used to put arbitrary text on the page.
 */
public enum Notice {

    SIGNED_OUT("signed-out", "You have signed out."),
    ENDED("ended", "Your session has ended. Sign in again."),
    PASSWORD_CHANGED("password-changed", "Your password has been changed. Sign in with your new password."),
    MFA_ENROLLED("mfa-enrolled", "Multi-factor authentication is set up. Sign in again, with a code from your authenticator app."),
    MFA_FAILED("mfa-failed", "That code was not accepted. Sign in again."),
    MFA_LOCKED("mfa-locked", SignInMessages.MFA_LOCKED);

    private final String parameter;
    private final String message;

    Notice(final String parameter, final String message) {
        this.parameter = parameter;
        this.message = message;
    }

    public String getParameter() {
        return parameter;
    }

    public String getMessage() {
        return message;
    }

    public static Optional<Notice> fromParameter(final String parameter) {
        return Arrays.stream(values()).filter(notice -> notice.parameter.equals(parameter)).findFirst();
    }

}
