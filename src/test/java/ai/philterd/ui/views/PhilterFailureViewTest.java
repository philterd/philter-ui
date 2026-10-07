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
package ai.philterd.ui.views;

import ai.philterd.philter.model.exceptions.ClientException;
import ai.philterd.philter.model.exceptions.ServiceUnavailableException;
import ai.philterd.ui.security.PhilterUnreachableException;
import ai.philterd.ui.security.SignInMessages;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhilterFailureViewTest {

    @Test
    void thePageSaysWhyItCouldNotOpen() {
        assertEquals(SignInMessages.UNAVAILABLE,
                PhilterFailureView.message(new PhilterUnreachableException(new ConnectException("refused"))));
        assertEquals(SignInMessages.UNAVAILABLE, PhilterFailureView.message(new ServiceUnavailableException("503")));
        assertEquals("Reading the admin settings requires an administrator.", PhilterFailureView.message(
                new ClientException("HTTP 403", 403, "Reading the admin settings requires an administrator.")));
        assertEquals("Philter answered with HTTP 500.",
                PhilterFailureView.message(new ClientException("HTTP 500", 500, null)));
    }

}
