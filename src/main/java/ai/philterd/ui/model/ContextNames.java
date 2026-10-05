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

import com.google.gson.Gson;

import java.util.List;

/** Reads the context listings {@code GET /api/contexts} returns. */
public final class ContextNames {

    private static final Gson GSON = new Gson();

    /** A context in the listing of every user's contexts. */
    public record OwnedContext(String name, String owner) {
    }

    private ContextNames() {
    }

    /** The caller's own context names. */
    public static List<String> names(final String json) {
        final Names names = GSON.fromJson(json, Names.class);
        return names == null || names.contexts == null ? List.of() : names.contexts;
    }

    /** Every user's contexts, from {@code all_users=true}. */
    public static List<OwnedContext> owned(final String json) {
        final Owned owned = GSON.fromJson(json, Owned.class);
        return owned == null || owned.contexts == null ? List.of() : owned.contexts;
    }

    private static final class Names {
        private List<String> contexts;
    }

    private static final class Owned {
        private List<OwnedContext> contexts;
    }

}
