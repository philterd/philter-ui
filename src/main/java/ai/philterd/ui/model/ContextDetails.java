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

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A context as {@code GET /api/contexts/{name}} returns it: its settings and how many entries it holds,
 * by filter type.
 *
 * @param size The number of entries. The counts sum to it.
 * @param filterTypes The number of entries for each filter type.
 * @param untyped The number of entries stored without a filter type, which only an import creates.
 */
public record ContextDetails(long size, Map<String, Long> filterTypes, long untyped,
                             boolean entityTypeDisambiguation, boolean ledger) {

    /** The label for entries with no filter type, so they never show as a blank row. */
    public static final String NO_FILTER_TYPE = "No filter type";

    private static final Gson GSON = new Gson();

    /** One row of the counts: a filter type, or {@link #NO_FILTER_TYPE}, and its number of entries. */
    public record Count(String label, long count) {
    }

    public static ContextDetails fromJson(final String json) {
        final Response response = GSON.fromJson(json, Response.class);
        return new ContextDetails(
                response.size == null ? 0 : response.size,
                response.filterTypes == null ? Map.of() : new TreeMap<>(response.filterTypes),
                response.untyped == null ? 0 : response.untyped,
                Boolean.TRUE.equals(response.entityTypeDisambiguation),
                Boolean.TRUE.equals(response.ledger));
    }

    /** The counts by filter type, in filter type order, then entries with no filter type if there are any. */
    public List<Count> counts() {
        final List<Count> counts = new ArrayList<>();
        new TreeMap<>(filterTypes).forEach((filterType, count) -> counts.add(new Count(filterType, count)));
        if (untyped > 0) {
            counts.add(new Count(NO_FILTER_TYPE, untyped));
        }
        return counts;
    }

    private static final class Response {
        private Long size;
        private Map<String, Long> filterTypes;
        private Long untyped;
        private Boolean entityTypeDisambiguation;
        private Boolean ledger;
    }

}
