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
import ai.philterd.philter.model.exceptions.ServiceUnavailableException;
import ai.philterd.philter.model.exceptions.SignInLockedException;
import ai.philterd.philter.model.exceptions.SignInRateLimitedException;
import ai.philterd.philter.model.exceptions.UnauthorizedException;

import java.io.IOException;

/** What to tell a person whose sign-in Philter refused. */
public final class SignInMessages {

    public static final String INVALID = "Incorrect username or password.";
    public static final String RATE_LIMITED = "Too many sign-in attempts from this address. Try again in a minute.";
    public static final String MFA_LOCKED = "Your account is locked after too many incorrect codes. "
            + "Ask an administrator to unlock it.";
    public static final String DISABLED = "Password sign-in is not enabled in Philter. "
            + "An administrator must set PASSWORD_SIGN_IN_ENABLED=true.";
    public static final String UNAVAILABLE = "Philter could not be reached. Try again later.";

    private SignInMessages() {
    }

    public static String forFailure(final Exception exception) {
        if (exception instanceof SignInLockedException locked) {
            return "Too many failed sign-ins for this username. Try again in " + wait(locked.getRetryAfterSeconds()) + ".";
        }
        if (exception instanceof SignInRateLimitedException) {
            return RATE_LIMITED;
        }
        if (exception instanceof UnauthorizedException) {
            return INVALID;
        }
        if (exception instanceof ClientException client) {
            if (PhilterErrors.hasStatus(client, 403)) {
                return MFA_LOCKED;
            }
            if (PhilterErrors.hasStatus(client, 404)) {
                return DISABLED;
            }
        }
        if (exception instanceof ServiceUnavailableException || exception instanceof IOException) {
            return UNAVAILABLE;
        }
        return "Sign-in failed. Try again later.";
    }

    private static String wait(final Integer seconds) {
        if (seconds == null || seconds <= 60) {
            return "a minute";
        }
        final int minutes = (seconds + 59) / 60;
        return minutes + " minutes";
    }

}
