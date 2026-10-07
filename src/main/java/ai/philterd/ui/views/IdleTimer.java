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

import ai.philterd.ui.security.Notice;
import ai.philterd.ui.security.Sessions;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.WrappedSession;

/**
 * Signs a person out, and shows the sign-in page, when they have not interacted with Philter UI for
 * {@code SESSION_TIMEOUT_MINUTES}, without waiting for them to click something.
 *
 * <p>The browser records the last key press, click, touch, or scroll in local storage, which every tab of the
 * same browser shares, so a tab left idle is not signed out while another tab of the same session is in use.
 * Typing does not reach the server by itself, so activity is also reported to the server now and then, and a
 * long form does not time out while it is being filled in. When the shared last activity is older than the
 * timeout, the tab asks the server to end the session, as signing out does. If the server already ended it,
 * Vaadin's session-expired redirect ({@code IdleSessions}) opens the same page. Without local storage, each
 * tab times out on its own activity.
 */
class IdleTimer extends Div {

    /** How often the browser checks for the timeout. */
    static final int CHECK_MILLIS = 10_000;

    /** The longest gap between reports of activity to the server. */
    static final int MAX_REPORT_MILLIS = 60_000;

    private static final String SCRIPT = """
            const host = this, timeout = $0, report = $1, check = $2, key = 'philter-ui-last-activity';
            if (host.idleTimerStop) { host.idleTimerStop(); }
            let local = Date.now(), reported = local;
            const save = () => { try { localStorage.setItem(key, String(local)); } catch (e) { } };
            const last = () => {
                try { return Math.max(local, Number(localStorage.getItem(key)) || 0); } catch (e) { return local; }
            };
            const active = () => {
                if (!host.isConnected) { host.idleTimerStop(); return; }
                local = Date.now();
                save();
                if (local - reported >= report) { reported = local; host.$server.active(); }
            };
            const events = ['keydown', 'mousedown', 'touchstart', 'wheel'];
            events.forEach(type => window.addEventListener(type, active, { capture: true, passive: true }));
            save();
            const timer = setInterval(() => {
                if (!host.isConnected) { host.idleTimerStop(); return; }
                if (Date.now() - last() >= timeout) { host.idleTimerStop(); host.$server.idle(); }
            }, check);
            host.idleTimerStop = () => {
                clearInterval(timer);
                events.forEach(type => window.removeEventListener(type, active, { capture: true }));
                host.idleTimerStop = null;
            };
            """;

    private final transient Sessions sessions;

    IdleTimer(final Sessions sessions) {
        this.sessions = sessions;
        getStyle().set("display", "none");
    }

    @Override
    protected void onAttach(final AttachEvent event) {
        super.onAttach(event);
        final WrappedSession session = VaadinSession.getCurrent() == null ? null : VaadinSession.getCurrent().getSession();
        final int timeoutSeconds = session == null ? -1 : session.getMaxInactiveInterval();
        if (timeoutSeconds <= 0) {
            return;
        }
        final long timeoutMillis = timeoutSeconds * 1000L;
        getElement().executeJs(SCRIPT, timeoutMillis, reportMillis(timeoutMillis), CHECK_MILLIS);
    }

    @Override
    protected void onDetach(final DetachEvent event) {
        getElement().executeJs("if (this.idleTimerStop) { this.idleTimerStop(); }");
        super.onDetach(event);
    }

    /** How often activity is reported to the server: often enough to land well inside the timeout. */
    static long reportMillis(final long timeoutMillis) {
        return Math.min(MAX_REPORT_MILLIS, Math.max(1_000, timeoutMillis / 4));
    }

    /** Reached from the browser while the person is active; the request itself is what keeps the session alive. */
    @ClientCallable
    void active() {
        // Nothing to do: Vaadin counts this request as interaction.
    }

    /** Reached from the browser when the person has been idle for the timeout. */
    @ClientCallable
    void idle() {
        sessions.end(Notice.ENDED);
    }

}
