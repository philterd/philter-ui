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

import ai.philterd.philter.model.SignInResponse;
import ai.philterd.philter.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/** Turns Philter's answer to a sign-in into the Spring Security authentication for the session. */
@Component
public class SignIns {

    private static final Logger LOGGER = LoggerFactory.getLogger(SignIns.class);

    private final PhilterClients clients;
    private final SessionKeyRevoker revoker;

    public SignIns(final PhilterClients clients, final SessionKeyRevoker revoker) {
        this.clients = clients;
        this.revoker = revoker;
    }

    /** Completes a sign-in for a user enrolled in MFA, with a code from their authenticator app. */
    public Authentication completeSignIn(final PendingMfa pending, final String code) throws IOException {
        return toAuthentication(pending.getUsername(), clients.anonymous().completeSignIn(pending.challenge(), code));
    }

    /**
     * @param username The username the person typed, used when Philter's answer is an MFA challenge,
     *                 which does not name the user.
     */
    public Authentication toAuthentication(final String username, final SignInResponse response) throws IOException {

        if (response.isMfaRequired()) {
            return token(new PendingMfa(username, response.getChallenge()), Roles.MFA_PENDING);
        }

        if (response.isPasswordChangeRequired()) {
            return token(new PhilterUser(response.getUsername(), false, PhilterUser.Restriction.PASSWORD_CHANGE,
                    response.getApiKey()), Roles.PASSWORD_CHANGE);
        }

        if (response.isMfaEnrollmentRequired()) {
            return token(new PhilterUser(response.getUsername(), false, PhilterUser.Restriction.MFA_ENROLLMENT,
                    response.getApiKey()), Roles.MFA_ENROLLMENT);
        }

        // The session key holds every scope; the user's Philter role decides administrator access.
        final User user;
        try {
            user = clients.forSessionKey(response.getApiKey()).getCurrentUser();
        } catch (final IOException | RuntimeException e) {
            LOGGER.warn("Signed in {} but could not read their role: {}", response.getUsername(), e.getMessage());
            revoker.revoke(new PhilterUser(response.getUsername(), false, PhilterUser.Restriction.NONE,
                    response.getApiKey()));
            throw e;
        }

        final boolean administrator = "admin".equals(user.getRole());
        final PhilterUser principal = new PhilterUser(user.getUsername(), administrator,
                PhilterUser.Restriction.NONE, response.getApiKey());

        return administrator ? token(principal, Roles.USER, Roles.ADMIN) : token(principal, Roles.USER);

    }

    private static Authentication token(final Object principal, final String... roles) {
        final List<SimpleGrantedAuthority> authorities = Arrays.stream(roles)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
    }

}
