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

import ai.philterd.philter.model.ContextDetails;
import ai.philterd.philter.model.DisambiguationScope;
import ai.philterd.philter.model.Limits;
import ai.philterd.philter.model.exceptions.ClientException;
import ai.philterd.ui.TestRules;
import ai.philterd.ui.model.PhilterRules;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ContextsViewTest {

    private static ClientException conflict(final String reason, final String message) {
        return new ClientException("HTTP 409", 409, message, reason);
    }

    @Test
    void theContextLimitIsNotReportedAsADuplicateName() {
        assertEquals("You already have as many contexts as Philter allows. Delete one first.",
                ContextsView.createFailure(conflict("context_limit_reached", "Maximum number of contexts reached.")));
    }

    @Test
    void aDuplicateNameIsReported() {
        assertEquals("You already have a context with this name.",
                ContextsView.createFailure(conflict("context_exists", "Context already exists.")));
    }

    @Test
    void aConflictWithoutAReasonShowsPhiltersMessage() {
        assertEquals("Context already exists.", ContextsView.createFailure(
                new ClientException("HTTP 409", 409, "Context already exists.")));
    }

    @Test
    void anyOtherRefusalShowsPhiltersMessage() {
        assertEquals("Context name cannot be blank.", ContextsView.createFailure(
                new ClientException("HTTP 400", 400, "Context name cannot be blank.")));
        assertEquals("The context could not be created.",
                ContextsView.createFailure(new ClientException("HTTP 500", 500, null)));
    }


    private static ContextDetails details(final boolean disambiguation, final String scope, final boolean ledger) {
        final ContextDetails details = new ContextDetails();
        details.setEntityTypeDisambiguation(disambiguation);
        details.setDisambiguationScope(scope);
        details.setLedger(ledger);
        return details;
    }

    @Test
    void theScopesAreThoseTheSdkDefines() {
        assertEquals(List.of(DisambiguationScope.DOCUMENT, DisambiguationScope.CONTEXT), ContextsView.SCOPES);
        assertEquals("Each document on its own", ContextsView.scopeLabel(DisambiguationScope.DOCUMENT));
        assertEquals("Across the context: remember what is learned for later documents",
                ContextsView.scopeLabel(DisambiguationScope.CONTEXT));
    }

    @Test
    void creatingSendsTheScopeOnlyWithDisambiguation() {
        assertEquals(DisambiguationScope.CONTEXT, ContextsView.createScope(true, DisambiguationScope.CONTEXT));
        assertEquals(DisambiguationScope.DOCUMENT, ContextsView.createScope(true, DisambiguationScope.DOCUMENT));
        assertNull(ContextsView.createScope(false, DisambiguationScope.CONTEXT), "no effect without disambiguation");
    }

    @Test
    void editingSendsTheScopeOnlyWhenItChanged() {
        final ContextDetails document = details(true, DisambiguationScope.DOCUMENT, false);
        assertNull(ContextsView.scopeChange(document, DisambiguationScope.DOCUMENT), "unchanged, so kept");
        assertEquals(DisambiguationScope.CONTEXT, ContextsView.scopeChange(document, DisambiguationScope.CONTEXT));

        final ContextDetails context = details(true, DisambiguationScope.CONTEXT, false);
        assertEquals(DisambiguationScope.CONTEXT, ContextsView.currentScope(context), "the edit dialog starts here");
        assertEquals(DisambiguationScope.DOCUMENT, ContextsView.scopeChange(context, DisambiguationScope.DOCUMENT));

        // A context Philter reports no scope for is at document, so choosing document changes nothing.
        final ContextDetails unset = details(true, null, false);
        assertEquals(DisambiguationScope.DOCUMENT, ContextsView.currentScope(unset));
        assertNull(ContextsView.scopeChange(unset, DisambiguationScope.DOCUMENT));
    }

    @Test
    void theViewDialogStatesTheScope() {
        assertEquals("Entity type disambiguation is on, across the context: what it learns is remembered for later "
                + "documents. The redaction ledger is on.",
                ContextsView.settingsSummary(details(true, DisambiguationScope.CONTEXT, true)));
        assertEquals("Entity type disambiguation is on, for each document on its own. The redaction ledger is off.",
                ContextsView.settingsSummary(details(true, DisambiguationScope.DOCUMENT, false)));
        assertEquals("Entity type disambiguation is off. The redaction ledger is off.",
                ContextsView.settingsSummary(details(false, DisambiguationScope.CONTEXT, false)));
    }

    @Test
    void newContextsStopAtPhiltersLimit() {
        assertNull(ContextsView.createBlocked(TestRules.defaults()));
        assertNull(ContextsView.createBlocked(PhilterRules.unknown()));
        final Limits limits = TestRules.limits();
        limits.getCaller().setContextCount(10);
        assertEquals("You have 10 contexts, the most Philter allows. Delete one to create another.",
                ContextsView.createBlocked(PhilterRules.of(limits)));
        limits.getContexts().setMaxPerUser(1);
        limits.getCaller().setContextCount(1);
        assertEquals("You have 1 context, the most Philter allows. Delete one to create another.",
                ContextsView.createBlocked(PhilterRules.of(limits)));
    }

}
