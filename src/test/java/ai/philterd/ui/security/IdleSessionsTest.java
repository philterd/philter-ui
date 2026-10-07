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
package ai.philterd.ui.security;

import com.vaadin.flow.server.CustomizedSystemMessages;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class IdleSessionsTest {

    @Test
    void anEndedSessionGoesStraightToSignInWithTheNotice() {
        final CustomizedSystemMessages messages = IdleSessions.messages("");
        assertEquals("/login?notice=ended", messages.getSessionExpiredURL());
        assertFalse(messages.isSessionExpiredNotificationEnabled());
    }

    @Test
    void theSignInPageIsFoundUnderTheContextPath() {
        assertEquals("/philter-ui/login?notice=ended", IdleSessions.messages("/philter-ui").getSessionExpiredURL());
        assertEquals("/login?notice=ended", IdleSessions.messages(null).getSessionExpiredURL());
    }

}
