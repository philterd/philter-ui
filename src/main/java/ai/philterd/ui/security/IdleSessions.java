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
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.SessionDestroyEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.WrappedSession;
import org.springframework.stereotype.Component;

/**
 * Signs a person out when they have not interacted with Philter UI for {@code SESSION_TIMEOUT_MINUTES}, even
 * with the tab still open.
 *
 * <p>With {@code vaadin.closeIdleSessions}, Vaadin closes its session once no request other than a heartbeat
 * has arrived for the session timeout. The HTTP session, which holds the sign-in, would otherwise live on,
 * kept alive by the heartbeats, and the next click would start a new Vaadin session still signed in. Ending
 * the HTTP session with the Vaadin one signs the person out, and {@link SessionKeyRevoker} revokes the key.
 * On the person's next interaction, the browser is sent to the sign-in page with the "session has ended" notice.
 */
@Component
public class IdleSessions implements VaadinServiceInitListener {

    @Override
    public void serviceInit(final ServiceInitEvent event) {
        event.getSource().addSessionDestroyListener(IdleSessions::endSignIn);
        event.getSource().setSystemMessagesProvider(info -> messages(info.getRequest() == null ? ""
                : info.getRequest().getContextPath()));
    }

    private static void endSignIn(final SessionDestroyEvent event) {
        final WrappedSession session = event.getSession().getSession();
        if (session == null) {
            return;
        }
        try {
            session.invalidate();
        } catch (final IllegalStateException alreadyEnded) {
            // The HTTP session ended first, for example on sign-out or after the tab was closed.
        }
    }

    /** Vaadin's messages, sending a browser whose session has ended straight to the sign-in page. */
    static CustomizedSystemMessages messages(final String contextPath) {
        final CustomizedSystemMessages messages = new CustomizedSystemMessages();
        messages.setSessionExpiredURL((contextPath == null ? "" : contextPath) + "/login?notice="
                + Notice.ENDED.getParameter());
        messages.setSessionExpiredNotificationEnabled(false);
        return messages;
    }

}
