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

import ai.philterd.ui.model.PhilterRules;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Philter's password rules, checked here too so a person sees a mistake before the request is sent. */
final class Passwords {

    static final int GENERATED_LENGTH = 20;

    // Characters that are easy to misread, such as l, 1, O, and 0, are left out.
    static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    static final String DIGITS = "23456789";
    static final String SYMBOLS = "!@#$%^&*-_=+?";

    private static final SecureRandom RANDOM = new SecureRandom();

    private Passwords() {
    }

    /**
     * A random password that meets Philter's rules, with at least one character of each kind: {@link #GENERATED_LENGTH}
     * characters, or more if Philter requires more, and no more bytes than it allows. Every character is one byte.
     */
    static String generate(final PhilterRules rules) {
        int length = GENERATED_LENGTH;
        if (rules.passwordMinimumCharacters() != null) {
            length = Math.max(length, rules.passwordMinimumCharacters());
        }
        if (rules.passwordMaximumBytes() != null) {
            length = Math.min(length, rules.passwordMaximumBytes());
        }
        final String all = LOWER + UPPER + DIGITS + SYMBOLS;
        final List<Character> chars = new ArrayList<>(length);
        for (final String kind : List.of(LOWER, UPPER, DIGITS, SYMBOLS)) {
            chars.add(kind.charAt(RANDOM.nextInt(kind.length())));
        }
        while (chars.size() < length) {
            chars.add(all.charAt(RANDOM.nextInt(all.length())));
        }
        Collections.shuffle(chars, RANDOM);
        final StringBuilder password = new StringBuilder(length);
        chars.forEach(password::append);
        return password.toString();
    }

}
