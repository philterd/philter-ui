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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextDetailsTest {

    @Test
    void entriesWithNoFilterTypeAreLabeledAndCounted() {
        final ContextDetails details = ContextDetails.fromJson("""
                {"size":125,"filterTypes":{"PERSON":83,"EMAIL_ADDRESS":40},"untyped":2,
                 "entityTypeDisambiguation":true,"ledger":false}""");

        assertEquals(List.of(
                new ContextDetails.Count("EMAIL_ADDRESS", 40),
                new ContextDetails.Count("PERSON", 83),
                new ContextDetails.Count(ContextDetails.NO_FILTER_TYPE, 2)), details.counts());
        assertEquals(details.size(), sum(details));
        assertTrue(details.entityTypeDisambiguation());
        assertFalse(details.ledger());
    }

    @Test
    void noRowForUntypedEntriesWhenThereAreNone() {
        final ContextDetails details = ContextDetails.fromJson("""
                {"size":3,"filterTypes":{"PERSON":3},"untyped":0,"entityTypeDisambiguation":false,"ledger":true}""");

        assertEquals(List.of(new ContextDetails.Count("PERSON", 3)), details.counts());
        assertEquals(details.size(), sum(details));
        assertTrue(details.ledger());
    }

    @Test
    void anEmptyContextHasNoRows() {
        final ContextDetails details = ContextDetails.fromJson("""
                {"size":0,"filterTypes":{},"untyped":0,"entityTypeDisambiguation":false,"ledger":false}""");

        assertEquals(0, details.size());
        assertEquals(List.of(), details.counts());
    }

    @Test
    void noLabelIsBlank() {
        final ContextDetails details = ContextDetails.fromJson("""
                {"size":2,"filterTypes":{},"untyped":2}""");

        assertEquals(List.of(new ContextDetails.Count(ContextDetails.NO_FILTER_TYPE, 2)), details.counts());
        assertTrue(details.counts().stream().noneMatch(count -> count.label().isBlank()));
    }

    private static long sum(final ContextDetails details) {
        return details.counts().stream().mapToLong(ContextDetails.Count::count).sum();
    }

}
