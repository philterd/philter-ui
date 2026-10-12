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

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * How a failed request to Philter is shown: the explanation and, when Philter answered, the id it gave the request,
 * which a person can copy and quote to an administrator to find the request in Philter's audit log.
 */
public final class FailureNotice {

    /** How long a failure stays on screen, long enough to copy its request id. */
    static final int DURATION_MILLIS = 10_000;

    private FailureNotice() {
    }

    /** Shows a failure at the bottom of the page, with its request id when there is one. */
    public static void open(final String message, final String requestId) {
        final Notification notification = new Notification();
        notification.setDuration(DURATION_MILLIS);
        notification.setPosition(Notification.Position.BOTTOM_START);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        final Button close = new Button(VaadinIcon.CLOSE_SMALL.create(), e -> notification.close());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        close.setAriaLabel("Close");
        final HorizontalLayout row = new HorizontalLayout(content(message, requestId), close);
        row.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.START);
        notification.add(row);
        notification.open();
    }

    /** The message and, when there is one, the request id with a button that copies it. */
    static Component content(final String message, final String requestId) {
        final VerticalLayout content = new VerticalLayout(new Span(message));
        content.setPadding(false);
        content.setSpacing(false);
        if (requestId != null) {
            content.add(requestId(requestId));
        }
        return content;
    }

    /** The request id, selectable, with a button that copies it. */
    static Component requestId(final String requestId) {
        final Span text = new Span(requestIdText(requestId));
        text.getStyle().set("font-size", "var(--lumo-font-size-s)").set("user-select", "all");
        final Button copy = new Button("Copy", VaadinIcon.COPY.create(), e -> UI.getCurrent().getPage()
                .executeJs("navigator.clipboard.writeText($0)", requestId));
        copy.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        copy.setAriaLabel("Copy the request ID");
        final HorizontalLayout row = new HorizontalLayout(text, copy);
        row.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        row.setSpacing(true);
        return row;
    }

    static String requestIdText(final String requestId) {
        return "Request ID: " + requestId;
    }

}
