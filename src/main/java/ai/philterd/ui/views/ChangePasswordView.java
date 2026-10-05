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
import ai.philterd.ui.security.Notice;
import ai.philterd.ui.security.PhilterClients;
import ai.philterd.ui.security.PhilterErrors;
import ai.philterd.ui.security.PhilterUser;
import ai.philterd.ui.security.Roles;
import ai.philterd.ui.security.SignInMessages;
import ai.philterd.ui.security.Sessions;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.io.IOException;

/**
 * Shown when an administrator set the person's password: the session key can do nothing else until the
 * password is changed. Changing it revokes the key, so the person then signs in again.
 */
@Route("change-password")
@PageTitle("Change Password | Philter UI")
@RolesAllowed(Roles.PASSWORD_CHANGE)
public class ChangePasswordView extends StepLayout {

    private final transient PhilterClients clients;
    private final transient Sessions sessions;
    private final PasswordField current = new PasswordField("Current password");
    private final PasswordField password = new PasswordField("New password");
    private final PasswordField confirm = new PasswordField("Confirm new password");

    public ChangePasswordView(final PhilterClients clients, final Sessions sessions) {
        super(sessions, "Set a New Password",
                "An administrator set your password. Choose a new one before continuing.");
        this.clients = clients;
        this.sessions = sessions;

        current.setHelperText("The password you just signed in with.");
        password.setHelperText(Passwords.RULES);
        for (final PasswordField field : new PasswordField[] {current, password, confirm}) {
            field.setWidth("320px");
            field.setRequired(true);
        }

        final Button submit = new Button("Change password", e -> submit());
        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        addStep(current, password, confirm, submit);
    }

    private void submit() {

        current.setInvalid(false);
        password.setInvalid(false);
        confirm.setInvalid(false);

        final String problem = Passwords.problem(password.getValue());
        if (problem != null) {
            invalid(password, problem);
            return;
        }
        if (!password.getValue().equals(confirm.getValue())) {
            invalid(confirm, "The passwords do not match.");
            return;
        }
        if (password.getValue().equals(current.getValue())) {
            invalid(password, "The new password must be different from the current one.");
            return;
        }

        final PhilterUser user = Sessions.currentUser().orElse(null);
        if (user == null) {
            sessions.end(Notice.ENDED);
            return;
        }

        try {
            clients.forUser(user).changePassword(current.getValue(), password.getValue());
            // Philter revoked the session key; the person signs in again with the new password.
            sessions.end(Notice.PASSWORD_CHANGED);
        } catch (final ClientException e) {
            if (PhilterErrors.hasStatus(e, 403)) {
                invalid(current, "The current password is not correct.");
            } else if (PhilterErrors.hasStatus(e, 400)) {
                final String message = PhilterErrors.message(e);
                invalid(password, message == null ? "Philter did not accept the new password." : message);
            } else if (PhilterErrors.hasStatus(e, 409)) {
                // The password was changed by another request meanwhile.
                sessions.end(Notice.ENDED);
            } else {
                invalid(password, "Philter could not change the password. Try again later.");
            }
        } catch (final IOException | ServiceUnavailableException e) {
            invalid(password, SignInMessages.UNAVAILABLE);
        }

    }

    private static void invalid(final PasswordField field, final String message) {
        field.setErrorMessage(message);
        field.setInvalid(true);
    }

}
