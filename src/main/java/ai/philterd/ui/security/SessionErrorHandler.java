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

import ai.philterd.philter.model.exceptions.UnauthorizedException;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.server.DefaultErrorHandler;
import com.vaadin.flow.server.ErrorEvent;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * Handles exceptions from event listeners and data callbacks, such as a grid loading its rows. A rejected
 * session key, which happens when it expires or is revoked, ends the person's session; {@code SessionEndedView}
 * covers the same while navigating. Any other failed request to Philter is shown to the person with Philter's
 * explanation, rather than only logged. Everything else goes to Vaadin's default handling.
 */
@Component
public class SessionErrorHandler implements VaadinServiceInitListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionErrorHandler.class);

    /** A message repeated within this long, such as a grid's count and rows both failing, is shown once. */
    static final long REPEAT_WINDOW_NANOS = 3_000_000_000L;

    private final Sessions sessions;

    public SessionErrorHandler(final Sessions sessions) {
        this.sessions = sessions;
    }

    @Override
    public void serviceInit(final ServiceInitEvent event) {
        event.getSource().addSessionInitListener(init -> init.getSession().setErrorHandler(this::handle));
    }

    private void handle(final ErrorEvent event) {

        if (isSessionKeyRejected(event.getThrowable())) {
            sessions.end(Notice.ENDED);
            return;
        }

        final String message = PhilterFailures.messageFor(event.getThrowable());
        final UI ui = UI.getCurrent();
        if (message == null || ui == null) {
            new DefaultErrorHandler().error(event);
            return;
        }

        LOGGER.warn("A request to Philter failed: {}", message);
        final LastFailure last = ComponentUtil.getData(ui, LastFailure.class);
        final long now = System.nanoTime();
        if (isRepeat(last, message, now)) {
            return;
        }
        ComponentUtil.setData(ui, LastFailure.class, new LastFailure(message, now));

        final Notification notification = new Notification(message, 5000, Notification.Position.BOTTOM_START);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        notification.open();

    }

    /** Whether the message was just shown, so showing it again would only repeat it. */
    static boolean isRepeat(final LastFailure last, final String message, final long now) {
        return last != null && last.message().equals(message) && now - last.at() < REPEAT_WINDOW_NANOS;
    }

    /** The last failure shown in a browser tab, and when. Kept on the UI, so serializable with the session. */
    record LastFailure(String message, long at) implements Serializable {
    }

    public static boolean isSessionKeyRejected(final Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof UnauthorizedException) {
                return true;
            }
        }
        return false;
    }

}
