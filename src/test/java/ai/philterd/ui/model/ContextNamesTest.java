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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContextNamesTest {

    @Test
    void readsTheCallersContextNames() {
        assertEquals(List.of("default", "my-context"), ContextNames.names("{\"contexts\":[\"default\",\"my-context\"]}"));
        assertEquals(List.of(), ContextNames.names("{\"contexts\":[]}"));
    }

    @Test
    void readsEveryUsersContexts() {
        assertEquals(List.of(new ContextNames.OwnedContext("default", "jordan")),
                ContextNames.owned("{\"contexts\":[{\"name\":\"default\",\"owner\":\"jordan\"}]}"));
    }

}
