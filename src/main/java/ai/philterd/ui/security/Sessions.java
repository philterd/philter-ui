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
package ai.philterd.ui.security;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinServletRequest;
import com.vaadin.flow.server.VaadinServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Reads, replaces, and ends the current person's Philter UI session. */
@Component
public class Sessions {

    private final SessionKeyRevoker revoker;

    public Sessions(final SessionKeyRevoker revoker) {
        this.revoker = revoker;
    }

    public static Optional<PhilterUser> currentUser() {
        return principal(PhilterUser.class);
    }

    public static Optional<PendingMfa> pendingMfa() {
        return principal(PendingMfa.class);
    }

    private static <T> Optional<T> principal(final Class<T> type) {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && type.isInstance(authentication.getPrincipal())) {
            return Optional.of(type.cast(authentication.getPrincipal()));
        }
        return Optional.empty();
    }

    /**
     * Replaces the session's authentication after a step of signing in, with a new session ID so the
     * one used before the step cannot be reused.
     */
    public void replace(final Authentication authentication) {
        final HttpServletRequest request = VaadinServletRequest.getCurrent().getHttpServletRequest();
        request.changeSessionId();
        final SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request,
                VaadinServletResponse.getCurrent().getHttpServletResponse());
    }

    /** Revokes the session key, ends the session, and returns the person to the sign-in page. */
    public void end(final Notice notice) {
        currentUser().ifPresent(revoker::revoke);
        final UI ui = UI.getCurrent();
        new SecurityContextLogoutHandler().logout(VaadinServletRequest.getCurrent().getHttpServletRequest(), null, null);
        if (ui != null) {
            ui.getPage().setLocation("login?notice=" + notice.getParameter());
        }
    }

}
