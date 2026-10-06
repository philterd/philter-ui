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

import ai.philterd.philter.model.exceptions.ClientException;
import ai.philterd.philter.model.exceptions.ServiceUnavailableException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhilterFailuresTest {

    @Test
    void philtersExplanationIsShown() {
        assertEquals("Listing users requires an administrator.", PhilterFailures.messageFor(
                new ClientException("HTTP 403", 403, "Listing users requires an administrator.")));
        assertEquals("Philter answered with HTTP 500.", PhilterFailures.messageFor(new ClientException("HTTP 500", 500, null)));
    }

    @Test
    void philterBeingUnreachableIsSaid() {
        assertEquals(SignInMessages.UNAVAILABLE, PhilterFailures.messageFor(new ServiceUnavailableException("503")));
        assertEquals(SignInMessages.UNAVAILABLE,
                PhilterFailures.messageFor(new PhilterUnreachableException(new ConnectException("refused"))));
    }

    @Test
    void theFailureIsFoundWhereverVaadinWrapsIt() {
        assertEquals("No such list.", PhilterFailures.messageFor(new IllegalStateException("wrapped",
                new RuntimeException(new ClientException("HTTP 404", 404, "No such list.")))));
    }

    @Test
    void otherFailuresAreNotBlamedOnPhilter() {
        assertNull(PhilterFailures.messageFor(new NullPointerException()));
        assertNull(PhilterFailures.messageFor(new UncheckedIOException(new IOException("template missing"))));
        assertNull(PhilterFailures.messageFor(new RuntimeException(new IOException("Broken pipe"))));
        assertNull(PhilterFailures.messageFor(null));
    }

    @Test
    void aRepeatedFailureIsShownOnce() {
        final SessionErrorHandler.LastFailure last = new SessionErrorHandler.LastFailure("No such list.", 1_000L);
        assertTrue(SessionErrorHandler.isRepeat(last, "No such list.", 1_000L + 1_000_000_000L));
        assertFalse(SessionErrorHandler.isRepeat(last, "No such list.", 1_000L + SessionErrorHandler.REPEAT_WINDOW_NANOS));
        assertFalse(SessionErrorHandler.isRepeat(last, "Another message.", 1_001L));
        assertFalse(SessionErrorHandler.isRepeat(null, "No such list.", 1_001L));
    }

}
