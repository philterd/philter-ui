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

import com.vaadin.flow.component.html.Anchor;

/** Links to Philter's published documentation. */
final class PhilterDocs {

    private static final String BASE = "https://philterd.github.io/philter/latest/";

    private PhilterDocs() {
    }

    /** A link that opens a page of Philter's documentation in a new tab. */
    static Anchor link(final String text, final String page) {
        final Anchor anchor = new Anchor(BASE + page, text);
        anchor.setTarget("_blank");
        return anchor;
    }

}
