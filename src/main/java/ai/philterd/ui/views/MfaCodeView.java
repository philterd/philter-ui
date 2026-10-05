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
import ai.philterd.philter.model.exceptions.SignInRateLimitedException;
import ai.philterd.philter.model.exceptions.UnauthorizedException;
import ai.philterd.ui.security.Notice;
import ai.philterd.ui.security.PendingMfa;
import ai.philterd.ui.security.PhilterErrors;
import ai.philterd.ui.security.Roles;
import ai.philterd.ui.security.SignInMessages;
import ai.philterd.ui.security.SignIns;
import ai.philterd.ui.security.Sessions;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.io.IOException;

/** The second step of signing in for a user enrolled in MFA: a code from their authenticator app. */
@Route("sign-in/mfa")
@PageTitle("Verify | Philter UI")
@RolesAllowed(Roles.MFA_PENDING)
public class MfaCodeView extends StepLayout {

    private final transient SignIns signIns;
    private final transient Sessions sessions;
    private final TextField code = new TextField("Authentication code");

    public MfaCodeView(final SignIns signIns, final Sessions sessions) {
        super(sessions, "Verify", "Enter the code from your authenticator app to finish signing in.");
        this.signIns = signIns;
        this.sessions = sessions;

        code.setPlaceholder("123456");
        code.setMaxLength(6);
        code.setAllowedCharPattern("[0-9]");
        code.setAutofocus(true);
        code.addKeyDownListener(Key.ENTER, e -> verify());

        final Button verify = new Button("Verify", e -> verify());
        verify.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        addStep(code, verify);
    }

    private void verify() {

        final PendingMfa pending = Sessions.pendingMfa().orElse(null);
        if (pending == null) {
            sessions.end(Notice.ENDED);
            return;
        }

        try {
            sessions.replace(signIns.completeSignIn(pending, code.getValue()));
            UI.getCurrent().navigate(ContinueView.class);
        } catch (final UnauthorizedException e) {
            // A wrong code uses up the challenge, so the person signs in again with their password.
            sessions.end(Notice.MFA_FAILED);
        } catch (final SignInRateLimitedException e) {
            showError(SignInMessages.RATE_LIMITED);
        } catch (final ClientException e) {
            if (PhilterErrors.hasStatus(e, 403)) {
                sessions.end(Notice.MFA_LOCKED);
            } else {
                showError(SignInMessages.forFailure(e));
            }
        } catch (final IOException | ServiceUnavailableException e) {
            showError(SignInMessages.UNAVAILABLE);
        }

    }

    private void showError(final String message) {
        code.setInvalid(true);
        code.setErrorMessage(message);
    }

}
