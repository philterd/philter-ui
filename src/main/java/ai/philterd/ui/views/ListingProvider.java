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
import ai.philterd.ui.security.PhilterUnreachableException;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.data.provider.QuerySortOrder;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.flow.data.value.ValueChangeMode;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.function.LongConsumer;
import java.util.stream.Stream;

/**
 * Feeds a grid from one of Philter's listings, a page at a time: the row count comes from Philter's total, columns
 * with a sort property sort on Philter, and an optional search is sent as {@code q}. Vaadin asks for the count
 * before the first rows, so the count's request reads the first page and the first rows come from it: each
 * refresh makes one request, and each further page one more.
 */
final class ListingProvider<T> implements Serializable {

    /** Reads one request's worth of a listing with the given paging, sort, and search. */
    interface Reader<T> extends Serializable {
        Listing<T> read(ListOptions options) throws IOException;
    }

    private final Reader<T> reader;
    private final int firstPage;
    private LongConsumer onTotal = total -> { };
    private String search;
    private List<QuerySortOrder> sort = List.of();
    /** The first page as the last count read it, with the sort and search it was read with. */
    private First<T> first;

    private record First<T>(String key, Listing<T> listing) implements Serializable {
    }

    ListingProvider(final Reader<T> reader, final int firstPage) {
        this.reader = reader;
        this.firstPage = firstPage;
    }

    /**
     * Feeds {@code grid} from {@code reader}, following the grid's sort. Call it after adding the columns: only those
     * given a sort property, one of the listing's sort fields, stay sortable.
     */
    static <T> ListingProvider<T> bind(final Grid<T> grid, final Reader<T> reader) {
        final ListingProvider<T> provider = new ListingProvider<>(reader, grid.getPageSize());
        grid.getColumns().forEach(column ->
                column.setSortable(column.getSortOrder(SortDirection.ASCENDING).findAny().isPresent()));
        grid.addSortListener(e -> provider.sort = e.getSortOrder().stream()
                .flatMap(order -> order.getSorted().getSortOrder(order.getDirection()))
                .toList());
        grid.setItems(DataProvider.fromCallbacks(provider::fetch, provider::count));
        return provider;
    }

    /** Tells {@code onTotal} Philter's total after each read, for a count shown beside the grid. */
    ListingProvider<T> onTotal(final LongConsumer onTotal) {
        this.onTotal = onTotal;
        return this;
    }

    /**
     * A row above the grid with the number of items from Philter's total, such as "3 policies", and, when
     * {@code searchPlaceholder} is given, a search field.
     */
    HorizontalLayout toolbar(final Grid<T> grid, final String searchPlaceholder, final String one, final String many) {
        final Span count = new Span();
        onTotal(total -> count.setText(count(total, one, many)));
        final HorizontalLayout toolbar = searchPlaceholder == null ? new HorizontalLayout(count)
                : new HorizontalLayout(searchField(grid, searchPlaceholder), count);
        toolbar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);
        return toolbar;
    }

    /** How many items there are, such as "1 policy" or "3 policies". */
    static String count(final long total, final String one, final String many) {
        return total + " " + (total == 1 ? one : many);
    }

    /** A search field that filters {@code grid} as the person types. */
    TextField searchField(final Grid<T> grid, final String placeholder) {
        final TextField field = new TextField();
        field.setPlaceholder(placeholder);
        field.setClearButtonVisible(true);
        field.setPrefixComponent(VaadinIcon.SEARCH.create());
        field.setValueChangeMode(ValueChangeMode.LAZY);
        field.addValueChangeListener(e -> {
            setSearch(e.getValue());
            grid.getDataProvider().refreshAll();
        });
        return field;
    }

    void setSearch(final String search) {
        this.search = search == null || search.isBlank() ? null : search.trim();
    }

    Stream<T> fetch(final Query<T, Void> query) {
        final List<QuerySortOrder> sorts = query.getSortOrders();
        final String key = key(sorts, search);
        final First<T> cached = first;
        first = null;
        Listing<T> listing;
        if (query.getOffset() == 0 && cached != null && cached.key().equals(key)) {
            listing = cached.listing();
            final int have = listing.items().size();
            if (have < query.getLimit() && have < listing.total()) {
                final Listing<T> rest = read(have, query.getLimit() - have, sorts);
                listing = new Listing<>(Stream.concat(listing.items().stream(), rest.items().stream()).toList(),
                        rest.total());
            }
        } else {
            listing = read(query.getOffset(), query.getLimit(), sorts);
        }
        onTotal.accept(listing.total());
        return listing.items().stream().limit(query.getLimit());
    }

    int count(final Query<T, Void> query) {
        final Listing<T> listing = read(0, firstPage, sort);
        first = new First<>(key(sort, search), listing);
        onTotal.accept(listing.total());
        return (int) Math.min(Integer.MAX_VALUE, listing.total());
    }

    private Listing<T> read(final int offset, final int limit, final List<QuerySortOrder> sorts) {
        try {
            return Listing.read(offset, limit, (start, count) -> reader.read(options(start, count, sorts, search)));
        } catch (final IOException e) {
            throw new PhilterUnreachableException(e);
        }
    }

    /** The listing request for a range, the grid's first sort order, and the search. */
    static ListOptions options(final int offset, final int limit, final List<QuerySortOrder> sorts, final String search) {
        final ListOptions options = new ListOptions().offset(offset).limit(limit).search(search);
        if (sorts != null && !sorts.isEmpty()) {
            final QuerySortOrder order = sorts.get(0);
            options.sort(order.getSorted()).order(order.getDirection() == SortDirection.DESCENDING
                    ? ListOptions.DESCENDING : ListOptions.ASCENDING);
        }
        return options;
    }

    private static String key(final List<QuerySortOrder> sorts, final String search) {
        final ListOptions options = options(0, 0, sorts, search);
        return options.getSort() + "|" + options.getOrder() + "|" + options.getSearch();
    }

}
