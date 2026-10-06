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

/** What to tell a person when a request to Philter failed. */
public final class PhilterFailures {

    private PhilterFailures() {
    }

    /**
     * Philter's explanation, its HTTP status when it gave none, or that Philter cannot be reached, for the
     * first failed request to Philter in the cause chain; {@code null} when the failure is not one.
     */
    public static String messageFor(final Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof ClientException refusal) {
                return refusal.getErrorMessage() != null ? refusal.getErrorMessage()
                        : "Philter answered with HTTP " + refusal.getStatusCode() + ".";
            }
            if (cause instanceof ServiceUnavailableException || cause instanceof PhilterUnreachableException) {
                return SignInMessages.UNAVAILABLE;
            }
        }
        return null;
    }

}
