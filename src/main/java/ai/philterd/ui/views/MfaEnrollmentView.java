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

import ai.philterd.philter.model.MfaEnrollment;
import ai.philterd.philter.model.exceptions.ClientException;
import ai.philterd.philter.model.exceptions.ServiceUnavailableException;
import ai.philterd.ui.security.Notice;
import ai.philterd.ui.security.PhilterClients;
import ai.philterd.ui.security.PhilterUser;
import ai.philterd.ui.security.Roles;
import ai.philterd.ui.security.SignInMessages;
import ai.philterd.ui.security.Sessions;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.io.IOException;

/**
 * Shown when Philter requires MFA and the person has not enrolled: the session key can do nothing else
 * until they do. Confirming revokes the key, so the person then signs in again, with a code.
 */
@Route("mfa-enrollment")
@PageTitle("Set Up MFA | Philter UI")
@RolesAllowed(Roles.MFA_ENROLLMENT)
public class MfaEnrollmentView extends StepLayout {

    private final transient PhilterClients clients;
    private final transient Sessions sessions;
    private final Image qrCode = new Image();
    private final Paragraph secret = new Paragraph();
    private final TextField code = new TextField("Authentication code");
    private final Button confirm = new Button("Confirm", e -> confirm());
    private boolean started;

    public MfaEnrollmentView(final PhilterClients clients, final Sessions sessions) {
        super(sessions, "Set Up Multi-Factor Authentication",
                "Philter requires multi-factor authentication. Scan the QR code with your authenticator app, "
                        + "or type in the setup key, then enter the code the app shows.");
        this.clients = clients;
        this.sessions = sessions;

        qrCode.setWidth("200px");
        qrCode.setHeight("200px");
        code.setPlaceholder("123456");
        code.setMaxLength(6);
        code.setAllowedCharPattern("[0-9]");
        code.addKeyDownListener(Key.ENTER, e -> confirm());
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        addStep(qrCode, secret, code, confirm);
    }

    @Override
    protected void onAttach(final AttachEvent event) {
        super.onAttach(event);
        // Starting again replaces an unconfirmed secret, so start once per page rather than on every attach.
        if (!started) {
            started = true;
            start();
        }
    }

    private void start() {
        final PhilterUser user = Sessions.currentUser().orElse(null);
        if (user == null) {
            sessions.end(Notice.ENDED);
            return;
        }
        try {
            final MfaEnrollment enrollment = clients.forUser(user).startMfaEnrollment();
            qrCode.setSrc(QrCodes.svgDataUri(enrollment.getOtpauthUri()));
            qrCode.setAlt("QR code for your authenticator app");
            secret.setText("Setup key: " + grouped(enrollment.getSecret()));
        } catch (final ClientException e) {
            showProblem(e.getStatusCode() == 409
                    ? "Multi-factor authentication could not be started. Ask an administrator whether it is available."
                    : "Multi-factor authentication could not be started.");
        } catch (final IOException | ServiceUnavailableException e) {
            showProblem(SignInMessages.UNAVAILABLE);
        }
    }

    private void confirm() {
        final PhilterUser user = Sessions.currentUser().orElse(null);
        if (user == null) {
            sessions.end(Notice.ENDED);
            return;
        }
        try {
            clients.forUser(user).confirmMfaEnrollment(code.getValue());
            // Philter revoked the session key; the person signs in again, this time with a code.
            sessions.end(Notice.MFA_ENROLLED);
        } catch (final ClientException e) {
            if (e.getStatusCode() == 400) {
                code.setErrorMessage("That code is not valid. Check your authenticator app and try again.");
                code.setInvalid(true);
            } else if (e.getStatusCode() == 409) {
                // Enrollment was completed, or MFA turned off, by another request meanwhile.
                sessions.end(Notice.ENDED);
            } else {
                code.setErrorMessage("Philter could not complete enrollment. Try again later.");
                code.setInvalid(true);
            }
        } catch (final IOException | ServiceUnavailableException e) {
            code.setErrorMessage(SignInMessages.UNAVAILABLE);
            code.setInvalid(true);
        }
    }

    private void showProblem(final String message) {
        secret.setText(message);
        qrCode.setVisible(false);
        code.setEnabled(false);
        confirm.setEnabled(false);
    }

    /** The secret in groups of four, which is easier to type. */
    private static String grouped(final String secret) {
        return secret.replaceAll("(.{4})(?!$)", "$1 ");
    }

}
