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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientAddressesTest {

    private static final ClientAddresses PROXIED = new ClientAddresses("10.0.0.0/8, 192.0.2.10, 2001:db8::/32");

    @Test
    void withNoTrustedProxiesTheConnectionDecides() {
        final ClientAddresses direct = new ClientAddresses("");
        assertEquals("203.0.113.7", direct.resolve("203.0.113.7", List.of("198.51.100.1")));
        assertEquals("10.0.0.2", direct.resolve("10.0.0.2", List.of("198.51.100.1")));
        assertEquals("203.0.113.7", new ClientAddresses(null).resolve("203.0.113.7", List.of()));
    }

    @Test
    void anUntrustedConnectionCannotChooseItsAddress() {
        assertEquals("203.0.113.7", PROXIED.resolve("203.0.113.7", List.of("198.51.100.1")));
    }

    @Test
    void theRightmostUntrustedHopIsTheBrowser() {
        assertEquals("203.0.113.7", PROXIED.resolve("10.0.0.2", List.of("203.0.113.7")));
        // The browser wrote 198.51.100.99 itself; the proxy appended the address it saw.
        assertEquals("203.0.113.7", PROXIED.resolve("10.0.0.2", List.of("198.51.100.99, 203.0.113.7")));
        // Two trusted proxies in a row are skipped.
        assertEquals("203.0.113.7", PROXIED.resolve("10.0.0.2", List.of("203.0.113.7, 192.0.2.10")));
        // Several headers are read as one list, in order.
        assertEquals("203.0.113.7", PROXIED.resolve("10.0.0.2", List.of("198.51.100.99", "203.0.113.7")));
    }

    @Test
    void whenEveryHopIsTrustedTheLeftmostIsUsed() {
        assertEquals("10.1.1.1", PROXIED.resolve("10.0.0.2", List.of("10.1.1.1")));
        assertEquals("10.0.0.2", PROXIED.resolve("10.0.0.2", List.of()));
    }

    @Test
    void anUnreadableHopStopsTheWalk() {
        assertEquals("10.0.0.2", PROXIED.resolve("10.0.0.2", List.of("unknown")));
        assertEquals("10.0.0.2", PROXIED.resolve("10.0.0.2", List.of("203.0.113.7, proxy.example.com")));
        assertEquals("10.0.0.2", PROXIED.resolve("10.0.0.2", List.of("999.1.1.1")));
        assertEquals("10.0.0.2", PROXIED.resolve("10.0.0.2", List.of("fe80::1%eth0")));
    }

    @Test
    void portsAndIpv6AreUnderstood() {
        assertEquals("203.0.113.7", PROXIED.resolve("10.0.0.2", List.of("203.0.113.7:51234")));
        assertTrue(PROXIED.resolve("10.0.0.2", List.of("[2001:db9::7]:443")).startsWith("2001:db9:"));
        // 2001:db8::/32 is trusted, so the walk continues past it.
        assertEquals("203.0.113.7", PROXIED.resolve("2001:db8::5", List.of("203.0.113.7")));
        assertNull(PROXIED.resolve("not-an-address", List.of()));
    }

    @Test
    void textThatIsNotAnAddressIsNeverLookedUp() {
        assertNull(ClientAddresses.literal("localhost"));
        assertNull(ClientAddresses.literal("999.1.1.1"));
        assertNull(ClientAddresses.literal("proxy.example.com"));
        assertNull(ClientAddresses.literal("1:2"));
        assertEquals("203.0.113.7", ClientAddresses.literal("203.0.113.7").getHostAddress());
    }

    @Test
    void aMistypedTrustedProxyStopsStartup() {
        assertThrows(IllegalArgumentException.class, () -> new ClientAddresses("10.0.0.0/8, proxy.example.com"));
        assertThrows(IllegalArgumentException.class, () -> new ClientAddresses("10.0.0.0/33"));
        assertThrows(IllegalArgumentException.class, () -> new ClientAddresses("10.0.0.0/x"));
    }

}
