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

import com.vaadin.flow.server.VaadinServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The address of the person's browser, which Philter UI passes to Philter with each request so that Philter
 * rate-limits sign-ins and audits each person by their own address rather than all of them by Philter UI's.
 *
 * <p>It is the address of the connection to Philter UI, unless that connection comes from a proxy listed in
 * {@code TRUSTED_PROXIES}. Then {@code X-Forwarded-For} is read from the right, skipping trusted proxies,
 * since each proxy appends the address it received from and a browser can write the leftmost entries
 * itself. Only IP addresses are used; nothing is looked up in DNS.
 */
@Component
public class ClientAddresses {

    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    private static final Pattern IPV6 = Pattern.compile("[0-9A-Fa-f:.]*:[0-9A-Fa-f:.]*");

    private final List<Range> trusted = new ArrayList<>();

    /**
     * @param trustedProxies Comma-separated IP addresses and CIDR ranges of the proxies in front of Philter UI.
     * @throws IllegalArgumentException If an entry is not an IP address or CIDR range, so a mistyped setting
     *                                  stops Philter UI from starting rather than being ignored.
     */
    public ClientAddresses(@Value("${ui.trusted-proxies:}") final String trustedProxies) {
        if (trustedProxies == null) {
            return;
        }
        for (final String entry : trustedProxies.split(",")) {
            if (!entry.isBlank()) {
                trusted.add(Range.parse(entry.trim()));
            }
        }
    }

    /** The address of the browser making the current request, or {@code null} outside a request. */
    public String current() {
        final HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        return resolve(request.getRemoteAddr(), Collections.list(request.getHeaders("X-Forwarded-For")));
    }

    /**
     * The browser's address, from the connection's address and the {@code X-Forwarded-For} headers.
     *
     * @return The address, or {@code null} if the connection's address is not an IP address.
     */
    String resolve(final String remoteAddress, final List<String> forwardedFor) {

        InetAddress client = literal(remoteAddress);
        if (client == null) {
            return null;
        }

        final List<String> hops = new ArrayList<>();
        for (final String header : forwardedFor) {
            for (final String hop : header.split(",")) {
                if (!hop.isBlank()) {
                    hops.add(hop.trim());
                }
            }
        }

        for (int i = hops.size() - 1; i >= 0 && isTrusted(client); i--) {
            final InetAddress hop = literal(withoutPort(hops.get(i)));
            if (hop == null) {
                break;
            }
            client = hop;
        }

        return client.getHostAddress();

    }

    private boolean isTrusted(final InetAddress address) {
        for (final Range range : trusted) {
            if (range.contains(address)) {
                return true;
            }
        }
        return false;
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        final VaadinServletRequest vaadin = VaadinServletRequest.getCurrent();
        return vaadin == null ? null : vaadin.getHttpServletRequest();
    }

    /** An entry without its port: {@code 203.0.113.7:443} and {@code [2001:db8::7]:443} are allowed. */
    static String withoutPort(final String hop) {
        if (hop.startsWith("[")) {
            final int end = hop.indexOf(']');
            return end < 0 ? hop : hop.substring(1, end);
        }
        final int colon = hop.indexOf(':');
        return colon > 0 && colon == hop.lastIndexOf(':') ? hop.substring(0, colon) : hop;
    }

    /** The address if the text is an IPv4 or IPv6 literal, or {@code null}; never a DNS lookup. */
    static InetAddress literal(final String text) {
        if (text == null) {
            return null;
        }
        try {
            if (IPV4.matcher(text).matches()) {
                // Built from the octets: InetAddress.getByName looks up text like 999.1.1.1 in DNS.
                final String[] parts = text.split("\\.");
                final byte[] bytes = new byte[4];
                for (int i = 0; i < 4; i++) {
                    final int octet = Integer.parseInt(parts[i]);
                    if (octet > 255) {
                        return null;
                    }
                    bytes[i] = (byte) octet;
                }
                return InetAddress.getByAddress(bytes);
            }
            // Text with a colon is only ever parsed as an IPv6 literal, never looked up.
            return IPV6.matcher(text).matches() ? InetAddress.getByName(text) : null;
        } catch (final UnknownHostException e) {
            return null;
        }
    }

    /** An IP address or a CIDR range. */
    private record Range(byte[] network, int prefix) {

        static Range parse(final String entry) {
            final int slash = entry.indexOf('/');
            final InetAddress address = literal(slash < 0 ? entry : entry.substring(0, slash));
            if (address == null) {
                throw new IllegalArgumentException("TRUSTED_PROXIES entry '" + entry
                        + "' is not an IP address or CIDR range.");
            }
            final int bits = address.getAddress().length * 8;
            int prefix = bits;
            if (slash >= 0) {
                try {
                    prefix = Integer.parseInt(entry.substring(slash + 1));
                } catch (final NumberFormatException e) {
                    prefix = -1;
                }
                if (prefix < 0 || prefix > bits) {
                    throw new IllegalArgumentException("TRUSTED_PROXIES entry '" + entry
                            + "' has a prefix length outside 0 to " + bits + ".");
                }
            }
            return new Range(address.getAddress(), prefix);
        }

        boolean contains(final InetAddress address) {
            final byte[] bytes = address.getAddress();
            if (bytes.length != network.length) {
                return false;
            }
            for (int bit = 0; bit < prefix; bit++) {
                final int mask = 0x80 >> (bit % 8);
                if ((bytes[bit / 8] & mask) != (network[bit / 8] & mask)) {
                    return false;
                }
            }
            return true;
        }

    }

}
