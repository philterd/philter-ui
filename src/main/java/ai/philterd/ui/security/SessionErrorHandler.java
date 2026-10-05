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
import com.vaadin.flow.server.DefaultErrorHandler;
import com.vaadin.flow.server.ErrorEvent;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import org.springframework.stereotype.Component;

/**
 * Ends the person's session when Philter rejects their session key, which happens when it expires or is
 * revoked. Covers exceptions from event listeners; {@code SessionEndedView} covers those thrown while
 * navigating.
 */
@Component
public class SessionErrorHandler implements VaadinServiceInitListener {

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
        } else {
            new DefaultErrorHandler().error(event);
        }
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
