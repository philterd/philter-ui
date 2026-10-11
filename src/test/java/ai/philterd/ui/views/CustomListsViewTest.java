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

import ai.philterd.ui.TestRules;
import ai.philterd.ui.model.Lines;
import ai.philterd.ui.model.PhilterRules;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomListsViewTest {

    @Test
    void blankLinesDoNotCountTowardTheLimit() {
        final String text = String.join("\n\n", Collections.nCopies(100, "x"));
        assertNull(CustomListsView.itemsProblem(Lines.terms(text), TestRules.defaults()));
    }

    @Test
    void philtersLimitsAreCheckedBeforeSending() {
        assertEquals("Enter at least one item.", CustomListsView.itemsProblem(List.of(), TestRules.defaults()));
        assertTrue(CustomListsView.itemsProblem(Collections.nCopies(101, "x"), TestRules.defaults())
                .startsWith("A list can have at most 100 items"));
        assertTrue(CustomListsView.itemsProblem(List.of("x".repeat(51)), TestRules.defaults())
                .startsWith("Each item can be at most 50 characters"));
        assertNull(CustomListsView.itemsProblem(List.of("x".repeat(50)), TestRules.defaults()));
    }

    @Test
    void unknownLimitsAreLeftToPhilter() {
        assertNull(CustomListsView.itemsProblem(Collections.nCopies(500, "x".repeat(500)), PhilterRules.unknown()));
        assertEquals("Enter at least one item.", CustomListsView.itemsProblem(List.of(), PhilterRules.unknown()));
    }

}
