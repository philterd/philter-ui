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

import ai.philterd.philter.model.exceptions.ErrorReasons;
import ai.philterd.philter.model.exceptions.UnauthorizedException;
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

/**
 * Handles exceptions from event listeners and data callbacks, such as a grid loading its rows. A rejected
 * session key, which happens when it expires or is revoked, ends the person's session; {@code SessionEndedView}
 * covers the same while navigating. Any other failed request to Philter is shown to the person with Philter's
 * explanation, rather than only logged. Everything else goes to Vaadin's default handling.
 */
@Component
public class SessionErrorHandler implements VaadinServiceInitListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionErrorHandler.class);

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
            sessions.end(noticeFor(event.getThrowable()));
            return;
        }

        final String message = PhilterFailures.messageFor(event.getThrowable());
        final UI ui = UI.getCurrent();
        if (message == null || ui == null) {
            new DefaultErrorHandler().error(event);
            return;
        }

        LOGGER.warn("A request to Philter failed: {}", message);

        final Notification notification = new Notification(message, 5000, Notification.Position.BOTTOM_START);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        notification.open();

    }

    public static boolean isSessionKeyRejected(final Throwable throwable) {
        return rejection(throwable) != null;
    }

    /**
     * What to tell a person whose session key Philter rejected: that their account was deactivated, when Philter
     * says so, or otherwise that their session ended and they should sign in again.
     */
    public static Notice noticeFor(final Throwable throwable) {
        final UnauthorizedException rejection = rejection(throwable);
        return rejection != null && ErrorReasons.USER_DEACTIVATED.equals(rejection.getReason())
                ? Notice.DEACTIVATED : Notice.ENDED;
    }

    private static UnauthorizedException rejection(final Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof UnauthorizedException unauthorized) {
                return unauthorized;
            }
        }
        return null;
    }

}
