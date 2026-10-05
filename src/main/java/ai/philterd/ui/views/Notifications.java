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
import ai.philterd.ui.security.PhilterErrors;
import ai.philterd.ui.security.SignInMessages;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;

import java.io.IOException;

/** Short messages at the bottom of the page after an action. */
final class Notifications {

    private Notifications() {
    }

    static void success(final String message) {
        show(message, NotificationVariant.LUMO_SUCCESS);
    }

    static void failure(final String message) {
        show(message, NotificationVariant.LUMO_ERROR);
    }

    /** Reports a failed request to Philter, with Philter's explanation when it gave one. */
    static void failure(final Exception exception, final String fallback) {
        if (exception instanceof IOException || exception instanceof ServiceUnavailableException) {
            failure(SignInMessages.UNAVAILABLE);
        } else if (exception instanceof ClientException client && PhilterErrors.message(client) != null) {
            failure(PhilterErrors.message(client));
        } else {
            failure(fallback);
        }
    }

    private static void show(final String message, final NotificationVariant variant) {
        final Notification notification = new Notification(message, 5000, Notification.Position.BOTTOM_START);
        notification.addThemeVariants(variant);
        notification.open();
    }

}
