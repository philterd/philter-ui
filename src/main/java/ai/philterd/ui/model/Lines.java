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

import java.util.Arrays;
import java.util.List;

/** Terms typed one per line, the way Philter UI's list editors take them. */
public final class Lines {

    private Lines() {
    }

    /** The lines, trimmed, without blank ones, as Philter would store them. */
    public static List<String> terms(final String text) {
        if (text == null) {
            return List.of();
        }
        return Arrays.stream(text.split("\\R")).map(String::trim).filter(term -> !term.isEmpty()).toList();
    }

}
