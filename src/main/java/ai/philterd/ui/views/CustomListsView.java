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
import ai.philterd.philter.model.CustomListSummary;
import ai.philterd.philter.model.GetListsResponse;
import ai.philterd.philter.model.exceptions.ClientException;
import ai.philterd.philter.model.exceptions.ServiceUnavailableException;
import ai.philterd.ui.model.Lines;
import ai.philterd.ui.model.Pages;
import ai.philterd.ui.model.PathSafeNames;
import ai.philterd.ui.security.PhilterClients;
import ai.philterd.ui.security.PhilterUser;
import ai.philterd.ui.security.Roles;
import ai.philterd.ui.security.Sessions;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

/**
 * The person's custom lists, through Philter's Custom Lists API. Administrators also see every user's
 * lists, read-only, when Philter allows cross-user access.
 */
@Route(value = "custom-lists", layout = MainLayout.class)
@PageTitle("Custom Lists | Philter UI")
@RolesAllowed(Roles.USER)
public class CustomListsView extends VerticalLayout {

    /** Philter's limits on a list's items, checked here too so a mistake shows before the request. */
    static final int MAXIMUM_ITEMS = 100;
    static final int MAXIMUM_ITEM_LENGTH = 50;

    /** Kept short because Philter takes the description in the query string. */
    static final int MAXIMUM_DESCRIPTION_LENGTH = 250;

    private final transient PhilterClient client;
    private final Grid<CustomListSummary> grid = new Grid<>();

    public CustomListsView(final PhilterClients clients) {

        final PhilterUser user = Sessions.currentUser().orElseThrow();
        this.client = clients.forUser(user);

        setSizeFull();

        grid.addColumn(CustomListSummary::getName).setHeader("Name").setResizable(true).setSortable(true)
                .setComparator(Comparator.comparing(CustomListSummary::getName, String.CASE_INSENSITIVE_ORDER));
        grid.addColumn(CustomListSummary::getDescription).setHeader("Description").setResizable(true);
        grid.addColumn(CustomListSummary::getSize).setHeader("Terms").setSortable(true)
                .setComparator(Comparator.comparingInt(CustomListSummary::getSize)).setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::editButton).setHeader("Edit").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(list -> ViewSupport.button(null, VaadinIcon.TRASH, "Delete custom list " + list.getName(),
                () -> openDelete(list.getName()))).setHeader("Delete").setAutoWidth(true).setFlexGrow(0);
        grid.setSizeFull();
        refresh();

        final Span description = new Span("Custom lists can be referenced by policies to include a list of terms to "
                + "always or never redact. ");
        description.add(PhilterDocs.link("Learn more about custom lists.", "redaction/custom_lists.html"));

        final VerticalLayout mine = new VerticalLayout(description, grid);
        mine.setSizeFull();

        final TabSheet tabs = new TabSheet();
        tabs.add("My Custom Lists", mine);
        if (ViewSupport.showAllUsers(user, client)) {
            tabs.add("All Custom Lists", allLists());
        }
        tabs.setSizeFull();

        final Button create = new Button("New Custom List", VaadinIcon.LIST.create(), e -> openCreate());
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        create.setTooltipText("Create a new custom list.");
        tabs.setSuffixComponent(create);

        add(new H2("Custom Lists"), tabs);

    }

    /** Philter returns all of a person's lists at once, so the grid holds them in memory and sorts them itself. */
    private void refresh() {
        grid.setItems(ViewSupport.orEmpty(ViewSupport.unchecked(client::listCustomLists)));
    }

    private Button editButton(final CustomListSummary list) {
        final Button edit = ViewSupport.button(null, VaadinIcon.EDIT, "Edit custom list " + list.getName(),
                () -> openEdit(list.getName()));
        // A list named before Philter checked names cannot be opened or changed, only deleted.
        if (!PathSafeNames.isPathSafe(list.getName())) {
            edit.setEnabled(false);
            edit.setTooltipText("This list's name " + PathSafeNames.RULE + ", so it can only be deleted.");
        }
        return edit;
    }

    private VerticalLayout allLists() {

        final Grid<CustomListSummary> all = new Grid<>();
        all.addColumn(CustomListSummary::getName).setHeader("Custom List").setResizable(true);
        all.addColumn(CustomListSummary::getDescription).setHeader("Description").setResizable(true);
        all.addColumn(CustomListSummary::getOwner).setHeader("Owner").setResizable(true);
        all.addColumn(CustomListSummary::getSize).setHeader("Terms").setAutoWidth(true).setFlexGrow(0);
        // Philter's listing has no total, so the grid pages until a page comes back short.
        all.setItems(query -> ViewSupport.unchecked(() -> Pages.read(query.getOffset(), query.getLimit(),
                (offset, limit) -> ViewSupport.orEmpty(client.listCustomListsAcrossUsers(offset, limit)))).stream());
        all.setSizeFull();

        final VerticalLayout layout = new VerticalLayout(new Span("All custom lists across all users."), all);
        layout.setSizeFull();
        return layout;

    }

    private void openCreate() {

        final TextField name = new TextField("List name");
        name.setWidthFull();
        name.setRequired(true);
        final TextField description = descriptionField();
        final TextArea items = itemsField();
        final Span error = errorSpan();

        final Dialog dialog = listDialog("New Custom List", error, name, description, items);

        final Button save = new Button("Save", e -> {
            error.setVisible(false);
            name.setInvalid(false);
            if (name.getValue() == null || name.getValue().isBlank()) {
                invalid(name, "Enter a name.");
                return;
            }
            if (!PathSafeNames.isPathSafe(name.getValue())) {
                invalid(name, "The list name " + PathSafeNames.RULE + ".");
                return;
            }
            final List<String> values = Lines.terms(items.getValue());
            final String problem = itemsProblem(values);
            if (problem != null) {
                showError(error, problem);
                return;
            }
            try {
                client.saveList(name.getValue(), blankToNull(description.getValue()), values);
                dialog.close();
                refresh();
                Notifications.success("List created.");
            } catch (final ClientException ex) {
                if ("list_exists".equals(ex.getReason())) {
                    invalid(name, "You already have a list with this name.");
                } else {
                    showError(error, messageOr(ex, "The list could not be created."));
                }
            } catch (final IOException | ServiceUnavailableException ex) {
                Notifications.failure(ex, "The list could not be created.");
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(ViewSupport.cancel(dialog), save);
        dialog.open();

    }

    /** Edits a list's items and description, starting from what Philter has. */
    private void openEdit(final String listName) {

        final GetListsResponse list;
        try {
            list = client.getList(listName);
        } catch (final ClientException | ServiceUnavailableException | IOException e) {
            Notifications.failure(e, "The list could not be read.");
            refresh();
            return;
        }

        final TextField name = new TextField("List name");
        name.setWidthFull();
        name.setValue(listName);
        name.setReadOnly(true);
        final String originalDescription = list.getDescription() == null ? "" : list.getDescription();
        final TextField description = descriptionField();
        description.setValue(originalDescription);
        final TextArea items = itemsField();
        items.setValue(String.join("\n", ViewSupport.orEmpty(list.getLists())));
        final Span error = errorSpan();

        final Dialog dialog = listDialog("Edit Custom List", error, name, description, items);

        final Button save = new Button("Save", e -> {
            error.setVisible(false);
            final List<String> values = Lines.terms(items.getValue());
            final String problem = itemsProblem(values);
            if (problem != null) {
                showError(error, problem);
                return;
            }
            try {
                client.replaceList(listName, descriptionChange(originalDescription, description.getValue()), values);
                dialog.close();
                refresh();
                Notifications.success("List updated.");
            } catch (final ClientException ex) {
                if (ex.getStatusCode() == 404) {
                    // Deleted since the dialog opened.
                    dialog.close();
                    refresh();
                    Notifications.failure(ex, "The list no longer exists.");
                } else {
                    showError(error, messageOr(ex, "The list could not be updated."));
                }
            } catch (final IOException | ServiceUnavailableException ex) {
                Notifications.failure(ex, "The list could not be updated.");
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(ViewSupport.cancel(dialog), save);
        dialog.open();

    }

    private void openDelete(final String listName) {

        final Dialog dialog = new Dialog();
        dialog.add(new H3("Confirm Deletion"),
                new Paragraph("Are you sure you want to delete the list " + listName + "? This cannot be undone."));

        final Button delete = new Button("Delete", e -> {
            try {
                // The SDK sends a name that cannot be used in a path in the query instead.
                client.deleteList(listName);
                dialog.close();
                refresh();
                Notifications.success("List deleted.");
            } catch (final ClientException | ServiceUnavailableException | IOException ex) {
                Notifications.failure(ex, "The list could not be deleted.");
            }
        });
        delete.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        dialog.getFooter().add(ViewSupport.cancel(dialog), delete);
        dialog.open();

    }

    /** What is wrong with the items under Philter's limits, or {@code null} if nothing is. */
    static String itemsProblem(final List<String> items) {
        if (items.isEmpty()) {
            return "Enter at least one item.";
        }
        if (items.size() > MAXIMUM_ITEMS) {
            return "A list can have at most " + MAXIMUM_ITEMS + " items; this one has " + items.size() + ".";
        }
        for (final String item : items) {
            if (item.length() > MAXIMUM_ITEM_LENGTH) {
                return "Each item can be at most " + MAXIMUM_ITEM_LENGTH + " characters: \"" + item + "\" is longer.";
            }
        }
        return null;
    }

    /**
     * The description to send when replacing a list: {@code null} keeps Philter's, an empty string clears
     * it, and anything else replaces it.
     */
    static String descriptionChange(final String original, final String edited) {
        final String value = edited == null ? "" : edited.trim();
        return value.equals(original == null ? "" : original.trim()) ? null : value;
    }

    private static String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String messageOr(final ClientException e, final String fallback) {
        return e.getErrorMessage() == null ? fallback : e.getErrorMessage();
    }

    private static TextField descriptionField() {
        final TextField description = new TextField("Description");
        description.setWidthFull();
        description.setPlaceholder("Optional short description");
        description.setMaxLength(MAXIMUM_DESCRIPTION_LENGTH);
        description.setHelperText("Up to " + MAXIMUM_DESCRIPTION_LENGTH + " characters.");
        return description;
    }

    private static TextArea itemsField() {
        final TextArea items = new TextArea("Items, one per line");
        items.setWidthFull();
        items.setRequired(true);
        items.setMinRows(10);
        items.setMaxRows(20);
        items.setHelperText("Up to " + MAXIMUM_ITEMS + " items of up to " + MAXIMUM_ITEM_LENGTH + " characters each.");
        return items;
    }

    private static Span errorSpan() {
        final Span error = new Span();
        error.getStyle().set("color", "var(--lumo-error-text-color)");
        error.setVisible(false);
        return error;
    }

    private static void showError(final Span error, final String message) {
        error.setText(message);
        error.setVisible(true);
    }

    private static void invalid(final TextField field, final String message) {
        field.setErrorMessage(message);
        field.setInvalid(true);
    }

    private static Dialog listDialog(final String title, final Span error, final TextField name,
                                     final TextField description, final TextArea items) {
        final Dialog dialog = new Dialog();
        dialog.setWidth("500px");
        dialog.add(new H3(title), error, new VerticalLayout(name, description, items));
        return dialog;
    }

}
