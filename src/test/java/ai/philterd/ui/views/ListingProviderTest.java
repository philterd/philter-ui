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

import ai.philterd.philter.model.ListOptions;
import ai.philterd.ui.model.Listing;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.data.provider.QuerySortOrder;
import com.vaadin.flow.data.provider.SortDirection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ListingProviderTest {

    /** Philter's listing of policy-0 to policy-(total-1), sorted by name and searched as Philter does. */
    private static final class Policies implements ListingProvider.Reader<String> {

        private final int total;
        final List<ListOptions> requests = new ArrayList<>();

        Policies(final int total) {
            this.total = total;
        }

        @Override
        public Listing<String> read(final ListOptions options) {
            requests.add(options);
            Comparator<String> order = Comparator.naturalOrder();
            if (ListOptions.DESCENDING.equals(options.getOrder())) {
                order = order.reversed();
            }
            final List<String> matching = IntStream.range(0, total).mapToObj(i -> String.format("policy-%03d", i))
                    .filter(name -> options.getSearch() == null || name.contains(options.getSearch()))
                    .sorted(order).toList();
            final int from = Math.min(options.getOffset(), matching.size());
            final int to = Math.min(matching.size(), from + options.getLimit());
            return new Listing<>(matching.subList(from, to), matching.size());
        }
    }

    private static Query<String, Void> rows(final int offset, final int limit, final List<QuerySortOrder> sort) {
        return new Query<>(offset, limit, sort, null, null);
    }

    @Test
    void theCountComesFromPhiltersTotalAndTheFirstRowsFromTheSameRequest() {
        final Policies philter = new Policies(120);
        final List<Long> totals = new ArrayList<>();
        final ListingProvider<String> provider = new ListingProvider<>(philter, 50).onTotal(totals::add);

        assertEquals(120, provider.count(rows(0, Integer.MAX_VALUE, List.of())));
        assertEquals(50, provider.fetch(rows(0, 50, List.of())).count());
        // One request served the count and the first rows.
        assertEquals(1, philter.requests.size());

        assertEquals(List.of("policy-050", "policy-051"), provider.fetch(rows(50, 50, List.of())).limit(2).toList());
        assertEquals(2, philter.requests.size());
        assertEquals(50, philter.requests.get(1).getOffset());
        assertEquals(List.of(120L, 120L, 120L), totals);
    }

    @Test
    void aFirstFetchLargerThanTheCountsPageReadsOnlyTheRest() {
        final Policies philter = new Policies(120);
        final ListingProvider<String> provider = new ListingProvider<>(philter, 50);
        provider.count(rows(0, Integer.MAX_VALUE, List.of()));
        assertEquals(100, provider.fetch(rows(0, 100, List.of())).count());
        assertEquals(2, philter.requests.size());
        assertEquals(50, philter.requests.get(1).getOffset());
        assertEquals(50, philter.requests.get(1).getLimit());
    }

    @Test
    void aSortedColumnSortsOnPhilter() {
        final Policies philter = new Policies(30);
        final ListingProvider<String> provider = new ListingProvider<>(philter, 50);
        final List<QuerySortOrder> descending = List.of(new QuerySortOrder("name", SortDirection.DESCENDING));
        assertEquals("policy-029", provider.fetch(rows(0, 50, descending)).findFirst().orElseThrow());
        assertEquals("name", philter.requests.get(0).getSort());
        assertEquals(ListOptions.DESCENDING, philter.requests.get(0).getOrder());
    }

    @Test
    void theFirstPageIsNotReusedForADifferentSort() {
        final Policies philter = new Policies(30);
        final ListingProvider<String> provider = new ListingProvider<>(philter, 50);
        provider.count(rows(0, Integer.MAX_VALUE, List.of()));
        final List<QuerySortOrder> descending = List.of(new QuerySortOrder("name", SortDirection.DESCENDING));
        assertEquals("policy-029", provider.fetch(rows(0, 50, descending)).findFirst().orElseThrow());
        assertEquals(2, philter.requests.size());
    }

    @Test
    void aSearchIsSentToPhilterAndCountsOnlyMatches() {
        final Policies philter = new Policies(120);
        final List<Long> totals = new ArrayList<>();
        final ListingProvider<String> provider = new ListingProvider<>(philter, 50).onTotal(totals::add);
        provider.setSearch("  policy-11 ");
        assertEquals(10, provider.count(rows(0, Integer.MAX_VALUE, List.of())));
        assertEquals("policy-11", philter.requests.get(0).getSearch());
        assertEquals(List.of(10L), totals);

        provider.setSearch(" ");
        assertEquals(120, provider.count(rows(0, Integer.MAX_VALUE, List.of())));
        assertNull(philter.requests.get(1).getSearch());
    }

    @Test
    void optionsCarryTheRangeAndTheFirstSortOrder() {
        final ListOptions options = ListingProvider.options(25, 50, List.of(
                new QuerySortOrder("created", SortDirection.ASCENDING), new QuerySortOrder("name", SortDirection.DESCENDING)),
                "claims");
        assertEquals(25, options.getOffset());
        assertEquals(50, options.getLimit());
        assertEquals("created", options.getSort());
        assertEquals(ListOptions.ASCENDING, options.getOrder());
        assertEquals("claims", options.getSearch());
        assertNull(ListingProvider.options(0, 10, List.of(), null).getSort());
    }

    @Test
    void countsAreWorded() {
        assertEquals("1 policy", ListingProvider.count(1, "policy", "policies"));
        assertEquals("0 policies", ListingProvider.count(0, "policy", "policies"));
        assertEquals("12 policies", ListingProvider.count(12, "policy", "policies"));
    }

}
