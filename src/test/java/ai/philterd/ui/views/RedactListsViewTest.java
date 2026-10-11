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

import ai.philterd.philter.model.RedactList;
import ai.philterd.philter.model.RedactListTerms;
import ai.philterd.philter.model.exceptions.ClientException;
import ai.philterd.philter.model.exceptions.ErrorReasons;
import ai.philterd.ui.TestRules;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedactListsViewTest {

    /** Philter's two lists, each with a revision, refusing a replacement at the wrong revision as Philter does. */
    private static final class Store implements RedactListsView.ListStore {

        private final Map<RedactList, RedactListTerms> lists = new EnumMap<>(RedactList.class);
        private final List<RedactList> written = new ArrayList<>();

        Store() {
            lists.put(RedactList.ALWAYS, terms(List.of("Project Cardinal"), 3));
            lists.put(RedactList.NEVER, terms(List.of("Philterd"), 7));
        }

        @Override
        public RedactListTerms read(final RedactList list) {
            return lists.get(list);
        }

        @Override
        public RedactListTerms replace(final RedactList list, final List<String> terms, final Long expectedRevision)
                throws ClientException {
            final RedactListTerms current = lists.get(list);
            if (expectedRevision != null && expectedRevision != current.getRevision()) {
                throw new ClientException("HTTP 409", 409, "The list changed.", ErrorReasons.REDACT_LIST_CHANGED);
            }
            written.add(list);
            final RedactListTerms replaced = terms(terms, current.getRevision() + 1);
            lists.put(list, replaced);
            return replaced;
        }
    }

    private static RedactListTerms terms(final List<String> terms, final long revision) {
        final RedactListTerms list = new RedactListTerms();
        list.setTerms(terms);
        list.setRevision(revision);
        return list;
    }

    @Test
    void savingOneListWritesOnlyThatList() throws IOException {
        final Store store = new Store();
        final RedactListsView.Saved saved = RedactListsView.save(store, RedactListsView.Kind.ALWAYS,
                List.of("Project Cardinal", "Project Wren"), 3L);
        assertFalse(saved.changed());
        assertEquals(List.of("Project Cardinal", "Project Wren"), saved.list().getTerms());
        assertEquals(4, saved.list().getRevision());
        assertEquals(List.of(RedactList.ALWAYS), store.written);
        assertEquals(List.of("Philterd"), store.read(RedactList.NEVER).getTerms());
    }

    @Test
    void aSecondSaveUsesTheRevisionTheFirstReturned() throws IOException {
        final Store store = new Store();
        final RedactListsView.Saved first = RedactListsView.save(store, RedactListsView.Kind.NEVER, List.of("a"), 7L);
        final RedactListsView.Saved second = RedactListsView.save(store, RedactListsView.Kind.NEVER, List.of("a", "b"),
                first.list().getRevision());
        assertFalse(second.changed());
        assertEquals(9, second.list().getRevision());
        assertEquals(List.of(RedactList.NEVER, RedactList.NEVER), store.written);
    }

    @Test
    void aListChangedSinceItWasReadIsNotOverwritten() throws IOException {
        final Store store = new Store();
        // Someone else saved the always-redact list after this page read it at revision 3.
        store.replace(RedactList.ALWAYS, List.of("Project Heron"), 3L);
        store.written.clear();

        final RedactListsView.Saved saved = RedactListsView.save(store, RedactListsView.Kind.ALWAYS, List.of("mine"), 3L);
        assertTrue(saved.changed());
        assertEquals(List.of(), store.written);
        assertEquals(List.of("Project Heron"), saved.list().getTerms());
        assertEquals(4, saved.list().getRevision());

        // Saving again from the reloaded list uses its new revision and succeeds.
        final RedactListsView.Saved retried = RedactListsView.save(store, RedactListsView.Kind.ALWAYS,
                List.of("Project Heron", "mine"), saved.list().getRevision());
        assertFalse(retried.changed());
        assertEquals(List.of("Project Heron", "mine"), store.read(RedactList.ALWAYS).getTerms());
    }

    @Test
    void otherRefusalsAreNotTreatedAsAConflict() {
        final RedactListsView.ListStore refusing = new RedactListsView.ListStore() {
            @Override
            public RedactListTerms read(final RedactList list) {
                throw new AssertionError("not read");
            }

            @Override
            public RedactListTerms replace(final RedactList list, final List<String> terms, final Long revision)
                    throws ClientException {
                throw new ClientException("HTTP 400", 400, "Too many terms.");
            }
        };
        assertThrows(ClientException.class,
                () -> RedactListsView.save(refusing, RedactListsView.Kind.NEVER, List.of("x"), 1L));
    }

    @Test
    void theConflictMessageSaysTheEditsWereNotSaved() {
        assertTrue(RedactListsView.conflictMessage(RedactListsView.Kind.NEVER)
                .startsWith("Someone changed the never-redact list after you opened it, so your edits were not saved."));
    }

    @Test
    void philtersLimitsAreCheckedBeforeSending() {
        assertNull(RedactListsView.termsProblem(RedactListsView.Kind.ALWAYS, Collections.nCopies(1000, "x"), TestRules.defaults()));
        assertTrue(RedactListsView.termsProblem(RedactListsView.Kind.NEVER, Collections.nCopies(1001, "x"), TestRules.defaults())
                .startsWith("The never-redact list can have at most 1000 terms"));
        assertNull(RedactListsView.termsProblem(RedactListsView.Kind.ALWAYS, List.of("x".repeat(100)), TestRules.defaults()));
        assertTrue(RedactListsView.termsProblem(RedactListsView.Kind.ALWAYS, List.of("x".repeat(101)), TestRules.defaults())
                .startsWith("Each term can be at most 100 characters"));
        assertNull(RedactListsView.termsProblem(RedactListsView.Kind.ALWAYS, List.of(), TestRules.defaults()));
    }

}
