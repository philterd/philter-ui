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
import ai.philterd.ui.security.SignInFailedException;
import ai.philterd.ui.security.SignInMessages;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinServletRequest;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.web.WebAttributes;

import java.util.List;

/**
 * The sign-in page. The form posts to Spring Security's {@code /login}, which sends the username and
 * password to Philter; Philter UI does not store either.
 */
@Route("login")
@PageTitle("Sign In | Philter UI")
@AnonymousAllowed
public class SignInView extends VerticalLayout implements BeforeEnterObserver {

    private final LoginForm form = new LoginForm();
    private final Paragraph notice = new Paragraph();

    public SignInView() {
        addClassName("login-view");
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        form.setAction("login");
        form.setForgotPasswordButtonVisible(false);
        notice.setVisible(false);

        add(new H1("Philter UI"), notice, form);
    }

    @Override
    public void beforeEnter(final BeforeEnterEvent event) {

        final QueryParameters parameters = event.getLocation().getQueryParameters();

        if (parameters.getParameters().containsKey("error")) {
            final LoginI18n i18n = LoginI18n.createDefault();
            i18n.getErrorMessage().setTitle("Sign-in failed");
            i18n.getErrorMessage().setMessage(lastFailure());
            form.setI18n(i18n);
            form.setError(true);
        }

        parameters.getParameters().getOrDefault("notice", List.of()).stream().findFirst()
                .flatMap(Notice::fromParameter)
                .ifPresent(n -> {
                    notice.setText(n.getMessage());
                    notice.setVisible(true);
                });

    }

    /** The message for the failure Spring Security saved in the session, read once. */
    private static String lastFailure() {
        final HttpSession session = VaadinServletRequest.getCurrent().getHttpServletRequest().getSession(false);
        if (session == null) {
            return SignInMessages.INVALID;
        }
        final Object failure = session.getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
        session.removeAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
        return failure instanceof SignInFailedException signIn ? signIn.getMessage() : SignInMessages.INVALID;
    }

}
