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

import ai.philterd.philter.model.SignInOptions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SignInViewTest {

    @Test
    void theFormIsOffOnlyWhenPhilterSaysPasswordSignInIs() {
        assertNull(SignInView.passwordSignInOff(Optional.of(new SignInOptions())));
        assertEquals(SignInView.PASSWORD_SIGN_IN_OFF, SignInView.passwordSignInOff(Optional.empty()));
        // When Philter cannot be asked, the form stays usable and signing in reports the problem.
        assertNull(SignInView.passwordSignInOff(null));
    }

}
