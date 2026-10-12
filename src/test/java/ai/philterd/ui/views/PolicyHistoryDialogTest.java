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

import ai.philterd.philter.model.PolicyDiff;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PolicyHistoryDialogTest {

    private static PolicyDiff diff(final String json) {
        return new Gson().fromJson(json, PolicyDiff.class);
    }

    @Test
    void eachChangeShowsTheValuesBeforeAndAfter() {
        final PolicyDiff diff = diff("""
                {"from": 2, "to": 3, "changes": [
                  {"op": "replace", "path": "/identifiers/ssn/ssnFilterStrategies", "value": [{"strategy": "MASK"}],
                   "oldValue": [{"strategy": "REDACT"}]},
                  {"op": "replace", "path": "/config/enabled", "value": false, "oldValue": true},
                  {"op": "remove", "path": "/identifiers/emailAddress", "oldValue": {}},
                  {"op": "add", "path": "/identifiers/phoneNumber", "value": {}}
                ]}
                """);
        assertEquals(List.of(
                new PolicyHistoryDialog.Change("replace", "/identifiers/ssn/ssnFilterStrategies",
                        "[{\"strategy\":\"REDACT\"}]", "[{\"strategy\":\"MASK\"}]"),
                new PolicyHistoryDialog.Change("replace", "/config/enabled", "true", "false"),
                new PolicyHistoryDialog.Change("remove", "/identifiers/emailAddress", "{}", ""),
                new PolicyHistoryDialog.Change("add", "/identifiers/phoneNumber", "", "{}")),
                PolicyHistoryDialog.changes(diff));
    }

    @Test
    void escapedPathsAreShownAsPhilterReturnsThem() {
        final PolicyDiff diff = diff("""
                {"from": 1, "to": 2, "changes": [
                  {"op": "add", "path": "/crypto/a~1b~0c", "value": "x"}
                ]}
                """);
        assertEquals("/crypto/a~1b~0c", PolicyHistoryDialog.changes(diff).get(0).path());
    }

    @Test
    void aValueSetToNullIsShownAsNull() {
        final PolicyDiff diff = diff("""
                {"from": 1, "to": 2, "changes": [
                  {"op": "replace", "path": "/config/x", "value": null, "oldValue": "y"}
                ]}
                """);
        assertEquals(new PolicyHistoryDialog.Change("replace", "/config/x", "y", "null"),
                PolicyHistoryDialog.changes(diff).get(0));
    }

    @Test
    void identicalRevisionsHaveNoChanges() {
        final PolicyDiff diff = diff("{\"from\": 4, \"to\": 5, \"changes\": []}");
        assertEquals(List.of(), PolicyHistoryDialog.changes(diff));
        assertEquals("Revisions 4 and 5 have the same content.", PolicyHistoryDialog.sameContent(diff));
    }

}
