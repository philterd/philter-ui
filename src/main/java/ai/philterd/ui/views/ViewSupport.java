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

import ai.philterd.philter.PhilterClient;
import ai.philterd.philter.model.exceptions.ClientException;
import ai.philterd.ui.security.PhilterUser;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.icon.VaadinIcon;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** Small pieces shared by the views that list and edit Philter resources. */
final class ViewSupport {

    /** A call to Philter that returns something. */
    interface PhilterQuery<T> {
        T get() throws IOException;
    }

    private ViewSupport() {
    }

    /**
     * Whether to show an administrator every user's resources: the person is an administrator and
     * Philter allows cross-user access ({@code ADMIN_CROSS_USER_ACCESS_ENABLED}).
     */
    static boolean showAllUsers(final PhilterUser user, final PhilterClient client) {
        if (!user.isAdministrator()) {
            return false;
        }
        try {
            return client.getAdminSettings().isCrossUserAccessEnabled();
        } catch (final ClientException e) {
            return false;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A grid row's button, labeled for screen readers with its tooltip. */
    static Button button(final String text, final VaadinIcon icon, final String tooltip, final Runnable action) {
        final Button button = new Button(text, icon.create(), e -> action.run());
        button.setTooltipText(tooltip);
        button.setAriaLabel(tooltip);
        return button;
    }

    static Button cancel(final Dialog dialog) {
        final Button cancel = new Button("Cancel", e -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        return cancel;
    }

    static <T> List<T> orEmpty(final List<T> items) {
        return items == null ? List.of() : items;
    }

    /** Runs a call to Philter where a checked exception cannot be thrown, such as a grid's data callback. */
    static <T> T unchecked(final PhilterQuery<T> query) {
        try {
            return query.get();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
