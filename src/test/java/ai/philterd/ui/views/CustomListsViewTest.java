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

import ai.philterd.philter.model.CustomList;
import ai.philterd.philter.model.Limits;
import ai.philterd.ui.TestRules;
import ai.philterd.ui.model.Lines;
import ai.philterd.ui.model.PhilterRules;
import com.google.gson.Gson;
import com.vaadin.flow.component.textfield.TextField;
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

    /** A list as the SDK reads Philter's answer to {@code GET /api/lists/{name}}. */
    private static CustomList read(final String json) {
        return new Gson().fromJson(json, CustomList.class);
    }

    @Test
    void theEditDialogShowsTheListsItemsFromPhilter4() {
        // Philter 4.0's documented response.
        final CustomList list = read("{\"items\": [\"item1\", \"item2\"], \"description\": \"My description\"}");
        assertEquals("item1\nitem2", CustomListsView.itemsText(list));
        assertEquals("My description", list.getDescription());
    }

    @Test
    void aListWhoseItemsCannotBeReadIsNotOpenedForEditing() {
        // Philter 4.0 development builds returned the items under lists, which the SDK no longer reads.
        assertNull(CustomListsView.itemsText(read("{\"lists\": [\"item1\"], \"description\": \"\"}")));
        assertNull(CustomListsView.itemsText(new CustomList()));
    }

    @Test
    void aListWithNoItemsOpensEmpty() {
        // Philter 4.0 stores one when every item sent is whitespace, which it trims only after its empty check.
        assertEquals("", CustomListsView.itemsText(read("{\"items\": [], \"description\": \"\"}")));
    }

    private static PhilterRules descriptionLimit(final int maximumLength) {
        final Limits limits = TestRules.limits();
        limits.getCustomLists().setDescriptionMaxLength(maximumLength);
        return PhilterRules.of(limits);
    }

    @Test
    void theDescriptionIsLimitedToPhiltersLimit() {
        final TextField description = CustomListsView.descriptionField(TestRules.defaults());
        assertEquals(200, description.getMaxLength());
        assertEquals("Up to 200 characters.", description.getHelperText());

        final TextField raised = CustomListsView.descriptionField(descriptionLimit(500));
        assertEquals(500, raised.getMaxLength());
        assertEquals("Up to 500 characters.", raised.getHelperText());
    }

    @Test
    void theDescriptionFallsBackToPhiltersDefault() {
        assertEquals(200, CustomListsView.descriptionField(PhilterRules.unknown()).getMaxLength(),
                "when Philter's limits cannot be read");
        assertEquals(200, CustomListsView.descriptionField(descriptionLimit(0)).getMaxLength(),
                "when Philter does not report the description limit");
    }

}
