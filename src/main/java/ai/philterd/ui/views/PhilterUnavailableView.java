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

import ai.philterd.philter.model.exceptions.ServiceUnavailableException;
import com.vaadin.flow.router.ParentLayout;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletResponse;

/** A page could not open because Philter said it was unavailable. */
@ParentLayout(MainLayout.class)
@PermitAll
public class PhilterUnavailableView extends PhilterFailureView<ServiceUnavailableException> {

    public PhilterUnavailableView() {
        super(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    }

}
