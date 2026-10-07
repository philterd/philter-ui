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

import ai.philterd.ui.security.PhilterFailures;
import ai.philterd.ui.security.SignInMessages;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.ErrorParameter;
import com.vaadin.flow.router.HasErrorParameter;

/**
 * Shown in place of a page that failed to open because a request to Philter failed, with Philter's
 * explanation or that Philter could not be reached, rather than Vaadin's generic error page. Failures after
 * a page has opened are handled by {@code SessionErrorHandler}; a rejected session key by
 * {@link SessionEndedView}.
 */
abstract class PhilterFailureView<T extends Exception> extends VerticalLayout implements HasErrorParameter<T> {

    private final int status;

    PhilterFailureView(final int status) {
        this.status = status;
    }

    @Override
    public int setErrorParameter(final BeforeEnterEvent event, final ErrorParameter<T> parameter) {
        final Button retry = new Button("Try again", VaadinIcon.REFRESH.create(),
                e -> UI.getCurrent().getPage().reload());
        retry.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        removeAll();
        add(new H2("This page could not be opened"), new Paragraph(message(parameter.getCaughtException())), retry);
        return status;
    }

    static String message(final Exception exception) {
        final String message = PhilterFailures.messageFor(exception);
        return message == null ? SignInMessages.UNAVAILABLE : message;
    }

}
