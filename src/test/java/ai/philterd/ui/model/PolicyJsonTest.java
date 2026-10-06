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
package ai.philterd.ui.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyJsonTest {

    @Test
    void theTemplateIsAValidPolicyObject() {
        assertNull(PolicyJson.problem(PolicyJson.template()));
        assertTrue(PolicyJson.template().contains("\"identifiers\""));
    }

    @Test
    void onlyAJsonObjectIsAPolicy() {
        assertNull(PolicyJson.problem("{\"identifiers\":{}}"));
        assertEquals("Enter the policy's JSON.", PolicyJson.problem("  "));
        assertEquals("The policy is not valid JSON.", PolicyJson.problem("{\"identifiers\":"));
        assertEquals("A policy must be a JSON object.", PolicyJson.problem("[1, 2]"));
    }

    @Test
    void prettyPrintingKeepsTextThatIsNotJson() {
        assertEquals("{\n  \"a\": 1\n}", PolicyJson.pretty("{\"a\":1}"));
        assertEquals("not json {", PolicyJson.pretty("not json {"));
        assertEquals("{\n  \"redactionFormat\": \"{{{REDACTED-%t}}}\"\n}", PolicyJson.pretty("{\"redactionFormat\":\"{{{REDACTED-%t}}}\"}"));
    }

    @Test
    void formattingDoesNotMakeJsonDifferent() {
        assertTrue(PolicyJson.sameJson("{\"a\":1,\"b\":[1,2]}", "{\n  \"a\": 1,\n  \"b\": [1, 2]\n}"));
        assertFalse(PolicyJson.sameJson("{\"a\":1}", "{\"a\":2}"));
    }

    @Test
    void differencesShowBeforeAndAfter() {
        final List<PolicyJson.Difference> differences = PolicyJson.differences(
                "{\"identifiers\":{\"ssn\":{\"s\":\"REDACT\"},\"email\":{}},\"name\":\"a\"}",
                "{\"identifiers\":{\"ssn\":{\"s\":\"MASK\"},\"phone\":{}},\"name\":\"a\"}");
        assertEquals(List.of(
                new PolicyJson.Difference("replace", "/identifiers/ssn/s", "REDACT", "MASK"),
                new PolicyJson.Difference("remove", "/identifiers/email", "{}", ""),
                new PolicyJson.Difference("add", "/identifiers/phone", "", "{}")), differences);
    }

    @Test
    void identicalRevisionsHaveNoDifferences() {
        assertEquals(List.of(), PolicyJson.differences("{\"a\":[1,2]}", "{ \"a\" : [1, 2] }"));
    }

}
