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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContextsViewTest {

    private static ClientException conflict(final String reason, final String message) {
        return new ClientException("HTTP 409", 409, message, reason);
    }

    @Test
    void theContextLimitIsNotReportedAsADuplicateName() {
        assertEquals("You already have as many contexts as Philter allows. Delete one first.",
                ContextsView.createFailure(conflict("context_limit_reached", "Maximum number of contexts reached.")));
    }

    @Test
    void aDuplicateNameIsReported() {
        assertEquals("You already have a context with this name.",
                ContextsView.createFailure(conflict("context_exists", "Context already exists.")));
    }

    @Test
    void aConflictWithoutAReasonIsADuplicateName() {
        assertEquals("You already have a context with this name.", ContextsView.createFailure(
                new ClientException("HTTP 409", 409, "Context already exists.")));
    }

    @Test
    void anyOtherRefusalShowsPhiltersMessage() {
        assertEquals("Context name cannot be blank.", ContextsView.createFailure(
                new ClientException("HTTP 400", 400, "Context name cannot be blank.")));
        assertEquals("The context could not be created.",
                ContextsView.createFailure(new ClientException("HTTP 500", 500, null)));
    }

}
