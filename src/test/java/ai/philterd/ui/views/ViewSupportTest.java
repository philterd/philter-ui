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
import ai.philterd.philter.model.exceptions.ServiceUnavailableException;
import ai.philterd.ui.security.SignInMessages;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ViewSupportTest {

    @Test
    void timesFromPhilterAreShownInUtcWhetherWrittenWithZOrAnOffset() {
        assertEquals("2026-10-06 14:24 UTC", ViewSupport.utc("2026-10-06T14:24:13.920Z"));
        assertEquals("2026-06-08 14:11 UTC", ViewSupport.utc("2026-06-08T14:11:33.000+00:00"));
        assertEquals("2026-06-08 12:11 UTC", ViewSupport.utc("2026-06-08T14:11:33.000+02:00"));
    }

    @Test
    void anUnreadableTimeIsShownAsPhilterSentIt() {
        assertEquals("not a time", ViewSupport.utc("not a time"));
        assertEquals("", ViewSupport.utc(null));
        assertEquals("", ViewSupport.utc(" "));
    }

    @Test
    void anUnchangedFieldIsLeftOutSoPhilterKeepsIt() {
        assertNull(ViewSupport.change("Employee IDs", "Employee IDs"));
        assertNull(ViewSupport.change("Employee IDs", " Employee IDs "));
        assertNull(ViewSupport.change(null, ""));
    }

    @Test
    void aChangedFieldIsSentAndAClearedOneIsSentEmpty() {
        assertEquals("Contractor IDs", ViewSupport.change("Employee IDs", "Contractor IDs"));
        assertEquals("", ViewSupport.change("Employee IDs", "   "));
        assertEquals("New", ViewSupport.change("", "New"));
    }

    @Test
    void anUnreadableSettingsReadSaysWhy() {
        assertEquals(ViewSupport.SETTINGS_UNREADABLE + " An administrator is required.",
                ViewSupport.settingsFailure(new ClientException("HTTP 403", 403, "An administrator is required.")));
        assertEquals(ViewSupport.SETTINGS_UNREADABLE + " Philter answered with HTTP 500.",
                ViewSupport.settingsFailure(new ClientException("HTTP 500", 500, null)));
        assertEquals(ViewSupport.SETTINGS_UNREADABLE + " " + SignInMessages.UNAVAILABLE,
                ViewSupport.settingsFailure(new ServiceUnavailableException("down")));
        assertEquals("Philter answered with HTTP 502.", ViewSupport.why(new ClientException("HTTP 502", 502, null)));
    }

}
