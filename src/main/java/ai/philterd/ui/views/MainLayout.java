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
import ai.philterd.ui.security.Roles;
import ai.philterd.ui.security.Sessions;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import jakarta.annotation.security.RolesAllowed;

/**
 * The frame around every view a signed-in person uses: navigation, who they are, and a way to sign out.
 * Vaadin checks a layout's access rules as well as its views', so it allows only fully signed-in people.
 */
@RolesAllowed(Roles.USER)
public class MainLayout extends AppLayout {

    public MainLayout(final Sessions sessions) {

        final H1 title = new H1("Philter UI");
        title.getStyle().set("font-size", "var(--lumo-font-size-l)").set("margin", "0");

        final Span signedInAs = new Span(Sessions.currentUser()
                .map(user -> "Signed in as " + user.getUsername() + (user.isAdministrator() ? " (administrator)" : ""))
                .orElse(""));

        final Button signOut = new Button("Sign out", e -> sessions.end(Notice.SIGNED_OUT));
        signOut.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        final HorizontalLayout header = new HorizontalLayout(title, signedInAs, signOut);
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.expand(title);
        header.setWidthFull();
        header.getStyle().set("padding", "0 var(--lumo-space-m)");

        addToNavbar(new DrawerToggle(), header);

        final SideNav home = new SideNav();
        home.addItem(new SideNavItem("Home", HomeView.class, VaadinIcon.HOME.create()));

        final SideNav redaction = new SideNav("Redaction");
        redaction.addItem(new SideNavItem("Custom Lists", CustomListsView.class, VaadinIcon.LIST.create()));
        redaction.addItem(new SideNavItem("Always/Never Redact Lists", RedactListsView.class, VaadinIcon.TAGS.create()));
        redaction.addItem(new SideNavItem("Contexts", ContextsView.class, VaadinIcon.RECORDS.create()));
        redaction.addItem(new SideNavItem("Legal Holds", HoldsView.class, VaadinIcon.LOCK.create()));

        addToDrawer(home, redaction);

    }

}
