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

import ai.philterd.ui.security.PhilterUnreachableException;
import com.vaadin.flow.router.ParentLayout;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletResponse;

/** A page could not open because Philter could not be reached. */
@ParentLayout(MainLayout.class)
@PermitAll
public class PhilterUnreachableView extends PhilterFailureView<PhilterUnreachableException> {

    public PhilterUnreachableView() {
        super(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    }

}
