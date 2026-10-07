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

class IdleTimerTest {

    @Test
    void activityIsReportedWellInsideTheTimeout() {
        // The default 30 minutes: at most once a minute.
        assertEquals(60_000, IdleTimer.reportMillis(30 * 60_000L));
        // A short timeout: a quarter of it, so a person typing is never mistaken for idle.
        assertEquals(15_000, IdleTimer.reportMillis(60_000));
        // Never more often than once a second.
        assertEquals(1_000, IdleTimer.reportMillis(2_000));
    }

}
