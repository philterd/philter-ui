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
import ai.philterd.philter.model.ContextDetails;
import ai.philterd.philter.model.DisambiguationScope;
import ai.philterd.philter.model.GetContextsAcrossUsersResponse;
import ai.philterd.philter.model.GetContextsResponse;
import ai.philterd.philter.model.OwnedName;
import ai.philterd.philter.model.exceptions.ClientException;
import ai.philterd.philter.model.exceptions.ErrorReasons;
import ai.philterd.philter.model.exceptions.ServiceUnavailableException;
import ai.philterd.ui.model.ContextCounts;
import ai.philterd.ui.model.Listing;
import ai.philterd.ui.model.PhilterRules;
import ai.philterd.ui.security.PhilterClients;
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
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.io.IOException;
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
    private final Button create = new Button("New Context", VaadinIcon.RECORDS.create(), e -> openCreate());

    public ContextsView(final PhilterClients clients) {

        final PhilterUser user = Sessions.currentUser().orElseThrow();
        this.client = clients.forUser(user);

        setSizeFull();

        grid.addColumn(name -> name).setHeader("Context").setResizable(true).setSortProperty("name");
        grid.addComponentColumn(name -> ViewSupport.button("View", VaadinIcon.DOCTOR_BRIEFCASE, "View context " + name,
                () -> openView(name, null))).setHeader("View").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(name -> ViewSupport.button(null, VaadinIcon.EDIT, "Edit context " + name,
                () -> openEdit(name))).setHeader("Edit").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(name -> ViewSupport.button(null, VaadinIcon.RECYCLE, "Clear context " + name,
                () -> openClear(name))).setHeader("Clear").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(name -> ViewSupport.button(null, VaadinIcon.TRASH, "Delete context " + name,
                () -> openDelete(name))).setHeader("Delete").setAutoWidth(true).setFlexGrow(0);
        final ListingProvider<String> listing = ListingProvider.bind(grid, options -> {
            final GetContextsResponse page = client.listContexts(null, options);
            return new Listing<>(page.getContexts(), page.getTotal());
        });
        grid.setSizeFull();

        final Span description = new Span("Contexts group documents during redaction and provide features such as "
                + "referential integrity. ");
        description.add(PhilterDocs.link("Learn more about contexts.", "redaction/contexts.html"));

        final VerticalLayout mine = new VerticalLayout(description,
                listing.toolbar(grid, "Search by name", "context", "contexts"), grid);
        mine.setSizeFull();

        final TabSheet tabs = new TabSheet();
        tabs.add("My Contexts", mine);
        if (ViewSupport.showAllUsers(client)) {
            tabs.add("All Contexts", allContexts());
        }
        tabs.setSizeFull();

        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        showCreate(ViewSupport.rules(client));
        tabs.setSuffixComponent(create);

        add(new H2("Contexts"), tabs);

    }

    /** Enables New Context unless the person already has as many contexts as Philter allows, and says why. */
    private void showCreate(final PhilterRules rules) {
        final String blocked = createBlocked(rules);
        create.setEnabled(blocked == null);
        create.setTooltipText(blocked == null ? "Create a new redaction context." : blocked);
    }

    /** Why a new context cannot be created, or {@code null} when it can or the limit is not known. */
    static String createBlocked(final PhilterRules rules) {
        if (!rules.isContextLimitReached()) {
            return null;
        }
        final int limit = rules.contextLimit();
        return "You have " + limit + (limit == 1 ? " context" : " contexts")
                + ", the most Philter allows. Delete one to create another.";
    }

    private VerticalLayout allContexts() {

        final Grid<OwnedName> all = new Grid<>();
        all.addColumn(OwnedName::getName).setHeader("Context").setResizable(true).setSortProperty("name");
        all.addColumn(OwnedName::getOwner).setHeader("Owner").setResizable(true);
        all.addComponentColumn(row -> ViewSupport.button("View", VaadinIcon.DOCTOR_BRIEFCASE, "View context " + row.getName(),
                () -> openView(row.getName(), row.getOwner()))).setHeader("View").setAutoWidth(true).setFlexGrow(0);
        final ListingProvider<OwnedName> listing = ListingProvider.bind(all, options -> {
            final GetContextsAcrossUsersResponse page = client.listContextsAcrossUsers(options);
            return new Listing<>(page.getContexts(), page.getTotal());
        });
        all.setSizeFull();

        final VerticalLayout layout = new VerticalLayout(new Span("All contexts across all users."),
                listing.toolbar(all, "Search by name", "context", "contexts"), all);
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
        dialog.add(new Paragraph(settingsSummary(details)));

        if (details.getSize() == 0) {
            dialog.add(new Paragraph("No entries found in this context."));
        } else {
            dialog.add(new Paragraph(details.getSize() == 1 ? "1 entry." : details.getSize() + " entries."));
            final Grid<ContextCounts.Count> counts = new Grid<>();
            counts.addColumn(ContextCounts.Count::label).setHeader("Filter Type").setSortable(true);
            counts.addColumn(ContextCounts.Count::count).setHeader("Count").setSortable(true);
            counts.setItems(ContextCounts.of(details));
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
        final Select<String> scope = scopeSelect(DisambiguationScope.DOCUMENT, disambiguation);
        final Checkbox ledger = new Checkbox("Enable the redaction ledger for this context.");

        final Dialog dialog = settingsDialog("New Context", name, disambiguation, scope, ledger);

        final Button save = new Button("Save", e -> {
            try {
                client.createContext(name.getValue(), disambiguation.getValue(),
                        createScope(disambiguation.getValue(), scope.getValue()), ledger.getValue(), null);
                dialog.close();
                grid.getDataProvider().refreshAll();
                showCreate(ViewSupport.rereadRules(client));
                Notifications.success("Context created.");
            } catch (final ClientException ex) {
                name.setErrorMessage(createFailure(ex));
                name.setInvalid(true);
            } catch (final IOException | ServiceUnavailableException ex) {
                Notifications.failure(ex, "The context could not be created.");
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(ViewSupport.cancel(dialog), save);
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
                details.isEntityTypeDisambiguation());
        final Select<String> scope = scopeSelect(currentScope(details), disambiguation);
        final Checkbox ledger = new Checkbox("Enable the redaction ledger for this context.", details.isLedger());
        ledger.addValueChangeListener(e -> ledger.setHelperText(details.isLedger() && !e.getValue()
                ? "Turning the ledger off stops recording redaction evidence for this context." : null));

        final Dialog dialog = settingsDialog("Edit Context", nameField, disambiguation, scope, ledger);

        final Button save = new Button("Save", e -> {
            final Boolean newDisambiguation = disambiguation.getValue() == details.isEntityTypeDisambiguation()
                    ? null : disambiguation.getValue();
            final String newScope = scopeChange(details, scope.getValue());
            final Boolean newLedger = ledger.getValue() == details.isLedger() ? null : ledger.getValue();
            if (newDisambiguation == null && newScope == null && newLedger == null) {
                dialog.close();
                return;
            }
            try {
                client.updateContext(name, newDisambiguation, newScope, newLedger, null);
                dialog.close();
                Notifications.success("Context updated.");
            } catch (final ClientException | ServiceUnavailableException | IOException ex) {
                Notifications.failure(ex, "The context could not be updated.");
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(ViewSupport.cancel(dialog), save);
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
                    showCreate(ViewSupport.rereadRules(client));
                    Notifications.success("Context deleted.");
                });
    }

    private interface PhilterCall {
        void run() throws IOException;
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
        dialog.getFooter().add(ViewSupport.cancel(dialog), confirm);
        dialog.open();

    }

    /** The context's details, or {@code null} after telling the person why they could not be read. */
    private ContextDetails details(final String name, final String owner) {
        try {
            return client.getContextDetails(name, owner);
        } catch (final ClientException | ServiceUnavailableException | IOException e) {
            Notifications.failure(e, "The context could not be read.");
            return null;
        }
    }

    private static Dialog settingsDialog(final String title, final TextField name, final Checkbox disambiguation,
                                         final Select<String> scope, final Checkbox ledger) {
        final VerticalLayout fields = new VerticalLayout(name, disambiguation, scope,
                PhilterDocs.link("Learn more about entity type disambiguation.", "redaction/contexts.html"),
                ledger, PhilterDocs.link("Learn more about the redaction ledger.", "redaction/ledgers.html"));
        final Dialog dialog = new Dialog();
        dialog.setWidth("500px");
        dialog.add(new H3(title), fields);
        return dialog;
    }

    /** Why Philter refused to create a context, using its {@code reason} to tell the 409s apart. */
    static String createFailure(final ClientException e) {
        final String reason = e.getReason();
        if (ErrorReasons.CONTEXT_LIMIT_REACHED.equals(reason)) {
            return "You already have as many contexts as Philter allows. Delete one first.";
        }
        if (ErrorReasons.CONTEXT_EXISTS.equals(reason)) {
            return "You already have a context with this name.";
        }
        return messageOr(e, "The context could not be created.");
    }

    private static String messageOr(final ClientException e, final String fallback) {
        return e.getErrorMessage() == null ? fallback : e.getErrorMessage();
    }

    /** The disambiguation scopes Philter accepts, in the order they are offered. */
    static final List<String> SCOPES = List.of(DisambiguationScope.DOCUMENT, DisambiguationScope.CONTEXT);

    /**
     * A selector for the disambiguation scope, starting at {@code initial}. It is shown only while entity type
     * disambiguation is on, since the scope has no effect otherwise.
     */
    private static Select<String> scopeSelect(final String initial, final Checkbox disambiguation) {
        final Select<String> scope = new Select<>();
        scope.setLabel("Disambiguation scope");
        scope.setItems(SCOPES);
        scope.setItemLabelGenerator(ContextsView::scopeLabel);
        scope.setValue(initial);
        scope.setWidthFull();
        scope.setVisible(disambiguation.getValue());
        disambiguation.addValueChangeListener(e -> scope.setVisible(e.getValue()));
        return scope;
    }

    /** How a scope is described to a person. */
    static String scopeLabel(final String scope) {
        if (DisambiguationScope.CONTEXT.equalsIgnoreCase(scope)) {
            return "Across the context: remember what is learned for later documents";
        }
        return "Each document on its own";
    }

    /** The context's scope, which Philter reports as document when none was set. */
    static String currentScope(final ContextDetails details) {
        final String scope = details.getDisambiguationScope();
        return DisambiguationScope.CONTEXT.equalsIgnoreCase(scope) ? DisambiguationScope.CONTEXT : DisambiguationScope.DOCUMENT;
    }

    /** The scope to send when creating a context: only when disambiguation is on, since it means nothing otherwise. */
    static String createScope(final boolean disambiguation, final String scope) {
        return disambiguation ? scope : null;
    }

    /** The scope to send when editing a context: {@code null} to keep it, when the person did not change it. */
    static String scopeChange(final ContextDetails details, final String selected) {
        return selected == null || selected.equals(currentScope(details)) ? null : selected;
    }

    /** The context's settings, in words, for the View dialog. */
    static String settingsSummary(final ContextDetails details) {
        final String disambiguation = details.isEntityTypeDisambiguation()
                ? "Entity type disambiguation is on, " + (DisambiguationScope.CONTEXT.equals(currentScope(details))
                        ? "across the context: what it learns is remembered for later documents."
                        : "for each document on its own.")
                : "Entity type disambiguation is off.";
        return disambiguation + " The redaction ledger is " + onOff(details.isLedger()) + ".";
    }

    private static String onOff(final boolean value) {
        return value ? "on" : "off";
    }

}
