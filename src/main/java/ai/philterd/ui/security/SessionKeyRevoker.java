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

import ai.philterd.philter.model.exceptions.UnauthorizedException;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Revokes a person's session key in Philter when their Philter UI session ends: on sign-out, and when the
 * session times out or is invalidated, so a key does not outlive the session that held it.
 */
@Component
public class SessionKeyRevoker implements HttpSessionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionKeyRevoker.class);

    private final PhilterClients clients;

    public SessionKeyRevoker(final PhilterClients clients) {
        this.clients = clients;
    }

    public void revoke(final PhilterUser user) {
        if (!user.markEnded()) {
            return;
        }
        try {
            clients.forUser(user).signOut();
        } catch (final UnauthorizedException e) {
            // Already expired or revoked, for example by a password change.
        } catch (final IOException | RuntimeException e) {
            LOGGER.warn("Could not revoke the session key for {}: {}", user.getUsername(), e.getMessage());
        }
    }

    @Override
    public void sessionDestroyed(final HttpSessionEvent event) {
        if (event.getSession().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)
                instanceof SecurityContext context
                && context.getAuthentication() != null
                && context.getAuthentication().getPrincipal() instanceof PhilterUser user) {
            revoke(user);
        }
    }

}
