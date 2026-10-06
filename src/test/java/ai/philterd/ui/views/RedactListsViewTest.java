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

import ai.philterd.philter.model.RedactLists;
import ai.philterd.philter.model.RedactListsRequest;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedactListsViewTest {

    private static RedactLists current(final List<String> always, final List<String> never) {
        final RedactLists lists = new RedactLists();
        lists.setAlwaysRedact(always);
        lists.setNeverRedact(never);
        return lists;
    }

    @Test
    void savingTheAlwaysListSendsTheNeverListAsPhilterHasIt() {
        final RedactListsRequest request = RedactListsView.request(RedactListsView.Kind.ALWAYS, List.of("Project Cardinal"),
                current(List.of("old"), List.of("Philterd")));
        assertEquals(List.of("Project Cardinal"), request.getAlwaysRedact());
        assertEquals(List.of("Philterd"), request.getNeverRedact());
    }

    @Test
    void savingTheNeverListSendsTheAlwaysListAsPhilterHasIt() {
        final RedactListsRequest request = RedactListsView.request(RedactListsView.Kind.NEVER, List.of("Philterd"),
                current(List.of("ACME-1234"), List.of("old")));
        assertEquals(List.of("ACME-1234"), request.getAlwaysRedact());
        assertEquals(List.of("Philterd"), request.getNeverRedact());
    }

    @Test
    void anEmptyListIsSentEmptySoPhilterClearsIt() {
        final RedactListsRequest request = RedactListsView.request(RedactListsView.Kind.ALWAYS, List.of(),
                current(List.of("old"), List.of("Philterd")));
        assertEquals(List.of(), request.getAlwaysRedact());
        assertEquals(List.of("Philterd"), request.getNeverRedact());
    }

    @Test
    void missingListsFromPhilterAreTreatedAsEmpty() {
        final RedactListsRequest request = RedactListsView.request(RedactListsView.Kind.NEVER, List.of("x"), current(null, null));
        assertEquals(List.of(), request.getAlwaysRedact());
        assertEquals(List.of("x"), RedactListsView.request(RedactListsView.Kind.NEVER, List.of("x"), null).getNeverRedact());
    }

    @Test
    void philtersLimitsAreCheckedBeforeSending() {
        assertNull(RedactListsView.termsProblem(RedactListsView.Kind.ALWAYS, Collections.nCopies(RedactListsView.MAXIMUM_TERMS, "x")));
        assertTrue(RedactListsView.termsProblem(RedactListsView.Kind.NEVER, Collections.nCopies(RedactListsView.MAXIMUM_TERMS + 1, "x"))
                .startsWith("The never-redact list can have at most 1000 terms"));
        assertNull(RedactListsView.termsProblem(RedactListsView.Kind.ALWAYS, List.of("x".repeat(RedactListsView.MAXIMUM_TERM_LENGTH))));
        assertTrue(RedactListsView.termsProblem(RedactListsView.Kind.ALWAYS, List.of("x".repeat(RedactListsView.MAXIMUM_TERM_LENGTH + 1)))
                .startsWith("Each term can be at most 100 characters"));
        assertNull(RedactListsView.termsProblem(RedactListsView.Kind.ALWAYS, List.of()));
    }

}
