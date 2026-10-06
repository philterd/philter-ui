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

import ai.philterd.philter.model.ContextDetails;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The rows of a context's entry counts, by filter type, as the View Context dialog shows them. */
public final class ContextCounts {

    /** The label for entries with no filter type, so they never show as a blank row. */
    public static final String NO_FILTER_TYPE = "No filter type";

    /** One row: a filter type, or {@link #NO_FILTER_TYPE}, and its number of entries. */
    public record Count(String label, long count) {
    }

    private ContextCounts() {
    }

    /** The counts in filter type order, then entries with no filter type if there are any. */
    public static List<Count> of(final ContextDetails details) {
        final List<Count> counts = new ArrayList<>();
        final Map<String, Long> filterTypes = details.getFilterTypes() == null ? Map.of() : details.getFilterTypes();
        new TreeMap<>(filterTypes).forEach((filterType, count) -> counts.add(new Count(filterType, count)));
        if (details.getUntyped() > 0) {
            counts.add(new Count(NO_FILTER_TYPE, details.getUntyped()));
        }
        return counts;
    }

}
