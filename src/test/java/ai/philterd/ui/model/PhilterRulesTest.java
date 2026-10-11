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

import ai.philterd.philter.model.Limits;
import ai.philterd.ui.TestRules;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhilterRulesTest {

    private final PhilterRules rules = TestRules.defaults();
    private final PhilterRules unknown = PhilterRules.unknown();

    @Test
    void passwordsFollowPhiltersRules() {
        assertEquals("The password must be at least 16 characters.", rules.passwordProblem("short"));
        assertEquals("The password must be at most 72 bytes.", rules.passwordProblem("x".repeat(73)));
        // A character outside ASCII counts by its bytes, as Philter counts it.
        assertEquals("The password must be at most 72 bytes.", rules.passwordProblem("é".repeat(37)));
        assertNull(rules.passwordProblem("x".repeat(16)));
        assertTrue(rules.passwordRules().startsWith("At least 16 characters, and at most 72 bytes."));
    }

    @Test
    void namesUsedInPathsFollowPhiltersRule() {
        assertTrue(rules.isPathSafe("Case 7 v2.1"));
        assertFalse(rules.isPathSafe("a/b"));
        assertFalse(rules.isPathSafe("a%b"));
        assertFalse(rules.isPathSafe(".."));
        assertFalse(rules.isPathSafe("a\tb"));
        assertTrue(rules.pathSafeRule().startsWith("cannot contain /"));
    }

    @Test
    void policyNamesFollowPhiltersRules() {
        assertNull(rules.policyNameProblem("claims_2026-v1"));
        assertEquals("Enter a name.", rules.policyNameProblem(" "));
        assertEquals("A policy name can be at most 50 characters.", rules.policyNameProblem("x".repeat(51)));
        assertEquals("A policy name can contain only letters, digits, dashes, and underscores.",
                rules.policyNameProblem("has space"));
        assertTrue(rules.policyNameProblem("managed_x").startsWith("A policy name cannot start with managed_"));
        assertEquals("The name templates is reserved by Philter.", rules.policyNameProblem("templates"));
        assertEquals("Up to 50 letters, digits, dashes, and underscores, not starting with managed_.", rules.policyNameRule());
        assertTrue(rules.isDefaultPolicy("default"));
        assertFalse(rules.isDefaultPolicy("other"));
    }

    @Test
    void patternsAreDescribedInWords() {
        assertEquals("lowercase letters and digits", PhilterRules.characters("^[a-z0-9]+$"));
        assertEquals("characters matching ^\\w+$", PhilterRules.characters("^\\w+$"));
        assertEquals("characters matching ^[a-z.]+$", PhilterRules.characters("^[a-z.]+$"));
    }

    @Test
    void limitsAreThoseReported() {
        assertEquals(100, rules.customListMaximumItems());
        assertEquals(50, rules.customListItemMaximumLength());
        assertEquals(1000, rules.redactListMaximumTerms());
        assertEquals(100, rules.redactListTermMaximumLength());
        assertEquals(16, rules.webhookSecretMinimumLength());
        assertEquals(List.of("document_chain", "user"), rules.holdScopeTypes());
        assertEquals(List.of("admin", "user"), rules.roles());
        assertEquals(30, rules.auditExportMaximumWindowDays());
        assertEquals(1000, rules.auditExportMaximumPageSize());
        assertEquals(100, rules.maximumPageSize());
        assertEquals(10485760L, rules.maximumDocumentBytes());
        assertEquals(10, rules.contextLimit());
    }

    @Test
    void theCallerIsDescribed() {
        assertEquals("user", rules.callerRole());
        assertFalse(rules.mayReachOtherUsers());
        assertFalse(rules.mayDeleteLedgerChains());
        assertFalse(rules.isContextLimitReached());

        final Limits limits = TestRules.limits();
        limits.getCaller().setRole("admin");
        limits.getCaller().setCrossUserAccess(true);
        limits.getCaller().setLedgerDeletion(true);
        limits.getCaller().setContextCount(10);
        final PhilterRules admin = PhilterRules.of(limits);
        assertEquals("admin", admin.callerRole());
        assertTrue(admin.mayReachOtherUsers());
        assertTrue(admin.mayDeleteLedgerChains());
        assertTrue(admin.isContextLimitReached());
    }

    @Test
    void unknownRulesLeaveChecksToPhilterAndGrantNothing() {
        assertFalse(unknown.isKnown());
        assertTrue(rules.isKnown());
        assertNull(unknown.passwordProblem("x"));
        assertTrue(unknown.isPathSafe("a/b"));
        assertNull(unknown.policyNameProblem("has space"));
        assertEquals("Enter a name.", unknown.policyNameProblem(""));
        assertNull(unknown.customListMaximumItems());
        assertNull(unknown.maximumDocumentBytes());
        assertNull(unknown.defaultPolicyName());
        assertFalse(unknown.isDefaultPolicy("default"));
        assertNull(unknown.callerRole());
        assertFalse(unknown.mayReachOtherUsers());
        assertFalse(unknown.mayDeleteLedgerChains());
        assertFalse(unknown.isContextLimitReached());
        assertEquals(List.of(), unknown.roles());
        assertEquals(List.of(), unknown.holdScopeTypes());
    }

}
