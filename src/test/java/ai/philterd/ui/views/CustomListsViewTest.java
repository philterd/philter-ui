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

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomListsViewTest {

    @Test
    void itemsAreOnePerLineTrimmedWithoutBlankLines() {
        assertEquals(List.of("alpha", "beta", "gamma delta"),
                CustomListsView.items("  alpha\n\nbeta  \r\n   \ngamma delta\n\n"));
        assertEquals(List.of(), CustomListsView.items(""));
        assertEquals(List.of(), CustomListsView.items(null));
    }

    @Test
    void blankLinesDoNotCountTowardTheLimit() {
        final String text = String.join("\n\n", Collections.nCopies(CustomListsView.MAXIMUM_ITEMS, "x"));
        assertNull(CustomListsView.itemsProblem(CustomListsView.items(text)));
    }

    @Test
    void philtersLimitsAreCheckedBeforeSending() {
        assertEquals("Enter at least one item.", CustomListsView.itemsProblem(List.of()));
        assertTrue(CustomListsView.itemsProblem(Collections.nCopies(CustomListsView.MAXIMUM_ITEMS + 1, "x"))
                .startsWith("A list can have at most 100 items"));
        assertTrue(CustomListsView.itemsProblem(List.of("x".repeat(CustomListsView.MAXIMUM_ITEM_LENGTH + 1)))
                .startsWith("Each item can be at most 50 characters"));
        assertNull(CustomListsView.itemsProblem(List.of("x".repeat(CustomListsView.MAXIMUM_ITEM_LENGTH))));
    }

    @Test
    void anUnchangedDescriptionIsLeftOutSoPhilterKeepsIt() {
        assertNull(CustomListsView.descriptionChange("Employee IDs", "Employee IDs"));
        assertNull(CustomListsView.descriptionChange("Employee IDs", " Employee IDs "));
        assertNull(CustomListsView.descriptionChange(null, ""));
    }

    @Test
    void aChangedDescriptionIsSentAndAClearedOneIsSentEmpty() {
        assertEquals("Contractor IDs", CustomListsView.descriptionChange("Employee IDs", "Contractor IDs"));
        assertEquals("", CustomListsView.descriptionChange("Employee IDs", "   "));
        assertEquals("New", CustomListsView.descriptionChange("", "New"));
    }

}
