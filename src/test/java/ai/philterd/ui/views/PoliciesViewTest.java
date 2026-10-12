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

import ai.philterd.philter.model.DeletedPolicy;
import ai.philterd.philter.model.PolicyVersionSummary;
import ai.philterd.philter.model.exceptions.ClientException;
import ai.philterd.philter.model.exceptions.ServiceUnavailableException;
import ai.philterd.ui.model.PolicyJson;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    @Test
    void aNewPolicyStartsFromPhiltersTemplate() {
        final PoliciesView.NewPolicy start = PoliciesView.newPolicy(name -> {
            assertEquals("default", name);
            return "{\"identifiers\":{\"ssn\":{\"ssnFilterStrategies\":[{\"strategy\":\"REDACT\"}]}}}";
        });
        assertNull(start.problem());
        assertTrue(start.json().contains("\"ssnFilterStrategies\""));
        assertTrue(start.json().contains("\n"), "shown indented");
    }

    @Test
    void aNewPolicyStartsEmptyWhenTheTemplateCannotBeRead() {
        for (final PoliciesView.TemplateSource failing : new PoliciesView.TemplateSource[] {
                name -> { throw new ClientException("HTTP 404", 404, "No such template."); },
                name -> { throw new ServiceUnavailableException("Philter is unavailable."); },
                name -> { throw new IOException("connection refused"); },
                name -> "not json"}) {
            final PoliciesView.NewPolicy start = PoliciesView.newPolicy(failing);
            assertEquals(PolicyJson.EMPTY, start.json());
            assertEquals(PoliciesView.TEMPLATE_UNAVAILABLE, start.problem());
        }
    }

    @Test
    void deletedPoliciesShowWhenAndByWhomOrBlank() {
        final DeletedPolicy recorded = new DeletedPolicy();
        recorded.setName("claims-2025");
        recorded.setLatestRevision(4);
        recorded.setDeletedAt("2026-10-02T09:30:00Z");
        recorded.setDeletedBy("jordan");
        assertEquals("2026-10-02 09:30 UTC", PoliciesView.deletedAt(recorded));
        assertEquals("jordan", PoliciesView.deletedBy(recorded));

        // A deletion made before Philter recorded deletions has neither.
        final DeletedPolicy unrecorded = new DeletedPolicy();
        unrecorded.setName("old");
        assertEquals("", PoliciesView.deletedAt(unrecorded));
        assertEquals("", PoliciesView.deletedBy(unrecorded));
    }

    @Test
    void restoringSavesTheChosenRevisionUnderTheSameName() throws IOException {
        final List<String> calls = new ArrayList<>();
        PolicyHistoryDialog.restore(new PolicyHistoryDialog.Restorer() {
            @Override
            public String read(final String name, final int revision) {
                calls.add("read " + name + " " + revision);
                return "{\"identifiers\":{}}";
            }

            @Override
            public void save(final String name, final String json) {
                calls.add("save " + name + " " + json);
            }
        }, "claims-2025", 3);
        assertEquals(List.of("read claims-2025 3", "save claims-2025 {\"identifiers\":{}}"), calls);
    }

    @Test
    void aRestoreOverALivePolicyIsExplained() {
        assertTrue(PolicyHistoryDialog.restoreFailure("claims", refused(409, "Exists.", "policy_exists"))
                .startsWith("You already have a policy named claims"));
        assertEquals("Invalid policy.", PolicyHistoryDialog.restoreFailure("claims", refused(400, "Invalid policy.", null)));
        assertEquals("The policy could not be restored.", PolicyHistoryDialog.restoreFailure("claims", refused(500, null, null)));
    }

    @Test
    void eachRevisionNamesItsAuthorOrIsBlank() {
        final PolicyVersionSummary version = new PolicyVersionSummary();
        version.setAuthor("jordan");
        assertEquals("jordan", PolicyHistoryDialog.author(version));
        version.setAuthor(null);
        assertEquals("", PolicyHistoryDialog.author(version));
    }

}
