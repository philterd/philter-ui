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

import ai.philterd.ui.security.Roles;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;

/**
 * Where a person lands after each step of signing in. Sends them to the next step they must finish, or
 * to the home page when there is none.
 */
@Route("continue")
@PermitAll
public class ContinueView extends Div implements BeforeEnterObserver {

    private final transient AuthenticationContext authenticationContext;

    public ContinueView(final AuthenticationContext authenticationContext) {
        this.authenticationContext = authenticationContext;
    }

    @Override
    public void beforeEnter(final BeforeEnterEvent event) {
        if (authenticationContext.hasRole(Roles.MFA_PENDING)) {
            event.forwardTo(MfaCodeView.class);
        } else if (authenticationContext.hasRole(Roles.PASSWORD_CHANGE)) {
            event.forwardTo(ChangePasswordView.class);
        } else if (authenticationContext.hasRole(Roles.MFA_ENROLLMENT)) {
            event.forwardTo(MfaEnrollmentView.class);
        } else {
            event.forwardTo(HomeView.class);
        }
    }

}
