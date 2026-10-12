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
import ai.philterd.philter.model.exceptions.ErrorReasons;
import ai.philterd.philter.model.exceptions.PhilterError;
import ai.philterd.philter.model.exceptions.ServiceUnavailableException;

/** What to tell a person when a request to Philter failed. */
public final class PhilterFailures {

    /** Shown when Philter says an administrator is required, which for someone who saw the action means their role changed. */
    public static final String ADMIN_REQUIRED = "This needs an administrator. Your role may have changed; open the "
            + "page again to see what you can do now.";

    /** Shown, with Philter's explanation, when Philter says the action is turned off. */
    public static final String FEATURE_DISABLED = "This is turned off in this Philter deployment.";

    private PhilterFailures() {
    }

    /**
     * Philter's explanation, its HTTP status when it gave none, or that Philter cannot be reached, for the
     * first failed request to Philter in the cause chain; {@code null} when the failure is not one.
     */
    /**
     * The id Philter gave the failed request, for the first Philter response in the cause chain, so a person can
     * quote it to an administrator; {@code null} when the failure did not come from a Philter response, such as
     * Philter being unreachable.
     */
    public static String requestIdFor(final Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof PhilterError error) {
                return error.getRequestId() == null || error.getRequestId().isBlank() ? null : error.getRequestId();
            }
            if (cause instanceof PhilterUnreachableException) {
                return null;
            }
        }
        return null;
    }

    public static String messageFor(final Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof ClientException refusal) {
                if (ErrorReasons.ADMIN_REQUIRED.equals(refusal.getReason())) {
                    return ADMIN_REQUIRED;
                }
                if (ErrorReasons.FEATURE_DISABLED.equals(refusal.getReason())) {
                    return FEATURE_DISABLED + (refusal.getErrorMessage() == null ? "" : " " + refusal.getErrorMessage());
                }
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
