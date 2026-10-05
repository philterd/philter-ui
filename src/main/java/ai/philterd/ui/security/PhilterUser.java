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

import ai.philterd.philter.PhilterClient;

import java.io.Serial;
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * A person signed in to Philter UI, and the Philter session key their requests use. The key lives only
 * here, in the server-side session; it is never sent to the browser or logged.
 */
public final class PhilterUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** What the session key is limited to, if anything. */
    public enum Restriction { NONE, PASSWORD_CHANGE, MFA_ENROLLMENT }

    private final String username;
    private final boolean administrator;
    private final Restriction restriction;
    private final String sessionKey;
    private final AtomicBoolean ended = new AtomicBoolean();
    private transient volatile PhilterClient client;

    public PhilterUser(final String username, final boolean administrator, final Restriction restriction,
                       final String sessionKey) {
        this.username = username;
        this.administrator = administrator;
        this.restriction = restriction;
        this.sessionKey = sessionKey;
    }

    public String getUsername() {
        return username;
    }

    public boolean isAdministrator() {
        return administrator;
    }

    public Restriction getRestriction() {
        return restriction;
    }

    String sessionKey() {
        return sessionKey;
    }

    synchronized PhilterClient client(final Supplier<PhilterClient> builder) {
        if (client == null) {
            client = builder.get();
        }
        return client;
    }

    /** Marks the session as ended, returning {@code false} if it already was, so the key is revoked once. */
    boolean markEnded() {
        return ended.compareAndSet(false, true);
    }

    @Override
    public String toString() {
        // Never include the session key: Spring Security logs principals at debug level.
        return "PhilterUser[" + username + ", administrator=" + administrator + ", restriction=" + restriction + "]";
    }

}
