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
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PhilterErrorsTest {

    @Test
    void readsTheStatusAndMessageTheSdkPutsInTheExceptionMessage() {
        final ClientException e = new ClientException("Unknown error: HTTP 400: {\"message\":\"The password is too short.\"}");
        assertEquals(OptionalInt.of(400), PhilterErrors.status(e));
        assertEquals("The password is too short.", PhilterErrors.message(e));
    }

    @Test
    void toleratesBodiesThatAreMissingOrNotJson() {
        assertEquals(OptionalInt.of(409), PhilterErrors.status(new ClientException("Unknown error: HTTP 409")));
        assertNull(PhilterErrors.message(new ClientException("Unknown error: HTTP 409")));
        assertNull(PhilterErrors.message(new ClientException("Unknown error: HTTP 500: {\"message\":\"cut of...")));
        assertEquals(OptionalInt.empty(), PhilterErrors.status(new ClientException("Something else")));
    }

}
