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

import ai.philterd.philter.model.exceptions.ClientException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PoliciesViewTest {

    private static ClientException refused(final int status, final String message, final String reason) {
        return new ClientException("HTTP " + status, status, message, reason);
    }

    @Test
    void replaceRefusalsAreExplainedByReason() {
        assertEquals("The policy changed since you opened it. Cancel and edit it again to see the latest.",
                PoliciesView.replaceFailure(refused(409, "Changed concurrently.", "policy_changed")));
        assertEquals("Managed policies cannot be changed. Copy it and change the copy instead.",
                PoliciesView.replaceFailure(refused(409, "Managed.", "policy_managed")));
        assertEquals("The policy must contain a non-empty identifiers object.",
                PoliciesView.replaceFailure(refused(400, "The policy must contain a non-empty identifiers object.", null)));
        assertEquals("Philter did not accept the policy.", PoliciesView.replaceFailure(refused(500, null, null)));
    }

    @Test
    void aTakenNameIsReportedWhenCopying() {
        assertEquals("You already have a policy with this name.",
                PoliciesView.copyFailure(refused(409, "A policy with this name already exists.", "policy_exists")));
        assertEquals("Not found.", PoliciesView.copyFailure(refused(404, "Not found.", null)));
    }

    @Test
    void thePolicyEditorOpensAtPhiltersSchemaVersion() {
        assertEquals("https://policies.philterd.ai/?version=1.3.0", PoliciesView.policyEditorUrl("1.3.0"));
        assertEquals("https://policies.philterd.ai/", PoliciesView.policyEditorUrl(null));
        assertEquals("https://policies.philterd.ai/?version=1.3+beta%261", PoliciesView.policyEditorUrl("1.3 beta&1"));
    }

    @Test
    void rollbackRefusalsAreExplainedByReason() {
        assertEquals("The policy changed while it was being rolled back. Open its history again and retry.",
                PolicyHistoryDialog.rollbackFailure(refused(409, "Changed.", "policy_changed")));
        assertEquals("Revision 9 does not exist.",
                PolicyHistoryDialog.rollbackFailure(refused(404, "Revision 9 does not exist.", null)));
        assertEquals("The policy could not be rolled back.", PolicyHistoryDialog.rollbackFailure(refused(404, null, null)));
    }

    @Test
    void hashesAreShortened() {
        assertEquals("9f86d081…", PolicyHistoryDialog.shortHash("9f86d081884c7d659a2feaa0c55ad015"));
        assertEquals("abc", PolicyHistoryDialog.shortHash("abc"));
        assertEquals("", PolicyHistoryDialog.shortHash(null));
    }

}
