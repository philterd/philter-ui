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
package ai.philterd.ui.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PolicyNamesTest {

    @Test
    void acceptsWhatPhilterAccepts() {
        assertNull(PolicyNames.problem("hipaa-2026_v2"));
        assertNull(PolicyNames.problem("x".repeat(PolicyNames.MAXIMUM_LENGTH)));
        assertNull(PolicyNames.problem("my_managed_policy"));
    }

    @Test
    void refusesWhatPhilterRefuses() {
        assertEquals("Enter a name.", PolicyNames.problem(" "));
        assertEquals("A policy name can be at most 50 characters.", PolicyNames.problem("x".repeat(51)));
        assertEquals("A policy name can contain only letters, digits, dashes, and underscores.", PolicyNames.problem("my policy"));
        assertEquals("A policy name can contain only letters, digits, dashes, and underscores.", PolicyNames.problem("a/b"));
        assertEquals("A policy name cannot start with managed_, which is reserved for managed policies.",
                PolicyNames.problem("managed_copy"));
    }

}
