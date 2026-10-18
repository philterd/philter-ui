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

import ai.philterd.philter.model.ContextSummary;
import ai.philterd.philter.model.ListOptions;
import ai.philterd.philter.model.Page;
import ai.philterd.philter.model.PolicySummary;
import ai.philterd.ui.model.Listing;
import com.google.gson.Gson;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.data.provider.QuerySortOrder;
import com.vaadin.flow.data.provider.SortDirection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The policy and context listings, which Philter 4.0 returns as summaries rather than names. */
class SummaryListingsTest {

    private static final Gson GSON = new Gson();

    /** Rows from the examples in Philter's policies and contexts API documentation. */
    private static final PolicySummary DEFAULT_POLICY = GSON.fromJson("""
            { "name": "default", "description": "Default policy", "revision": 0,
              "createdAt": "2026-10-01T12:00:00.000Z", "updatedAt": "2026-10-01T12:00:00.000Z" }""", PolicySummary.class);
    private static final PolicySummary MY_POLICY = GSON.fromJson("""
            { "name": "my-policy", "description": "", "revision": 3,
              "createdAt": "2026-10-02T09:15:00.000Z", "updatedAt": "2026-10-08T16:42:10.000Z" }""", PolicySummary.class);
    private static final ContextSummary MY_CONTEXT = GSON.fromJson("""
            { "name": "my-context", "createdAt": "2026-10-05T10:20:30.000Z" }""", ContextSummary.class);

    /** Each header's sort field, or {@code null} for a column Philter does not sort, once bound to a listing. */
    private static <T> List<String> sortFields(final Grid<T> grid, final List<ListOptions> requests) {
        ListingProvider.bind(grid, options -> {
            requests.add(options);
            return new Listing<>(List.of(), 0);
        });
        return grid.getColumns().stream().map(column -> column.isSortable()
                ? column.getSortOrder(SortDirection.ASCENDING).findFirst().map(QuerySortOrder::getSorted).orElse(null)
                : null).toList();
    }

    private static <T> List<String> headers(final Grid<T> grid) {
        return grid.getColumns().stream().map(Grid.Column::getHeaderText).toList();
    }

    @Test
    void policiesShowTheirDescriptionAndTimesAndSortOnPhilter() {
        final Grid<PolicySummary> grid = new Grid<>();
        PoliciesView.summaryColumns(grid);
        assertEquals(List.of("Name", "Description", "Created", "Updated"), headers(grid));
        assertEquals(Arrays.asList("name", null, "created", "updated"), sortFields(grid, new ArrayList<>()));

        assertEquals("Default policy", PoliciesView.description(DEFAULT_POLICY));
        assertEquals("", PoliciesView.description(MY_POLICY));
        assertEquals("", PoliciesView.description(new PolicySummary()), "no description is blank, not null");
        assertEquals("2026-10-08 16:42 UTC", ViewSupport.utc(MY_POLICY.getUpdatedAtText()));
        assertEquals("2026-10-02 09:15 UTC", ViewSupport.utc(MY_POLICY.getCreatedAtText()));
    }

    @Test
    void contextsShowWhenTheyWereCreatedAndSortOnPhilter() {
        final Grid<ContextSummary> grid = new Grid<>();
        ContextsView.summaryColumns(grid);
        assertEquals(List.of("Context", "Created"), headers(grid));
        assertEquals(List.of("name", "created"), sortFields(grid, new ArrayList<>()));
        assertEquals("2026-10-05 10:20 UTC", ViewSupport.utc(MY_CONTEXT.getCreatedAtText()));
    }

    @Test
    void sortingByAHeaderSendsItsFieldToPhilter() {
        final List<ListOptions> requests = new ArrayList<>();
        final Grid<PolicySummary> grid = new Grid<>();
        PoliciesView.summaryColumns(grid);
        final ListingProvider<PolicySummary> provider = new ListingProvider<>(options -> {
            requests.add(options);
            return new Listing<>(List.of(MY_POLICY, DEFAULT_POLICY), 2);
        }, 50);
        final Grid.Column<PolicySummary> updated = grid.getColumns().get(3);
        provider.fetch(new Query<>(0, 50, updated.getSortOrder(SortDirection.DESCENDING).toList(), null, null));
        assertEquals("updated", requests.get(0).getSort());
        assertEquals(ListOptions.DESCENDING, requests.get(0).getOrder());
    }

    @Test
    void theDashboardSelectsTheDefaultPolicyByName() {
        final Listing<String> first = Listing.of(page(MY_POLICY)).map(PolicySummary::getName);
        final List<String> matches = Listing.of(page(DEFAULT_POLICY)).map(PolicySummary::getName).items();
        assertEquals("default", DashboardView.initialPolicy(first, "default", matches));
    }

    private static Page<PolicySummary> page(final PolicySummary... policies) {
        return new Page<>(List.of(policies), policies.length);
    }

}
