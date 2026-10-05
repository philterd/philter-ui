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

import java.nio.charset.StandardCharsets;

/** Philter's password rules, checked here too so a person sees a mistake before the request is sent. */
final class Passwords {

    static final int MIN_LENGTH = 16;
    static final int MAX_BYTES = 72;

    static final String RULES = "At least " + MIN_LENGTH + " characters, and at most " + MAX_BYTES
            + " bytes. Use a mix of upper and lowercase letters, numbers, and symbols, or a passphrase of 5 to 7 "
            + "unrelated words.";

    private Passwords() {
    }

    /** What is wrong with the password, or {@code null} if it meets the rules. */
    static String problem(final String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return "The password must be at least " + MIN_LENGTH + " characters.";
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return "The password must be at most " + MAX_BYTES + " bytes.";
        }
        return null;
    }

}
