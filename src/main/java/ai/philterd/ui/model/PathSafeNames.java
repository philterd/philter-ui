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

/**
 * Philter's rule for names that travel in a request path, such as custom list names. Philter refuses
 * new names that break it, but items created before the rule can still have one, and those can be
 * deleted but not opened or changed. The SDK's own check is private, so this mirrors Philter's
 * {@code PathSafeNames}.
 */
public final class PathSafeNames {

    /** The rule as Philter words it. */
    public static final String RULE = "cannot contain /, \\, ;, %, or control characters, and cannot be . or ..";

    private PathSafeNames() {
    }

    public static boolean isPathSafe(final String name) {
        if (name == null) {
            return true;
        }
        if (".".equals(name) || "..".equals(name)) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            if (c == '/' || c == '\\' || c == ';' || c == '%' || Character.isISOControl(c)) {
                return false;
            }
        }
        return true;
    }

}
