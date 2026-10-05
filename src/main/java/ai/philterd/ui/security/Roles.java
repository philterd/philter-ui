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

/**
 * The roles a Philter UI session can hold. A person who still has a step to finish before using Philter
 * UI (an MFA code, a password change, MFA enrollment) holds only that step's role, so every other view
 * refuses them.
 */
public final class Roles {

    /** Signed in with a session key that can do everything the user's Philter role allows. */
    public static final String USER = "USER";

    /** Signed in as a Philter administrator. Always held together with {@link #USER}. */
    public static final String ADMIN = "ADMIN";

    /** The password was accepted and Philter wants a code from the authenticator app. */
    public static final String MFA_PENDING = "MFA_PENDING";

    /** An administrator set the password, so the session key can only change it. */
    public static final String PASSWORD_CHANGE = "PASSWORD_CHANGE";

    /** Philter requires MFA and the user has not enrolled, so the session key can only enroll. */
    public static final String MFA_ENROLLMENT = "MFA_ENROLLMENT";

    private Roles() {
    }

}
