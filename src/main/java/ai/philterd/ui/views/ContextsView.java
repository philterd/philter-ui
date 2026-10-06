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
import ai.philterd.philter.model.exceptions.ServiceUnavailableException;
import ai.philterd.ui.model.ContextDetails;
import ai.philterd.ui.model.ContextNames;
import ai.philterd.ui.model.Pages;
import ai.philterd.ui.security.PhilterClients;
import ai.philterd.ui.security.PhilterErrors;
import ai.philterd.ui.security.PhilterUser;
import ai.philterd.ui.security.Roles;
import ai.philterd.ui.security.Sessions;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * The person's redaction contexts, through Philter's Contexts API. Administrators also see every user's
 * contexts, read-only, when Philter allows cross-user access.
 */
@Route(value = "contexts", layout = MainLayout.class)
@PageTitle("Contexts | Philter UI")
@RolesAllowed(Roles.USER)
public class ContextsView extends VerticalLayout {

    private final transient PhilterClient client;
    private final Grid<String> grid = new Grid<>();

    public ContextsView(final PhilterClients clients) {

        final PhilterUser user = Sessions.currentUser().orElseThrow();
        this.client = clients.forUser(user);

        setSizeFull();

        grid.addColumn(name -> name).setHeader("Context").setResizable(true);
        grid.addComponentColumn(name -> button("View", VaadinIcon.DOCTOR_BRIEFCASE, "View context " + name,
                () -> openView(name, null))).setHeader("View").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(name -> button(null, VaadinIcon.EDIT, "Edit context " + name,
                () -> openEdit(name))).setHeader("Edit").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(name -> button(null, VaadinIcon.RECYCLE, "Clear context " + name,
                () -> openClear(name))).setHeader("Clear").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(name -> button(null, VaadinIcon.TRASH, "Delete context " + name,
                () -> openDelete(name))).setHeader("Delete").setAutoWidth(true).setFlexGrow(0);
        // Philter's listing has no total, so the grid pages until a page comes back short.
        grid.setItems(query -> unchecked(() -> Pages.read(query.getOffset(), query.getLimit(),
                (offset, limit) -> ContextNames.names(client.getContexts(null, offset, limit)))).stream());
        grid.setSizeFull();

        final Span description = new Span("Contexts group documents during redaction and provide features such as "
                + "referential integrity. ");
        description.add(PhilterDocs.link("Learn more about contexts.", "redaction/contexts.html"));

        final VerticalLayout mine = new VerticalLayout(description, grid);
        mine.setSizeFull();

        final TabSheet tabs = new TabSheet();
        tabs.add("My Contexts", mine);
        if (user.isAdministrator() && acrossUsersAllowed()) {
            tabs.add("All Contexts", allContexts());
        }
        tabs.setSizeFull();

        final Button create = new Button("New Context", VaadinIcon.RECORDS.create(), e -> openCreate());
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        create.setTooltipText("Create a new redaction context.");
        tabs.setSuffixComponent(create);

        add(new H2("Contexts"), tabs);

    }

    /**
     * Whether Philter lets this administrator list every user's contexts. It refuses with 404 unless
     * {@code ADMIN_CROSS_USER_ACCESS_ENABLED} is set.
     */
    private boolean acrossUsersAllowed() {
        try {
            client.getContextsAcrossUsers(0, 1);
            return true;
        } catch (final ClientException e) {
            return false;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private VerticalLayout allContexts() {

        final Grid<ContextNames.OwnedContext> all = new Grid<>();
        all.addColumn(ContextNames.OwnedContext::name).setHeader("Context").setResizable(true);
        all.addColumn(ContextNames.OwnedContext::owner).setHeader("Owner").setResizable(true);
        all.addComponentColumn(row -> button("View", VaadinIcon.DOCTOR_BRIEFCASE, "View context " + row.name(),
                () -> openView(row.name(), row.owner()))).setHeader("View").setAutoWidth(true).setFlexGrow(0);
        all.setItems(query -> unchecked(() -> Pages.read(query.getOffset(), query.getLimit(),
                (offset, limit) -> ContextNames.owned(client.getContextsAcrossUsers(offset, limit)))).stream());
        all.setSizeFull();

        final VerticalLayout layout = new VerticalLayout(new Span("All contexts across all users."), all);
        layout.setSizeFull();
        return layout;

    }

    /** Shows a context's settings and its entry counts by filter type. */
    private void openView(final String name, final String owner) {

        final ContextDetails details = details(name, owner);
        if (details == null) {
            return;
        }

        final Dialog dialog = new Dialog();
        dialog.setWidth("500px");
        dialog.add(new H3("Context"));
        dialog.add(new Paragraph("Filter type counts for context: " + name + (owner == null ? "" : " (owner: " + owner + ")")));
        dialog.add(new Paragraph("Entity type disambiguation is " + onOff(details.entityTypeDisambiguation())
                + ". The redaction ledger is " + onOff(details.ledger()) + "."));

        if (details.size() == 0) {
            dialog.add(new Paragraph("No entries found in this context."));
        } else {
            dialog.add(new Paragraph(details.size() == 1 ? "1 entry." : details.size() + " entries."));
            final Grid<ContextDetails.Count> counts = new Grid<>();
            counts.addColumn(ContextDetails.Count::label).setHeader("Filter Type").setSortable(true);
            counts.addColumn(ContextDetails.Count::count).setHeader("Count").setSortable(true);
            counts.setItems(details.counts());
            counts.setAllRowsVisible(true);
            dialog.add(counts);
        }

        final Button close = new Button("Close", e -> dialog.close());
        close.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(close);
        dialog.open();

    }

    private void openCreate() {

        final TextField name = new TextField("Context");
        name.setWidthFull();
        name.setRequired(true);
        final Checkbox disambiguation = new Checkbox("Enable entity type disambiguation for this context.");
        final Checkbox ledger = new Checkbox("Enable the redaction ledger for this context.");

        final Dialog dialog = settingsDialog("New Context", name, disambiguation, ledger);

        final Button save = new Button("Save", e -> {
            try {
                client.createContext(name.getValue(), disambiguation.getValue(), ledger.getValue());
                dialog.close();
                grid.getDataProvider().refreshAll();
                Notifications.success("Context created.");
            } catch (final ClientException ex) {
                name.setErrorMessage(createFailure(ex));
                name.setInvalid(true);
            } catch (final IOException | ServiceUnavailableException ex) {
                Notifications.failure(ex, "The context could not be created.");
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(cancel(dialog), save);
        dialog.open();

    }

    /** Edits a context's settings, starting from the values Philter reports, and sends only those changed. */
    private void openEdit(final String name) {

        final ContextDetails details = details(name, null);
        if (details == null) {
            return;
        }

        final TextField nameField = new TextField("Context");
        nameField.setWidthFull();
        nameField.setValue(name);
        nameField.setReadOnly(true);
        final Checkbox disambiguation = new Checkbox("Enable entity type disambiguation for this context.",
                details.entityTypeDisambiguation());
        final Checkbox ledger = new Checkbox("Enable the redaction ledger for this context.", details.ledger());
        ledger.addValueChangeListener(e -> ledger.setHelperText(details.ledger() && !e.getValue()
                ? "Turning the ledger off stops recording redaction evidence for this context." : null));

        final Dialog dialog = settingsDialog("Edit Context", nameField, disambiguation, ledger);

        final Button save = new Button("Save", e -> {
            final Boolean newDisambiguation = disambiguation.getValue() == details.entityTypeDisambiguation()
                    ? null : disambiguation.getValue();
            final Boolean newLedger = ledger.getValue() == details.ledger() ? null : ledger.getValue();
            if (newDisambiguation == null && newLedger == null) {
                dialog.close();
                return;
            }
            try {
                client.updateContext(name, newDisambiguation, newLedger);
                dialog.close();
                Notifications.success("Context updated.");
            } catch (final ClientException | ServiceUnavailableException | IOException ex) {
                Notifications.failure(ex, "The context could not be updated.");
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(cancel(dialog), save);
        dialog.open();

    }

    private void openClear(final String name) {
        confirm("Confirm Clear", "Are you sure you want to clear the " + name + " context? This removes all of its "
                + "entries and cannot be undone.", "Clear", () -> {
            client.deleteContextEntries(name);
            Notifications.success("Context cleared.");
        });
    }

    private void openDelete(final String name) {
        confirm("Confirm Deletion", "Are you sure you want to delete the context " + name + "? This cannot be undone.",
                "Delete", () -> {
                    client.deleteContext(name);
                    grid.getDataProvider().refreshAll();
                    Notifications.success("Context deleted.");
                });
    }

    private interface PhilterCall {
        void run() throws IOException;
    }

    private interface PhilterQuery<T> {
        T get() throws IOException;
    }

    private void confirm(final String title, final String text, final String action, final PhilterCall call) {

        final Dialog dialog = new Dialog();
        dialog.add(new H3(title), new Paragraph(text));

        final Button confirm = new Button(action, e -> {
            try {
                call.run();
                dialog.close();
            } catch (final ClientException | ServiceUnavailableException | IOException ex) {
                Notifications.failure(ex, "The " + action.toLowerCase() + " request failed.");
            }
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        dialog.getFooter().add(cancel(dialog), confirm);
        dialog.open();

    }

    /** The context's details, or {@code null} after telling the person why they could not be read. */
    private ContextDetails details(final String name, final String owner) {
        try {
            return ContextDetails.fromJson(client.getContext(name, owner));
        } catch (final ClientException | ServiceUnavailableException | IOException e) {
            Notifications.failure(e, "The context could not be read.");
            return null;
        }
    }

    private static Dialog settingsDialog(final String title, final TextField name, final Checkbox disambiguation,
                                         final Checkbox ledger) {
        final VerticalLayout fields = new VerticalLayout(name, disambiguation,
                PhilterDocs.link("Learn more about entity type disambiguation.", "redaction/contexts.html"),
                ledger, PhilterDocs.link("Learn more about the redaction ledger.", "redaction/ledgers.html"));
        final Dialog dialog = new Dialog();
        dialog.setWidth("500px");
        dialog.add(new H3(title), fields);
        return dialog;
    }

    private static Button button(final String text, final VaadinIcon icon, final String tooltip, final Runnable action) {
        final Button button = new Button(text, icon.create(), e -> action.run());
        button.setTooltipText(tooltip);
        button.setAriaLabel(tooltip);
        return button;
    }

    private static Button cancel(final Dialog dialog) {
        final Button cancel = new Button("Cancel", e -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        return cancel;
    }

    /** Why Philter refused to create a context, using its {@code reason} to tell the 409s apart. */
    static String createFailure(final ClientException e) {
        final String reason = PhilterErrors.reason(e);
        if ("context_limit_reached".equals(reason)) {
            return "You already have as many contexts as Philter allows. Delete one first.";
        }
        // Before Philter gave a reason, a 409 here could only mean a duplicate name.
        if ("context_exists".equals(reason) || (reason == null && PhilterErrors.hasStatus(e, 409))) {
            return "You already have a context with this name.";
        }
        return messageOr(e, "The context could not be created.");
    }

    private static String messageOr(final ClientException e, final String fallback) {
        final String message = PhilterErrors.message(e);
        return message == null ? fallback : message;
    }

    private static String onOff(final boolean value) {
        return value ? "on" : "off";
    }

    private static <T> T unchecked(final PhilterQuery<T> query) {
        try {
            return query.get();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
