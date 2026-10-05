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

import ai.philterd.philter.model.User;
import ai.philterd.ui.security.PhilterClients;
import ai.philterd.ui.security.Roles;
import ai.philterd.ui.security.Sessions;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.io.IOException;

/** Shown until the dashboard's views are ported to Philter's REST API. */
@Route(value = "", layout = MainLayout.class)
@PageTitle("Philter UI")
@RolesAllowed(Roles.USER)
public class HomeView extends VerticalLayout {

    /**
     * Reads the person's user from Philter, so a session key that has expired or been revoked is noticed
     * on opening the page and the session ends.
     */
    public HomeView(final PhilterClients clients) throws IOException {
        final User user = clients.forUser(Sessions.currentUser().orElseThrow()).getCurrentUser();
        add(new H2("Philter UI"),
                new Paragraph("Signed in to Philter as " + user.getUsername() + ", with the " + user.getRole() + " role."),
                new Paragraph("The Philter dashboard is being ported to Philter's REST API. "
                        + "Until it is, administer Philter through its API."));
    }

}
