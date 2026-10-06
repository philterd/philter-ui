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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhilterAuthenticationProviderTest {

    private static final String PASSWORD = "correct-horse-battery-staple";

    private FakePhilter philter;
    private PhilterAuthenticationProvider provider;
    private SignIns signIns;

    @BeforeEach
    void start() throws Exception {
        philter = new FakePhilter();
        final PhilterClients clients = new PhilterClients(philter.url());
        final ClientAddresses addresses = new ClientAddresses("10.0.0.0/8");
        signIns = new SignIns(clients, new SessionKeyRevoker(clients), addresses);
        provider = new PhilterAuthenticationProvider(clients, signIns, addresses);
    }

    @AfterEach
    void stop() {
        RequestContextHolder.resetRequestAttributes();
        philter.close();
    }

    @Test
    void theBrowsersAddressGoesToPhilter() throws Exception {
        // Through a trusted reverse proxy at 10.0.0.2, from a browser at 203.0.113.7.
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.2");
        request.addHeader("X-Forwarded-For", "198.51.100.99, 203.0.113.7");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        signIn("jordan");
        signIns.completeSignIn(new PendingMfa("mfa", "challenge-1"), "123456");

        assertEquals(java.util.List.of("203.0.113.7", "203.0.113.7"), philter.forwardedFor);
    }

    @Test
    void outsideARequestNoAddressIsSent() {
        signIn("jordan");
        assertEquals(java.util.List.of("none"), philter.forwardedFor);
    }

    @Test
    void signsInAUser() {
        final Authentication authentication = signIn("jordan");
        assertEquals(Set.of("ROLE_USER"), roles(authentication));
        final PhilterUser user = assertInstanceOf(PhilterUser.class, authentication.getPrincipal());
        assertEquals("jordan", user.getUsername());
        assertFalse(user.isAdministrator());
        assertEquals("sk_jordan", user.sessionKey());
        assertEquals("id-jordan", user.getSessionKeyId());
        assertNull(authentication.getCredentials());
    }

    @Test
    void signsInAnAdministratorWithBothRoles() {
        final Authentication authentication = signIn("admin");
        assertEquals(Set.of("ROLE_USER", "ROLE_ADMIN"), roles(authentication));
        assertTrue(((PhilterUser) authentication.getPrincipal()).isAdministrator());
    }

    @Test
    void anMfaChallengeHoldsOnlyThePendingRole() {
        final Authentication authentication = signIn("mfa");
        assertEquals(Set.of("ROLE_MFA_PENDING"), roles(authentication));
        final PendingMfa pending = assertInstanceOf(PendingMfa.class, authentication.getPrincipal());
        assertEquals("mfa", pending.getUsername());
        assertEquals("challenge-1", pending.challenge());
    }

    @Test
    void aCorrectCodeCompletesSignIn() throws Exception {
        final Authentication authentication = signIns.completeSignIn(new PendingMfa("mfa", "challenge-1"), "123456");
        assertEquals(Set.of("ROLE_USER"), roles(authentication));
        assertEquals("sk_mfa", ((PhilterUser) authentication.getPrincipal()).sessionKey());
        assertEquals("id-mfa", ((PhilterUser) authentication.getPrincipal()).getSessionKeyId());
    }

    @Test
    void aPasswordSetByAnAdministratorRestrictsTheSession() {
        final Authentication authentication = signIn("newpassword");
        assertEquals(Set.of("ROLE_PASSWORD_CHANGE"), roles(authentication));
        assertEquals(PhilterUser.Restriction.PASSWORD_CHANGE, ((PhilterUser) authentication.getPrincipal()).getRestriction());
        assertEquals("id-newpassword", ((PhilterUser) authentication.getPrincipal()).getSessionKeyId());
    }

    @Test
    void requiredEnrollmentRestrictsTheSession() {
        final Authentication authentication = signIn("enroll");
        assertEquals(Set.of("ROLE_MFA_ENROLLMENT"), roles(authentication));
    }

    @Test
    void refusalsAreExplainedWithoutSayingWhichUsernamesExist() {
        assertEquals(SignInMessages.INVALID, failure("nobody"));
        assertEquals("Too many failed sign-ins for this username. Try again in 15 minutes.", failure("locked"));
        assertEquals(SignInMessages.RATE_LIMITED, failure("busy"));
        assertEquals(SignInMessages.MFA_LOCKED, failure("mfalocked"));
        assertEquals(SignInMessages.DISABLED, failure("disabled"));
    }

    @Test
    void philterBeingUnreachableIsReported() {
        final PhilterClients clients = new PhilterClients("http://127.0.0.1:1");
        final ClientAddresses addresses = new ClientAddresses("");
        final PhilterAuthenticationProvider unreachable = new PhilterAuthenticationProvider(clients,
                new SignIns(clients, new SessionKeyRevoker(clients), addresses), addresses);
        final SignInFailedException failure = assertThrows(SignInFailedException.class,
                () -> unreachable.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("jordan", PASSWORD)));
        assertEquals(SignInMessages.UNAVAILABLE, failure.getMessage());
    }

    @Test
    void aKeyWhoseRoleCannotBeReadIsRevoked() {
        failure("broken");
        assertEquals(java.util.List.of("sk_broken"), philter.revokedKeys);
    }

    private Authentication signIn(final String username) {
        return provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(username, PASSWORD));
    }

    private String failure(final String username) {
        final SignInFailedException failure = assertThrows(SignInFailedException.class, () -> signIn(username));
        assertFalse(failure.getMessage().contains(PASSWORD));
        return failure.getMessage();
    }

    private static Set<String> roles(final Authentication authentication) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

}
