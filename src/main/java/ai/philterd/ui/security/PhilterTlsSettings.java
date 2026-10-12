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

import ai.philterd.philter.PhilterTls;

import javax.net.ssl.SSLContext;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * The TLS context for connecting to Philter, from the client certificate ({@code PHILTER_CLIENT_CERTIFICATE}) and
 * trust store ({@code PHILTER_TRUST_STORE}) settings. Each keystore is read and checked on its own first, so a
 * refusal names the setting and the file. No message contains a password.
 */
final class PhilterTlsSettings {

    static final String CLIENT_CERTIFICATE = "PHILTER_CLIENT_CERTIFICATE";
    static final String CLIENT_CERTIFICATE_PASSWORD = "PHILTER_CLIENT_CERTIFICATE_PASSWORD";
    static final String TRUST_STORE = "PHILTER_TRUST_STORE";
    static final String TRUST_STORE_PASSWORD = "PHILTER_TRUST_STORE_PASSWORD";

    private PhilterTlsSettings() {
    }

    /**
     * The TLS context presenting the client certificate and trusting the trust store, whichever are set, or
     * {@code null} when neither is, so the JDK's defaults apply as they would without these settings.
     *
     * @throws IllegalStateException Thrown if a keystore is missing, unreadable, has the wrong password, or holds
     * nothing usable, or if the client certificate is set without its password.
     */
    static SSLContext sslContext(final String clientCertificate, final String clientCertificatePassword,
                                 final String trustStore, final String trustStorePassword) {

        final Path keyStore = path(clientCertificate);
        final Path trusted = path(trustStore);

        if (keyStore == null && trusted == null) {
            return null;
        }

        final char[] keyStorePassword = password(clientCertificatePassword);
        final char[] trustedPassword = password(trustStorePassword);

        if (keyStore != null && keyStorePassword == null) {
            throw new IllegalStateException(CLIENT_CERTIFICATE + " is set to " + keyStore + " but "
                    + CLIENT_CERTIFICATE_PASSWORD + " is not.");
        }

        final SSLContext clientCertificateOnly = keyStore == null ? null
                : check(CLIENT_CERTIFICATE, () -> PhilterTls.sslContext(keyStore, keyStorePassword, null, null));
        final SSLContext trustStoreOnly = trusted == null ? null
                : check(TRUST_STORE, () -> PhilterTls.sslContext(null, null, trusted, trustedPassword),
                        // Without its password, a PKCS12 trust store's certificates cannot be read, so it reads as empty.
                        trustedPassword == null ? " If the trust store has a password, set " + TRUST_STORE_PASSWORD + "."
                                : "");

        if (trusted == null) {
            return clientCertificateOnly;
        }
        if (keyStore == null) {
            return trustStoreOnly;
        }
        return check(CLIENT_CERTIFICATE + " and " + TRUST_STORE,
                () -> PhilterTls.sslContext(keyStore, keyStorePassword, trusted, trustedPassword));

    }

    private static SSLContext check(final String setting, final Supplier<SSLContext> context) {
        return check(setting, context, "");
    }

    private static SSLContext check(final String setting, final Supplier<SSLContext> context, final String hint) {
        try {
            return context.get();
        } catch (final IllegalArgumentException ex) {
            // The SDK's message names the file and never the password.
            throw new IllegalStateException(setting + " could not be used. " + ex.getMessage() + hint, ex);
        }
    }

    private static Path path(final String value) {
        return value == null || value.isBlank() ? null : Path.of(value.strip());
    }

    private static char[] password(final String value) {
        return value == null || value.isEmpty() ? null : value.toCharArray();
    }

}
