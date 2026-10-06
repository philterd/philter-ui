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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Signs a person in by sending their username and password to Philter. Philter is the only user store:
 * Philter UI keeps no passwords, and the password is not stored or logged here. The browser's address goes
 * with them, so Philter rate-limits and audits the sign-in by the person's address rather than Philter UI's.
 */
@Component
public class PhilterAuthenticationProvider implements AuthenticationProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(PhilterAuthenticationProvider.class);

    private final PhilterClients clients;
    private final SignIns signIns;
    private final ClientAddresses addresses;

    public PhilterAuthenticationProvider(final PhilterClients clients, final SignIns signIns,
                                         final ClientAddresses addresses) {
        this.clients = clients;
        this.signIns = signIns;
        this.addresses = addresses;
    }

    @Override
    public Authentication authenticate(final Authentication authentication) throws AuthenticationException {

        final String username = authentication.getName();
        final String password = authentication.getCredentials() == null ? "" : authentication.getCredentials().toString();

        try {
            return signIns.toAuthentication(username,
                    clients.anonymous().signIn(username, password, addresses.current()));
        } catch (final IOException | RuntimeException e) {
            final String message = SignInMessages.forFailure(e);
            if (message.equals(SignInMessages.UNAVAILABLE) || message.startsWith("Sign-in failed")) {
                LOGGER.warn("Sign-in through Philter failed: {}", e.toString());
            }
            throw new SignInFailedException(message, e);
        }

    }

    @Override
    public boolean supports(final Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

}
