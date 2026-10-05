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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PagesTest {

    /** A listing of {@code total} items that, like Philter, returns at most 100 per request. */
    private static Pages.Fetcher<Integer> philter(final int total, final List<String> requests) {
        return (offset, limit) -> {
            requests.add(offset + "+" + limit);
            final int capped = Math.min(limit, Pages.MAX_LIMIT);
            return IntStream.range(offset, Math.min(total, offset + capped)).boxed().toList();
        };
    }

    @Test
    void aRangeLargerThanPhiltersLimitIsReadInSeveralRequests() throws Exception {
        final List<String> requests = new ArrayList<>();
        final List<Integer> items = Pages.read(0, 250, philter(1000, requests));
        assertEquals(IntStream.range(0, 250).boxed().toList(), items);
        assertEquals(List.of("0+100", "100+100", "200+50"), requests);
    }

    @Test
    void stopsAtTheEndOfTheListing() throws Exception {
        final List<String> requests = new ArrayList<>();
        assertEquals(IntStream.range(50, 120).boxed().toList(), Pages.read(50, 150, philter(120, requests)));
        assertEquals(List.of("50+100"), requests);
    }

    @Test
    void anEmptyListingIsOneRequest() throws Exception {
        final List<String> requests = new ArrayList<>();
        assertEquals(List.of(), Pages.read(0, 50, philter(0, requests)));
        assertEquals(List.of("0+50"), requests);
    }

}
