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

import ai.philterd.philter.model.AdminSettings;
import ai.philterd.philter.model.AuditEvent;
import ai.philterd.philter.model.GetAuditLogResponse;
import ai.philterd.philter.model.UpdateAdminSettingsRequest;
import ai.philterd.philter.model.User;
import ai.philterd.ui.TestRules;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminViewTest {

    private static AdminSettings settings() {
        final AdminSettings settings = new AdminSettings();
        settings.setDiffuseCountsEnabled(false);
        settings.setSigningEnabled(true);
        settings.setWebhookAllowlist("hooks.example.com");
        settings.setPhieldEnabled(true);
        settings.setPhieldUrl("https://phield.example.com");
        settings.setPhieldSourceId("philter");
        settings.setPhieldOrganization("philter");
        settings.setPhieldApiKeySet(true);
        settings.setMfaAvailable(true);
        settings.setMfaRequired(false);
        return settings;
    }

    /** The form as loaded from {@link #settings()}, with the given Phield key and MFA choices. */
    private static AdminView.SettingsForm form(final String allowlist, final String phieldKey,
                                               final boolean mfaAvailable, final boolean mfaRequired) {
        return new AdminView.SettingsForm(false, true, allowlist, true, "https://phield.example.com", "philter",
                "philter", phieldKey, mfaAvailable, mfaRequired);
    }

    @Test
    void nothingIsSentWhenNothingChanged() {
        assertNull(AdminView.settingsChanges(settings(), form("hooks.example.com", null, true, false)));
        // Whitespace around a value is not a change.
        assertNull(AdminView.settingsChanges(settings(), form(" hooks.example.com ", null, true, false)));
    }

    @Test
    void onlyChangedSettingsAreSent() {
        final UpdateAdminSettingsRequest request = AdminView.settingsChanges(settings(),
                form("hooks.example.com, 203.0.113.0/24", null, true, true));
        assertEquals("hooks.example.com, 203.0.113.0/24", request.getWebhookAllowlist());
        assertEquals(Boolean.TRUE, request.getMfaRequired());
        assertNull(request.getDiffuseCountsEnabled());
        assertNull(request.getSigningEnabled());
        assertNull(request.getPhieldEnabled());
        assertNull(request.getPhieldUrl());
        assertNull(request.getPhieldSourceId());
        assertNull(request.getMfaAvailable());
        assertNull(request.getPhieldApiKey());
    }

    @Test
    void thePhieldKeyIsSentOnlyWhenTypedOrRemoved() {
        assertNull(AdminView.phieldKeyChange(false, ""), "an untouched key field must not remove the key");
        assertNull(AdminView.phieldKeyChange(false, null));
        assertEquals("a-new-phield-key", AdminView.phieldKeyChange(false, "a-new-phield-key"));
        assertEquals("", AdminView.phieldKeyChange(true, "ignored"));

        assertEquals("a-new-phield-key", AdminView.settingsChanges(settings(),
                form("hooks.example.com", "a-new-phield-key", true, false)).getPhieldApiKey());
        assertEquals("", AdminView.settingsChanges(settings(),
                form("hooks.example.com", "", true, false)).getPhieldApiKey());

        // Removing a key that is not set changes nothing.
        final AdminSettings noKey = settings();
        noKey.setPhieldApiKeySet(false);
        assertNull(AdminView.settingsChanges(noKey, form("hooks.example.com", "", true, false)));
    }

    @Test
    void turningMfaOffSendsBothSettings() {
        final AdminSettings required = settings();
        required.setMfaRequired(true);
        final UpdateAdminSettingsRequest request = AdminView.settingsChanges(required,
                form("hooks.example.com", null, false, false));
        assertEquals(Boolean.FALSE, request.getMfaAvailable());
        assertEquals(Boolean.FALSE, request.getMfaRequired());
    }

    @Test
    void auditRangesAreWholeDaysUpToThirtyOne() {
        final LocalDate day = LocalDate.of(2026, 10, 1);
        assertNull(AdminView.auditRangeProblem(day, day, 30));
        assertNull(AdminView.auditRangeProblem(day, day.plusDays(30), 30));
        assertEquals("An export can cover at most 31 days.", AdminView.auditRangeProblem(day, day.plusDays(31), 30));
        assertEquals("From must be on or before To.", AdminView.auditRangeProblem(day.plusDays(1), day, 30));
        assertEquals("Choose both a From and a To date.", AdminView.auditRangeProblem(null, day, 30));
        assertEquals("Choose both a From and a To date.", AdminView.auditRangeProblem(day, null, 30));
        assertEquals("An export can cover at most 8 days.", AdminView.auditRangeProblem(day, day.plusDays(8), 7));
        assertNull(AdminView.auditRangeProblem(day, day.plusDays(400), null));
    }

    @Test
    void csvPagesKeepOneHeader() {
        final String header = "timestamp,event,principal\n";
        AdminView.AuditCsv joined = AdminView.joinCsvPages(List.of(header + "a\nb\n", header + "c\n"));
        assertEquals(header + "a\nb\nc\n", joined.csv());
        assertEquals(3, joined.events());
        assertEquals(header + "a\n", AdminView.joinCsvPages(List.of(header + "a\n", header)).csv());
        assertEquals(header, AdminView.joinCsvPages(List.of(header)).csv());
        assertEquals(0, AdminView.joinCsvPages(List.of(header)).events());
        assertEquals("", AdminView.joinCsvPages(List.of()).csv());
        // A page that does not end in a newline is not run into the next one.
        assertEquals(header + "a\nb\n", AdminView.joinCsvPages(List.of(header + "a", header + "b")).csv());
        assertEquals("h\na\nb\n", AdminView.joinCsvPages(List.of("h\r\na\r\n", "h\r\nb\r\n")).csv());
    }

    @Test
    void anEventRepeatedAcrossPagesIsWrittenOnce() {
        // A new event pushed "b" from the end of the first page to the start of the second.
        final AdminView.AuditCsv joined = AdminView.joinCsvPages(List.of("h\nnew\na\nb\n", "h\nb\nc\n"));
        assertEquals("h\nnew\na\nb\nc\n", joined.csv());
        assertEquals(4, joined.events());
    }

    @Test
    void quotedNewlinesStayInTheirRecord() {
        assertEquals(List.of("h", "1,\"two\nlines\",x", "2,\"say \"\"hi\"\"\",y"),
                AdminView.csvRecords("h\n1,\"two\nlines\",x\n2,\"say \"\"hi\"\"\",y\n"));
        final AdminView.AuditCsv joined = AdminView.joinCsvPages(List.of("h\n1,\"two\nlines\",x\n",
                "h\n1,\"two\nlines\",x\n2,y\n"));
        assertEquals("h\n1,\"two\nlines\",x\n2,y\n", joined.csv());
        assertEquals(2, joined.events());
    }

    @Test
    void userStatesAreDescribed() {
        final User user = new User();
        user.setActive(true);
        user.setPasswordSet(true);
        assertEquals("Active", AdminView.status(user));
        assertEquals("Set", AdminView.passwordState(user));
        assertEquals("Off", AdminView.mfaState(user));
        user.setPasswordChangeRequired(true);
        user.setMfaEnabled(true);
        assertEquals("Must change", AdminView.passwordState(user));
        assertEquals("On", AdminView.mfaState(user));
        user.setMfaLocked(true);
        user.setActive(false);
        user.setPasswordSet(false);
        assertEquals("Locked", AdminView.mfaState(user));
        assertEquals("Deactivated", AdminView.status(user));
        assertEquals("None (API keys only)", AdminView.passwordState(user));
    }

    @Test
    void generatedPasswordsMeetTheRules() {
        final Set<String> seen = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            final String password = Passwords.generate(TestRules.defaults());
            assertNull(TestRules.defaults().passwordProblem(password));
            assertEquals(Passwords.GENERATED_LENGTH, password.length());
            assertTrue(password.chars().anyMatch(c -> Passwords.LOWER.indexOf(c) >= 0), password);
            assertTrue(password.chars().anyMatch(c -> Passwords.UPPER.indexOf(c) >= 0), password);
            assertTrue(password.chars().anyMatch(c -> Passwords.DIGITS.indexOf(c) >= 0), password);
            assertTrue(password.chars().anyMatch(c -> Passwords.SYMBOLS.indexOf(c) >= 0), password);
            assertFalse(password.matches(".*[l1O0].*"), password);
            seen.add(password);
        }
        assertEquals(50, seen.size());
    }

    private static AuditEvent event(final String username, final String id) {
        final AuditEvent event = new AuditEvent();
        event.setUsername(username);
        event.setApiKeyId(id);
        return event;
    }

    @Test
    void eachEventNamesWhoActedOrFallsBack() {
        assertEquals("jordan", AdminView.actor(event("jordan", "66f1")));
        // An event whose actor is not a user keeps the id Philter stored.
        assertEquals("66f1", AdminView.actor(event(null, "66f1")));
        assertEquals("-", AdminView.actor(event(null, null)));
        assertEquals("-", AdminView.actor(event(" ", "")));
    }

    @Test
    void auditFiltersMapToPhiltersParameters() {
        final AdminView.AuditFilter filter = AdminView.auditFilter(" policy_deleted ", LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 3), " jordan ");
        assertEquals("policy_deleted", filter.event());
        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), filter.from());
        // The last day is included in full: Philter's to is exclusive.
        assertEquals(Instant.parse("2026-10-04T00:00:00Z"), filter.to());
        assertEquals("jordan", filter.owner());
        assertEquals(AdminView.AuditFilter.NONE, AdminView.auditFilter("", null, null, "  "));
        assertEquals("From must be on or before To.",
                AdminView.auditFilterProblem(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1)));
        assertNull(AdminView.auditFilterProblem(null, LocalDate.of(2026, 10, 1)));
    }

    @Test
    void theAuditLogIsReadInPhilterSizedPagesWithTheTotal() throws IOException {
        final List<String> requests = new ArrayList<>();
        final AdminView.AuditFilter filter = AdminView.auditFilter("policy_deleted", null, null, null);
        final AdminView.AuditPage page = AdminView.readAudit((f, offset, limit) -> {
            assertEquals(filter, f);
            requests.add(offset + "+" + limit);
            final GetAuditLogResponse response = new GetAuditLogResponse();
            response.setEvents(Collections.nCopies(Math.min(limit, 230 - offset), event("jordan", "1")));
            response.setTotal(230);
            return response;
        }, filter, 50, 200);
        assertEquals(List.of("50+100", "150+100"), requests);
        assertEquals(180, page.events().size());
        assertEquals(230, page.total());
        assertEquals("230 events", AdminView.auditTotal(page.total()));
        assertEquals("1 event", AdminView.auditTotal(1));
    }

}
