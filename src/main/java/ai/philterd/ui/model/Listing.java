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

import ai.philterd.philter.model.Page;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A range of one of Philter's listings and how many items match across every page, as Philter reports it.
 *
 * @param items The items in the range, in Philter's order.
 * @param total How many items match across every page.
 */
public record Listing<T>(List<T> items, long total) {

    /** The most items Philter returns for one listing request. */
    public static final int MAX_LIMIT = 100;

    public Listing {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** Reads at most {@code limit} items from {@code offset} in one request, with the total. */
    public interface Source<T> {
        Listing<T> read(int offset, int limit) throws IOException;
    }

    public static <T> Listing<T> of(final Page<T> page) {
        return new Listing<>(page.getItems(), page.getTotal());
    }

    /**
     * Reads {@code limit} items from {@code offset}, in requests of at most {@link #MAX_LIMIT}, stopping once
     * Philter's total is reached or a request returns nothing.
     */
    public static <T> Listing<T> read(final int offset, final int limit, final Source<T> source) throws IOException {
        final List<T> items = new ArrayList<>();
        long total = 0;
        do {
            // At least one, so a request for none still learns the total.
            final int requested = Math.min(MAX_LIMIT, Math.max(1, limit - items.size()));
            final Listing<T> page = source.read(offset + items.size(), requested);
            total = page.total();
            items.addAll(page.items());
            if (page.items().isEmpty()) {
                break;
            }
        } while (items.size() < limit && offset + items.size() < total);
        return new Listing<>(items.subList(0, Math.min(items.size(), Math.max(0, limit))), total);
    }

}
