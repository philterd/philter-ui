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
import ai.philterd.philter.model.exceptions.ErrorReasons;
import ai.philterd.philter.model.exceptions.ServiceUnavailableException;
import ai.philterd.philter.model.exceptions.SignInLockedException;
import ai.philterd.ui.security.Notice;
import ai.philterd.ui.security.PhilterClients;
import ai.philterd.ui.security.PhilterUser;
import ai.philterd.ui.security.Roles;
import ai.philterd.ui.security.SignInMessages;
import ai.philterd.ui.security.Sessions;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasValidation;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.io.IOException;
import java.util.List;

/**
 * Shown when Philter requires MFA and the person has not enrolled: the session key can do nothing else
 * until they do. Starting takes the current password again, since Philter UI does not keep the one typed at
 * sign-in. Confirming revokes the key, so the person then signs in again, with a code.
 */
@Route("mfa-enrollment")
@PageTitle("Set Up MFA | Philter UI")
@RolesAllowed(Roles.MFA_ENROLLMENT)
public class MfaEnrollmentView extends StepLayout {

    private final transient PhilterClients clients;
    private final transient Sessions sessions;
    private final PasswordField password = passwordField();
    private final Button start = new Button("Continue", e -> start());
    private final Image qrCode = new Image();
    private final Paragraph secret = new Paragraph();
    private final TextField code = new TextField("Authentication code");
    private final Button confirm = new Button("Confirm", e -> confirm());

    public MfaEnrollmentView(final PhilterClients clients, final Sessions sessions) {
        super(sessions, "Set Up Multi-Factor Authentication",
                "Philter requires multi-factor authentication. Enter your current password, then scan the QR code "
                        + "with your authenticator app, or type in the setup key, and enter the code the app shows.");
        this.clients = clients;
        this.sessions = sessions;

        password.addKeyDownListener(Key.ENTER, e -> start());
        start.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        qrCode.setWidth("200px");
        qrCode.setHeight("200px");
        code.setPlaceholder("123456");
        code.setMaxLength(6);
        code.setAllowedCharPattern("[0-9]");
        code.addKeyDownListener(Key.ENTER, e -> confirm());
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        for (final Component enrolling : List.of(qrCode, secret, code, confirm)) {
            enrolling.setVisible(false);
        }

        addStep(password, start, qrCode, secret, code, confirm);
    }

    private void start() {
        final PhilterUser user = Sessions.currentUser().orElse(null);
        if (user == null) {
            sessions.end(Notice.ENDED);
            return;
        }
        final MfaEnrollment enrollment = startWith(password, clients.forUser(user)::startMfaEnrollment);
        if (enrollment == null) {
            return;
        }
        qrCode.setSrc(QrCodes.svgDataUri(enrollment.getOtpauthUri()));
        qrCode.setAlt("QR code for your authenticator app");
        secret.setText("Setup key: " + grouped(enrollment.getSecret()));
        password.setVisible(false);
        start.setVisible(false);
        for (final Component enrolling : List.of(qrCode, secret, code, confirm)) {
            enrolling.setVisible(true);
        }
        code.focus();
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
            if (endsEnrollment(e)) {
                // Enrollment was completed, or MFA turned off, by another request meanwhile.
                sessions.end(Notice.ENDED);
            } else {
                invalid(code, confirmFailure(e));
            }
        } catch (final IOException | ServiceUnavailableException e) {
            invalid(code, SignInMessages.UNAVAILABLE);
        }
    }

    private static void invalid(final HasValidation field, final String message) {
        field.setErrorMessage(message);
        field.setInvalid(true);
    }

    /** The current password asked for before enrollment starts. Its value is cleared when it is sent to Philter. */
    static PasswordField passwordField() {
        final PasswordField password = new PasswordField("Current password");
        password.setHelperText("A wrong password counts toward locking your sign-in.");
        password.setWidthFull();
        return password;
    }

    /** Starts enrollment, as {@code PhilterClient.startMfaEnrollment(String)} does. */
    @FunctionalInterface
    interface EnrollmentStart {
        MfaEnrollment start(String password) throws IOException;
    }

    /**
     * Starts enrollment with the password typed into {@code password}, clearing the field so Philter UI does not
     * keep it. Returns {@code null} when it did not start, with the reason shown on the field.
     */
    static MfaEnrollment startWith(final PasswordField password, final EnrollmentStart start) {
        if (password.isEmpty()) {
            invalid(password, ENTER_PASSWORD);
            return null;
        }
        // Cleared before the call: clearing re-validates the field, which would hide the error shown for a refusal.
        final String current = password.getValue();
        password.clear();
        try {
            return start.start(current);
        } catch (final ClientException e) {
            invalid(password, startFailure(e));
        } catch (final IOException | ServiceUnavailableException e) {
            invalid(password, SignInMessages.UNAVAILABLE);
        }
        return null;
    }

    /** The secret in groups of four, which is easier to type. */
    static String grouped(final String secret) {
        return secret.replaceAll("(.{4})(?!$)", "$1 ");
    }

    static final String INVALID_CODE = "That code is not valid. Check your authenticator app and try again.";
    static final String ENTER_PASSWORD = "Enter your current password.";
    static final String NO_PASSWORD = "You do not have a password, and multi-factor authentication protects signing in "
            + "with one. An administrator must set your password first.";

    /** Why Philter would not start enrollment. */
    static String startFailure(final ClientException e) {
        if (e instanceof SignInLockedException locked) {
            return "Sign-in is locked after too many incorrect passwords. Try again in "
                    + SignInMessages.retryIn(locked.getRetryAfterSeconds()) + ".";
        }
        if (ErrorReasons.WRONG_PASSWORD.equals(e.getReason())) {
            return ChangePasswordView.WRONG_CURRENT_PASSWORD;
        }
        if ("password".equals(e.getField())) {
            return ENTER_PASSWORD;
        }
        if (ErrorReasons.MFA_UNAVAILABLE.equals(e.getReason())) {
            return "Multi-factor authentication is not available. An administrator can make it available.";
        }
        if (ErrorReasons.MFA_ALREADY_ENROLLED.equals(e.getReason())) {
            return "You have already set up multi-factor authentication.";
        }
        // Philter gives no reason for the remaining conflict: the user has no password.
        if (e.getStatusCode() == 409 && (e.getReason() == null || ErrorReasons.CONFLICT.equals(e.getReason()))) {
            return NO_PASSWORD;
        }
        return e.getErrorMessage() == null ? "Multi-factor authentication could not be started." : e.getErrorMessage();
    }

    /** Whether a refused confirmation means enrollment ended elsewhere: completed, or MFA turned off. */
    static boolean endsEnrollment(final ClientException e) {
        return ErrorReasons.MFA_ALREADY_ENROLLED.equals(e.getReason()) || ErrorReasons.MFA_UNAVAILABLE.equals(e.getReason());
    }

    /** Why Philter would not confirm enrollment with a code. */
    static String confirmFailure(final ClientException e) {
        if (ErrorReasons.INVALID_CODE.equals(e.getReason()) || "code".equals(e.getField())) {
            return INVALID_CODE;
        }
        if (ErrorReasons.MFA_LOCKED.equals(e.getReason())) {
            return SignInMessages.MFA_LOCKED;
        }
        return e.getErrorMessage() == null ? "Philter could not complete enrollment. Try again later." : e.getErrorMessage();
    }

}
