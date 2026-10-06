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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

}
