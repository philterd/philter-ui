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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextCountsTest {

    private static ContextDetails details(final long size, final Map<String, Long> filterTypes, final long untyped) {
        final ContextDetails details = new ContextDetails();
        details.setSize(size);
        details.setFilterTypes(filterTypes);
        details.setUntyped(untyped);
        return details;
    }

    @Test
    void entriesWithNoFilterTypeAreLabeledAndCounted() {
        final ContextDetails details = details(125, Map.of("PERSON", 83L, "EMAIL_ADDRESS", 40L), 2);
        assertEquals(List.of(
                new ContextCounts.Count("EMAIL_ADDRESS", 40),
                new ContextCounts.Count("PERSON", 83),
                new ContextCounts.Count(ContextCounts.NO_FILTER_TYPE, 2)), ContextCounts.of(details));
        assertEquals(details.getSize(), sum(details));
    }

    @Test
    void noRowForUntypedEntriesWhenThereAreNone() {
        final ContextDetails details = details(3, Map.of("PERSON", 3L), 0);
        assertEquals(List.of(new ContextCounts.Count("PERSON", 3)), ContextCounts.of(details));
        assertEquals(details.getSize(), sum(details));
    }

    @Test
    void anEmptyContextHasNoRows() {
        assertEquals(List.of(), ContextCounts.of(details(0, Map.of(), 0)));
        assertEquals(List.of(), ContextCounts.of(details(0, null, 0)));
    }

    @Test
    void noLabelIsBlank() {
        final List<ContextCounts.Count> counts = ContextCounts.of(details(2, Map.of(), 2));
        assertEquals(List.of(new ContextCounts.Count(ContextCounts.NO_FILTER_TYPE, 2)), counts);
        assertTrue(counts.stream().noneMatch(count -> count.label().isBlank()));
    }

    private static long sum(final ContextDetails details) {
        return ContextCounts.of(details).stream().mapToLong(ContextCounts.Count::count).sum();
    }

}
