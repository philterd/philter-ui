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

import ai.philterd.philter.model.Limits;
import ai.philterd.ui.TestRules;
import ai.philterd.ui.model.PhilterRules;
import ai.philterd.ui.security.PhilterUser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MainLayoutTest {

    private static PhilterUser user(final boolean administrator) {
        return new PhilterUser("jordan", administrator, PhilterUser.Restriction.NONE, "key", "id");
    }

    private static PhilterRules role(final String role) {
        final Limits limits = TestRules.limits();
        limits.getCaller().setRole(role);
        return PhilterRules.of(limits);
    }

    @Test
    void anUnchangedRoleChangesNothing() {
        assertNull(MainLayout.administratorChange(user(false), role("user")));
        assertNull(MainLayout.administratorChange(user(true), role("admin")));
    }

    @Test
    void aChangedRoleIsFollowed() {
        assertEquals(Boolean.TRUE, MainLayout.administratorChange(user(false), role("admin")));
        assertEquals(Boolean.FALSE, MainLayout.administratorChange(user(true), role("user")));
    }

    @Test
    void anUnknownRoleKeepsTheSessionAsItIs() {
        assertNull(MainLayout.administratorChange(user(true), PhilterRules.unknown()));
    }

}
