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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a range of a Philter listing. Philter returns at most {@link #MAX_LIMIT} items per request and
 * no total, so a range larger than that is read in several requests, stopping at the first short page.
 */
public final class Pages {

    /** The most items Philter returns for one listing request. */
    public static final int MAX_LIMIT = 100;

    /** Reads one page: the items from {@code offset}, at most {@code limit} of them. */
    public interface Fetcher<T> {
        List<T> fetch(int offset, int limit) throws IOException;
    }

    private Pages() {
    }

    public static <T> List<T> read(final int offset, final int limit, final Fetcher<T> fetcher) throws IOException {
        final List<T> items = new ArrayList<>();
        while (items.size() < limit) {
            final int requested = Math.min(MAX_LIMIT, limit - items.size());
            final List<T> page = fetcher.fetch(offset + items.size(), requested);
            items.addAll(page);
            if (page.size() < requested) {
                break;
            }
        }
        return items;
    }

}
