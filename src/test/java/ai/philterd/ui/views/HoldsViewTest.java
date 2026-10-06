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

import ai.philterd.philter.model.LegalHoldRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoldsViewTest {

    @Test
    void aDocumentHoldSendsTheDocumentId() {
        final LegalHoldRequest request = HoldsView.request(" LIT-2026-001 ", HoldsView.SCOPE_DOCUMENT_CHAIN,
                " doc-abc123 ", " Counsel directive ", "jordan");
        assertEquals("LIT-2026-001", request.getReference());
        assertEquals("document_chain", request.getScopeType());
        assertEquals("doc-abc123", request.getScopeValue());
        assertEquals("Counsel directive", request.getReason());
    }

    @Test
    void aUserHoldSendsTheOwnersUsernameWhichPhilterRequiresButDoesNotUse() {
        final LegalHoldRequest request = HoldsView.request("AUDIT-7", HoldsView.SCOPE_USER, "", "", "jordan");
        assertEquals("user", request.getScopeType());
        assertEquals("jordan", request.getScopeValue());
        assertNull(request.getReason());
    }

    @Test
    void referencesArePathSafeAndNotBlank() {
        assertNull(HoldsView.referenceProblem("LIT-2026-001"));
        assertNull(HoldsView.referenceProblem("Case 7 v2.1"));
        assertEquals("Enter a reference.", HoldsView.referenceProblem("  "));
        assertTrue(HoldsView.referenceProblem("LIT/2026/001").startsWith("The reference cannot contain /"));
    }

    @Test
    void scopesAreLabeledForPeople() {
        assertEquals("A document's ledger chain", HoldsView.scopeLabel("document_chain"));
        assertEquals("All of the user's evidence", HoldsView.scopeLabel("user"));
        assertEquals("something_new", HoldsView.scopeLabel("something_new"));
    }

    @Test
    void setTimesAreShownInUtc() {
        assertEquals("2026-10-06 14:24 UTC", HoldsView.setAt("2026-10-06T14:24:13.920Z"));
        assertEquals("not a time", HoldsView.setAt("not a time"));
        assertEquals("", HoldsView.setAt(null));
    }

}
