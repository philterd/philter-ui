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
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * A bare page for a step of signing in. It has no navigation, since nothing else is reachable until the
 * step is finished, only a way to sign out.
 */
abstract class StepLayout extends VerticalLayout {

    private final Button signOut;

    StepLayout(final Sessions sessions, final String title, final String explanation) {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        signOut = new Button("Sign out", e -> sessions.end(Notice.SIGNED_OUT));
        signOut.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        add(new H2(title), new Paragraph(explanation), new IdleTimer(sessions));
    }

    /** Adds the step's fields and buttons, followed by the sign-out button. */
    void addStep(final Component... components) {
        add(components);
        add(signOut);
    }

}
