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

import ai.philterd.philter.PhilterClient;
import ai.philterd.philter.model.exceptions.ClientException;
import ai.philterd.philter.model.exceptions.ServiceUnavailableException;
import ai.philterd.ui.model.PhilterRules;
import ai.philterd.ui.security.PhilterFailures;
import ai.philterd.ui.security.PhilterUnreachableException;
import ai.philterd.ui.security.SignInMessages;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinService;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/** Small pieces shared by the views that list and edit Philter resources. */
final class ViewSupport {

    /** A call to Philter that returns something. */
    interface PhilterQuery<T> {
        T get() throws IOException;
    }

    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);

    private ViewSupport() {
    }

    /** Why a request to Philter failed: Philter's explanation, its status, or that it cannot be reached. */
    static String why(final RuntimeException e) {
        final String message = PhilterFailures.messageFor(e);
        return message == null ? SignInMessages.UNAVAILABLE : message;
    }

    /** The request attribute Philter's rules are kept under, so they are read once per request. */
    private static final String RULES = PhilterRules.class.getName();

    /**
     * Philter's rules and what the person may do, from {@code GET /api/limits}, read once per request. When they
     * cannot be read, the rules are unknown: checks pass, so Philter refuses invalid input itself, and
     * capabilities are off. A rejected session key is not caught, so the session still ends.
     */
    static PhilterRules rules(final PhilterClient client) {
        final VaadinRequest request = VaadinService.getCurrentRequest();
        if (request != null && request.getAttribute(RULES) instanceof PhilterRules cached) {
            return cached;
        }
        PhilterRules rules;
        try {
            rules = PhilterRules.of(client.getLimits());
        } catch (final ClientException | ServiceUnavailableException | IOException e) {
            rules = PhilterRules.unknown();
        }
        if (request != null) {
            request.setAttribute(RULES, rules);
        }
        return rules;
    }

    /** Philter's rules read again, for after a change in this request that they count, such as a new context. */
    static PhilterRules rereadRules(final PhilterClient client) {
        final VaadinRequest request = VaadinService.getCurrentRequest();
        if (request != null) {
            request.removeAttribute(RULES);
        }
        return rules(client);
    }

    /**
     * Whether to show every user's resources: Philter says the person may reach them, which needs an
     * administrator and {@code ADMIN_CROSS_USER_ACCESS_ENABLED}.
     */
    static boolean showAllUsers(final PhilterClient client) {
        return rules(client).mayReachOtherUsers();
    }

    /** A time from Philter in UTC, or Philter's text unchanged if it cannot be read. */
    static String utc(final String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return UTC.format(OffsetDateTime.parse(value));
        } catch (final DateTimeParseException e) {
            return value;
        }
    }

    /**
     * What to send for a text field Philter keeps unless told otherwise: {@code null} when unchanged, so
     * Philter keeps its value, an empty string to clear it, or the new text.
     */
    static String change(final String original, final String edited) {
        final String value = edited == null ? "" : edited.trim();
        return value.equals(original == null ? "" : original.trim()) ? null : value;
    }

    /** A grid row's button, labeled for screen readers with its tooltip. */
    static Button button(final String text, final VaadinIcon icon, final String tooltip, final Runnable action) {
        final Button button = new Button(text, icon.create(), e -> action.run());
        button.setTooltipText(tooltip);
        button.setAriaLabel(tooltip);
        return button;
    }

    static Button cancel(final Dialog dialog) {
        final Button cancel = new Button("Cancel", e -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        return cancel;
    }

    static <T> List<T> orEmpty(final List<T> items) {
        return items == null ? List.of() : items;
    }

    /** Runs a call to Philter where a checked exception cannot be thrown, such as a grid's data callback. */
    static <T> T unchecked(final PhilterQuery<T> query) {
        try {
            return query.get();
        } catch (final IOException e) {
            throw new PhilterUnreachableException(e);
        }
    }

}
