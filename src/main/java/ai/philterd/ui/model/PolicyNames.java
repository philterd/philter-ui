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
package ai.philterd.ui.model;

import java.util.regex.Pattern;

/** Philter's rule for policy names, checked here too so a mistake shows before the request. */
public final class PolicyNames {

    public static final int MAXIMUM_LENGTH = 50;

    /** How Philter UI describes the rule to people. */
    public static final String RULE = "Up to " + MAXIMUM_LENGTH + " letters, digits, dashes, and underscores, "
            + "not starting with managed_.";

    private static final Pattern ALLOWED = Pattern.compile("^[a-zA-Z0-9_-]+$");

    /** Managed policies' names begin with this, so a person's own policy cannot. */
    private static final String MANAGED_PREFIX = "managed_";

    private PolicyNames() {
    }

    /** What is wrong with a name for a new policy, or {@code null} if Philter will accept it. */
    public static String problem(final String name) {
        if (name == null || name.isBlank()) {
            return "Enter a name.";
        }
        if (name.length() > MAXIMUM_LENGTH) {
            return "A policy name can be at most " + MAXIMUM_LENGTH + " characters.";
        }
        if (!ALLOWED.matcher(name).matches()) {
            return "A policy name can contain only letters, digits, dashes, and underscores.";
        }
        if (name.startsWith(MANAGED_PREFIX)) {
            return "A policy name cannot start with " + MANAGED_PREFIX + ", which is reserved for managed policies.";
        }
        return null;
    }

}
