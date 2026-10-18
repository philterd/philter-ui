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
package ai.philterd.ui;

import ai.philterd.philter.model.Limits;
import ai.philterd.ui.model.PhilterRules;
import com.google.gson.Gson;

/** Philter's rules as a default Philter reports them from {@code GET /api/limits}, for tests. */
public final class TestRules {

    /** The response a default Philter gives a user with no contexts. */
    public static final String LIMITS = """
            {
              "requests": {"maxDocumentBytes": 10485760, "maxBodyBytes": 262144, "defaultPageSize": 25, "maxPageSize": 100},
              "password": {"minCharacters": 16, "maxBytes": 72},
              "names": {"forbiddenCharacters": ["/", "\\\\", ";", "%"], "controlCharactersForbidden": true,
                        "reservedNames": [".", ".."],
                        "rule": "cannot contain /, \\\\, ;, %, or control characters, and cannot be . or .."},
              "policies": {"nameMaxLength": 50, "namePattern": "^[a-zA-Z0-9_-]+$", "reservedNamePrefix": "managed_",
                           "reservedNames": ["templates"], "defaultPolicyName": "default",
                           "descriptionMaxLength": 200, "notesMaxLength": 1000},
              "customLists": {"maxItems": 100, "itemMaxLength": 50, "descriptionMaxLength": 200},
              "redactLists": {"maxTerms": 1000, "termMaxLength": 100},
              "contexts": {"maxPerUser": 10, "maxEntries": 10000},
              "webhook": {"secretMinLength": 16},
              "legalHolds": {"scopeTypes": ["document_chain", "user"]},
              "users": {"roles": ["admin", "user"], "reservedUsernames": ["me"]},
              "auditExport": {"maxWindowDays": 30, "defaultPageSize": 100, "maxPageSize": 1000},
              "sessionKeys": {"idleTimeoutMinutes": 30, "maxLifetimeMinutes": 720},
              "caller": {"role": "user", "contextCount": 0, "crossUserAccess": false, "ledgerDeletion": false}
            }
            """;

    private TestRules() {
    }

    /** A default Philter's limits, fresh each call so a test can change them. */
    public static Limits limits() {
        return new Gson().fromJson(LIMITS, Limits.class);
    }

    /** A default Philter's rules. */
    public static PhilterRules defaults() {
        return PhilterRules.of(limits());
    }

}
