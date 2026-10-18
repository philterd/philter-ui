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

import ai.philterd.ui.model.PhilterRules;
import ai.philterd.ui.security.Notice;
import ai.philterd.ui.security.PhilterClients;
import ai.philterd.ui.security.PhilterUser;
import ai.philterd.ui.security.Roles;
import ai.philterd.ui.security.Sessions;
import ai.philterd.ui.security.SignIns;
import com.vaadin.flow.component.UI;
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
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import jakarta.annotation.security.RolesAllowed;

/**
 * The frame around every view a signed-in person uses: navigation, who they are, and a way to sign out.
 * Vaadin checks a layout's access rules as well as its views', so it allows only fully signed-in people. On each
 * navigation it checks the person's role with Philter, so a role an administrator changed takes effect without
 * signing in again.
 */
@RolesAllowed(Roles.USER)
public class MainLayout extends AppLayout implements BeforeEnterObserver {

    private final transient Sessions sessions;
    private final transient PhilterClients clients;

    public MainLayout(final Sessions sessions, final PhilterClients clients) {

        this.sessions = sessions;
        this.clients = clients;

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

        addToNavbar(new DrawerToggle(), header, new IdleTimer(sessions));

        final SideNav home = new SideNav();
        home.addItem(new SideNavItem("Dashboard", DashboardView.class, VaadinIcon.DASHBOARD.create()));

        final SideNav redaction = new SideNav("Redaction");
        redaction.addItem(new SideNavItem("Redaction Policies", PoliciesView.class, VaadinIcon.FILE_TEXT.create()));
        redaction.addItem(new SideNavItem("Custom Lists", CustomListsView.class, VaadinIcon.LIST.create()));
        redaction.addItem(new SideNavItem("Redact Lists", RedactListsView.class, VaadinIcon.TAGS.create()));
        redaction.addItem(new SideNavItem("Contexts", ContextsView.class, VaadinIcon.RECORDS.create()));
        redaction.addItem(new SideNavItem("Models", ModelsView.class, VaadinIcon.CLUSTER.create()));
        redaction.addItem(new SideNavItem("Redaction Ledgers", LedgerView.class, VaadinIcon.BOOK.create()));
        redaction.addItem(new SideNavItem("Legal Holds", HoldsView.class, VaadinIcon.LOCK.create()));

        final SideNav account = new SideNav("Account");
        account.addItem(new SideNavItem("My Account", AccountView.class, VaadinIcon.USER.create()));

        addToDrawer(home, redaction, account);

        if (Sessions.currentUser().map(PhilterUser::isAdministrator).orElse(false)) {
            final SideNav administration = new SideNav("Administration");
            administration.addItem(new SideNavItem("Admin", AdminView.class, VaadinIcon.USER_STAR.create()));
            addToDrawer(administration);
        }

    }

    @Override
    public void beforeEnter(final BeforeEnterEvent event) {
        Sessions.currentUser().ifPresent(user -> {
            final Boolean administrator = administratorChange(user, ViewSupport.rules(clients.forUser(user)));
            if (administrator == null) {
                return;
            }
            sessions.changeAdministrator(administrator);
            // Reload so the navigation and every view's access check use the new role. A person who is no
            // longer an administrator goes to the dashboard, since the page they were opening may be closed to them.
            if (administrator) {
                UI.getCurrent().getPage().reload();
            } else {
                UI.getCurrent().getPage().setLocation(".");
            }
        });
    }

    /**
     * Whether the person should now be an administrator, when Philter's role for them differs from the one they
     * signed in with, or {@code null} when it is the same or Philter could not say.
     */
    static Boolean administratorChange(final PhilterUser user, final PhilterRules rules) {
        final String role = rules.callerRole();
        if (role == null || SignIns.isAdministrator(role) == user.isAdministrator()) {
            return null;
        }
        return SignIns.isAdministrator(role);
    }

}
