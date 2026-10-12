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
import ai.philterd.philter.model.DeletedPolicy;
import ai.philterd.philter.model.GetDeletedPoliciesResponse;
import ai.philterd.philter.model.ManagedPolicySummary;
import ai.philterd.philter.model.OwnedName;
import ai.philterd.philter.model.PolicyDetails;
import ai.philterd.philter.model.PolicyWithETag;
import ai.philterd.philter.model.exceptions.ClientException;
import ai.philterd.philter.model.exceptions.ServiceUnavailableException;
import ai.philterd.ui.model.Listing;
import ai.philterd.ui.model.PolicyJson;
import ai.philterd.ui.model.PhilterRules;
import ai.philterd.ui.security.PhilterClients;
import ai.philterd.ui.security.PhilterUser;
import ai.philterd.ui.security.Roles;
import ai.philterd.ui.security.Sessions;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * The person's redaction policies, through Philter's Policies API: editing, creating, copying, deleting,
 * and version history. Also lists Philter's managed policies and, for administrators when Philter allows
 * cross-user access, every user's policies.
 */
@Route(value = "policies", layout = MainLayout.class)
@PageTitle("Redaction Policies | Philter UI")
@RolesAllowed(Roles.USER)
public class PoliciesView extends VerticalLayout {

    static final String POLICY_EDITOR = "https://policies.philterd.ai/";

    private final transient PhilterClient client;
    private final PhilterRules rules;
    private final Grid<String> grid = new Grid<>();
    private final Grid<DeletedPolicy> deleted = new Grid<>();
    private final String policyEditorUrl;

    public PoliciesView(final PhilterClients clients) {

        final PhilterUser user = Sessions.currentUser().orElseThrow();
        this.client = clients.forUser(user);
        this.rules = ViewSupport.rules(client);
        this.policyEditorUrl = policyEditorUrl(schemaVersion());

        setSizeFull();

        grid.addColumn(name -> name).setHeader("Name").setResizable(true).setSortProperty("name");
        grid.addComponentColumn(name -> ViewSupport.button("Edit", VaadinIcon.EDIT, "Edit policy " + name,
                () -> openEdit(name))).setHeader("Edit").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(name -> ViewSupport.button("Duplicate", VaadinIcon.COPY, "Duplicate policy " + name,
                () -> openCopy(name, "Duplicate Policy", "Enter a name for the copy of " + name + ".")))
                .setHeader("Duplicate").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::deleteButton).setHeader("Delete").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(name -> ViewSupport.button("History", VaadinIcon.CLOCK, "Version history of policy " + name,
                () -> new PolicyHistoryDialog(client, name, false, this::refresh).open()))
                .setHeader("History").setAutoWidth(true).setFlexGrow(0);
        final ListingProvider<String> listing = ListingProvider.bind(grid,
                options -> Listing.of(client.listPolicies(null, options)));
        grid.setSizeFull();

        final Span description = new Span("A redaction policy defines which types of sensitive information Philter "
                + "detects and how each is redacted. ");
        description.add(PhilterDocs.link("Learn more about redaction policies.", "policies/filter_policies.html"));
        final VerticalLayout mine = new VerticalLayout(description,
                listing.toolbar(grid, "Search by name", "policy", "policies"), grid);
        mine.setSizeFull();

        final TabSheet tabs = new TabSheet();
        tabs.add("My Policies", mine);
        tabs.add("Managed Policies", managedPolicies());
        tabs.add("Deleted Policies", deletedPolicies());
        if (ViewSupport.showAllUsers(client)) {
            tabs.add("All Policies", allPolicies());
        }
        tabs.setSizeFull();

        final Button create = new Button("New Policy", VaadinIcon.PLUS.create(), e -> openCreate());
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        create.setTooltipText("Create a new redaction policy.");
        tabs.setSuffixComponent(create);

        add(new H2("Redaction Policies"), tabs);

    }

    private void refresh() {
        grid.getDataProvider().refreshAll();
        deleted.getDataProvider().refreshAll();
    }

    private Button deleteButton(final String name) {
        final Button delete = ViewSupport.button("Delete", VaadinIcon.TRASH, "Delete policy " + name,
                () -> openDelete(name));
        delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
        if (rules.isDefaultPolicy(name)) {
            delete.setEnabled(false);
            delete.setTooltipText("The default policy cannot be deleted.");
        }
        return delete;
    }

    private VerticalLayout managedPolicies() {

        final Grid<ManagedPolicySummary> managed = new Grid<>();
        managed.addColumn(ManagedPolicySummary::getName).setHeader("Name").setResizable(true).setAutoWidth(true)
                .setSortProperty("name");
        managed.addColumn(ManagedPolicySummary::getDescription).setHeader("Description").setResizable(true);
        managed.addComponentColumn(policy -> ViewSupport.button("View", VaadinIcon.FILE_TEXT,
                "View managed policy " + policy.getName(), () -> openView(policy.getName(), null)))
                .setHeader("View").setAutoWidth(true).setFlexGrow(0);
        managed.addComponentColumn(policy -> ViewSupport.button("Create Policy From", VaadinIcon.PLUS,
                "Create a policy from " + policy.getName(),
                () -> openCopy(policy.getName(), "Create Policy", "Enter a name for your new policy, copied from "
                        + policy.getName() + ".")))
                .setHeader("Create Policy").setAutoWidth(true).setFlexGrow(0);
        final ListingProvider<ManagedPolicySummary> listing = ListingProvider.bind(managed,
                options -> Listing.of(client.listManagedPolicies(options)));
        managed.setSizeFull();

        final VerticalLayout layout = new VerticalLayout(new Span("Managed policies are ready-made policies for common "
                + "needs. You can use them as any other policy, but not change or delete them; copy one to make "
                + "your own version."), listing.toolbar(managed, "Search by name", "managed policy", "managed policies"),
                managed);
        layout.setSizeFull();
        return layout;

    }

    /** The person's deleted policies whose history Philter keeps: browse it, or restore one. */
    private VerticalLayout deletedPolicies() {

        deleted.addColumn(DeletedPolicy::getName).setHeader("Name").setResizable(true).setSortProperty("name");
        deleted.addColumn(DeletedPolicy::getLatestRevision).setHeader("Latest Revision").setAutoWidth(true).setFlexGrow(0);
        deleted.addColumn(PoliciesView::deletedAt).setHeader("Deleted").setAutoWidth(true);
        deleted.addColumn(PoliciesView::deletedBy).setHeader("Deleted By").setAutoWidth(true);
        deleted.addComponentColumn(policy -> ViewSupport.button("History", VaadinIcon.CLOCK,
                "Version history of deleted policy " + policy.getName(),
                () -> new PolicyHistoryDialog(client, policy.getName(), true, this::refresh).open()))
                .setHeader("History").setAutoWidth(true).setFlexGrow(0);
        deleted.addComponentColumn(policy -> ViewSupport.button("Restore", VaadinIcon.ARROW_BACKWARD,
                "Restore " + policy.getName() + " as it was when it was deleted",
                () -> PolicyHistoryDialog.openRestore(client, policy.getName(), policy.getLatestRevision(), this::refresh)))
                .setHeader("Restore").setAutoWidth(true).setFlexGrow(0);
        final ListingProvider<DeletedPolicy> listing = ListingProvider.bind(deleted, options -> {
            final GetDeletedPoliciesResponse page = client.getDeletedPolicies(null, options);
            return new Listing<>(page.getPolicies(), page.getTotal());
        });
        deleted.setSizeFull();

        final VerticalLayout layout = new VerticalLayout(new Span("Philter keeps a deleted policy's history. Browse "
                + "it, or restore the policy from any of its revisions under the same name."),
                listing.toolbar(deleted, "Search by name", "deleted policy", "deleted policies"), deleted);
        layout.setSizeFull();
        return layout;

    }

    /** When a policy was deleted, in UTC, or blank when Philter did not record it. */
    static String deletedAt(final DeletedPolicy policy) {
        return ViewSupport.utc(policy.getDeletedAtText());
    }

    /** Who deleted a policy, or blank when Philter did not record it. */
    static String deletedBy(final DeletedPolicy policy) {
        return policy.getDeletedBy() == null ? "" : policy.getDeletedBy();
    }

    private VerticalLayout allPolicies() {

        final Grid<OwnedName> all = new Grid<>();
        all.addColumn(OwnedName::getName).setHeader("Policy").setResizable(true).setSortProperty("name");
        all.addColumn(OwnedName::getOwner).setHeader("Owner").setResizable(true);
        all.addComponentColumn(policy -> ViewSupport.button("View", VaadinIcon.FILE_TEXT,
                "View policy " + policy.getName() + " of " + policy.getOwner(),
                () -> openView(policy.getName(), policy.getOwner())))
                .setHeader("View").setAutoWidth(true).setFlexGrow(0);
        final ListingProvider<OwnedName> listing = ListingProvider.bind(all,
                options -> Listing.of(client.listPoliciesAcrossUsers(options)));
        all.setSizeFull();

        final VerticalLayout layout = new VerticalLayout(
                new Span("All policies across all users, including users who have been deactivated."),
                listing.toolbar(all, "Search by name", "policy", "policies"), all);
        layout.setSizeFull();
        return layout;

    }

    /** Shows a policy's JSON, read-only. */
    private void openView(final String name, final String owner) {
        final String json;
        try {
            json = client.getPolicy(name, owner);
        } catch (final ClientException | ServiceUnavailableException | IOException e) {
            Notifications.failure(e, "The policy could not be read.");
            return;
        }
        showJson(name + (owner == null ? "" : " (owner: " + owner + ")"), json);
    }

    static void showJson(final String title, final String json) {
        final TextArea text = new TextArea(title);
        text.setWidthFull();
        text.setReadOnly(true);
        text.setValue(PolicyJson.pretty(json));
        final Dialog dialog = new Dialog(text);
        dialog.setWidth("700px");
        dialog.setCloseOnEsc(true);
        dialog.setCloseOnOutsideClick(true);
        final Button close = new Button("Close", e -> dialog.close());
        close.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(close);
        dialog.open();
    }

    private void openEdit(final String name) {

        final PolicyWithETag policy;
        final PolicyDetails details;
        try {
            // The policy's tag is read first, so a change made between the two reads makes the save refuse
            // rather than overwrite it.
            policy = client.getPolicyWithETag(name);
            details = client.getPolicyDetails(name);
        } catch (final ClientException | ServiceUnavailableException | IOException e) {
            Notifications.failure(e, "The policy could not be read.");
            refresh();
            return;
        }

        final PolicyForm form = new PolicyForm(name, true, PolicyJson.pretty(policy.getJson()),
                details.getDescription(), details.getNotes(), policyEditorUrl, rules);
        final Dialog dialog = form.dialog("Edit Policy");
        // The policy as this editor last read or wrote it, and its tag, sent with the next save.
        final Saved[] saved = {new Saved(policy.getJson(), details.getDescription(), details.getNotes(), policy.getETag())};

        final Button save = new Button("Save", e -> {
            form.clearErrors();
            final String problem = PolicyJson.problem(form.json.getValue());
            if (problem != null) {
                form.jsonError(problem);
                return;
            }
            try {
                saved[0] = saveEdit((json, description, notes, eTag) ->
                                client.replacePolicy(name, json, description, notes, eTag, null),
                        saved[0], form.json.getValue(), form.description.getValue(), form.notes.getValue());
                dialog.close();
                refresh();
                Notifications.success("Policy updated.");
            } catch (final ClientException ex) {
                form.jsonError(replaceFailure(ex));
            } catch (final IOException | ServiceUnavailableException ex) {
                Notifications.failure(ex, "The policy could not be saved.");
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(ViewSupport.cancel(dialog), save);
        dialog.open();

    }

    /** The Philter template every new policy starts from. */
    static final String TEMPLATE = "default";

    /** Shown when the editor opens without Philter's template. */
    static final String TEMPLATE_UNAVAILABLE = "Philter's policy template could not be loaded, so the new policy "
            + "starts empty. Build it in the policy editor or add identifiers here.";

    /** Reads a policy template from Philter, so starting a new policy can be tested without Philter. */
    interface TemplateSource {
        String read(String templateName) throws IOException;
    }

    /** The JSON a new policy starts from, and why it is not Philter's template, if it is not. */
    record NewPolicy(String json, String problem) {
    }

    /**
     * Starts a new policy from Philter's {@link #TEMPLATE}, read now so it follows Philter's policy schema. When it
     * cannot be read, the policy starts from {@link PolicyJson#EMPTY} and {@code problem} says so.
     */
    static NewPolicy newPolicy(final TemplateSource source) {
        try {
            final String template = source.read(TEMPLATE);
            if (template != null && PolicyJson.problem(template) == null) {
                return new NewPolicy(PolicyJson.pretty(template), null);
            }
        } catch (final ClientException | ServiceUnavailableException | IOException e) {
            // Fall through to the empty policy.
        }
        return new NewPolicy(PolicyJson.EMPTY, TEMPLATE_UNAVAILABLE);
    }

    private void openCreate() {

        final NewPolicy start = newPolicy(client::getPolicyTemplate);
        final PolicyForm form = new PolicyForm("", false, start.json(), "", "", policyEditorUrl, rules);
        final Dialog dialog = form.dialog("New Policy");
        if (start.problem() != null) {
            Notifications.warning(start.problem());
        }

        final Button save = new Button("Save", e -> {
            form.clearErrors();
            final String name = form.name.getValue().trim();
            final String nameProblem = rules.policyNameProblem(name);
            if (nameProblem != null) {
                form.nameError(nameProblem);
                return;
            }
            final String problem = PolicyJson.problem(form.json.getValue());
            if (problem != null) {
                form.jsonError(problem);
                return;
            }
            try {
                // The policy, description, and notes are created in one request, so a refusal creates nothing.
                client.savePolicy(name, form.json.getValue(), ViewSupport.change("", form.description.getValue()),
                        ViewSupport.change("", form.notes.getValue()));
            } catch (final ClientException ex) {
                if ("policy_exists".equals(ex.getReason())) {
                    form.nameError("You already have a policy with this name.");
                } else {
                    form.jsonError(messageOr(ex, "Philter did not accept the policy."));
                }
                return;
            } catch (final IOException | ServiceUnavailableException ex) {
                Notifications.failure(ex, "The policy could not be created.");
                return;
            }
            dialog.close();
            refresh();
            Notifications.success("Policy created.");
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(ViewSupport.cancel(dialog), save);
        dialog.open();

    }

    /** A policy as an editor last read or wrote it: its JSON, description, notes, and entity tag. */
    record Saved(String json, String description, String notes, String eTag) {
    }

    /** Replaces a policy and its details in one request, refused when the policy changed since {@code eTag}. */
    interface PolicyWriter {
        String replace(String json, String description, String notes, String eTag) throws IOException;
    }

    /**
     * Saves an edit as one request, only if the policy is still as the editor read it: the JSON, and the description
     * and notes when they changed, with the tag from {@code saved}. Nothing is sent when nothing changed. Returns the
     * policy as written, with the tag Philter returned, for the next save from the same editor.
     */
    static Saved saveEdit(final PolicyWriter writer, final Saved saved, final String json, final String description,
                          final String notes) throws IOException {
        final String newDescription = ViewSupport.change(saved.description(), description);
        final String newNotes = ViewSupport.change(saved.notes(), notes);
        if (newDescription == null && newNotes == null && PolicyJson.sameJson(saved.json(), json)) {
            return saved;
        }
        // Philter adds a revision only when the JSON changed, so a details-only edit adds none.
        final String eTag = writer.replace(json, newDescription, newNotes, saved.eTag());
        return new Saved(json, newDescription == null ? saved.description() : newDescription,
                newNotes == null ? saved.notes() : newNotes, eTag);
    }

    /** Copies a policy, the person's own or a managed one, to a new name. */
    private void openCopy(final String source, final String title, final String explanation) {

        final TextField name = new TextField("New policy name");
        name.setWidthFull();
        name.setRequired(true);
        if (rules.policyNameMaximumLength() != null) {
            name.setMaxLength(rules.policyNameMaximumLength());
        }
        name.setHelperText(rules.policyNameRule());

        final Dialog dialog = new Dialog();
        dialog.setWidth("450px");
        dialog.add(new H3(title), new Paragraph(explanation), name);

        final Button copy = new Button(title.startsWith("Duplicate") ? "Duplicate" : "Create Policy", e -> {
            name.setInvalid(false);
            final String problem = rules.policyNameProblem(name.getValue().trim());
            if (problem != null) {
                invalid(name, problem);
                return;
            }
            try {
                client.copyPolicy(source, name.getValue().trim());
                dialog.close();
                refresh();
                Notifications.success("Policy " + name.getValue().trim() + " created from " + source + ".");
            } catch (final ClientException ex) {
                invalid(name, copyFailure(ex));
            } catch (final IOException | ServiceUnavailableException ex) {
                Notifications.failure(ex, "The policy could not be copied.");
            }
        });
        copy.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(ViewSupport.cancel(dialog), copy);
        dialog.open();

    }

    private void openDelete(final String name) {

        final Dialog dialog = new Dialog();
        dialog.add(new H3("Confirm Deletion"), new Paragraph("Delete the policy " + name + "? It can no longer be "
                + "used for redaction, and this cannot be undone. Philter keeps its version history."));

        final Button delete = new Button("Delete", e -> {
            try {
                client.deletePolicy(name);
                dialog.close();
                refresh();
                Notifications.success("Policy deleted.");
            } catch (final ClientException | ServiceUnavailableException | IOException ex) {
                dialog.close();
                refresh();
                Notifications.failure(ex, "The policy could not be deleted.");
            }
        });
        delete.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        dialog.getFooter().add(ViewSupport.cancel(dialog), delete);
        dialog.open();

    }

    /** Why Philter refused to replace a policy, for the JSON field. */
    static String replaceFailure(final ClientException e) {
        if ("policy_changed".equals(e.getReason())) {
            return "The policy changed since you opened it. Cancel and edit it again to see the latest.";
        }
        if ("policy_managed".equals(e.getReason())) {
            return "Managed policies cannot be changed. Copy it and change the copy instead.";
        }
        return messageOr(e, "Philter did not accept the policy.");
    }

    /** Why Philter refused to copy a policy, for the name field. */
    static String copyFailure(final ClientException e) {
        if ("policy_exists".equals(e.getReason())) {
            return "You already have a policy with this name.";
        }
        return messageOr(e, "The policy could not be copied.");
    }

    /** The policy editor, opened at the policy schema version Philter reports, when it reports one. */
    static String policyEditorUrl(final String schemaVersion) {
        if (schemaVersion == null || schemaVersion.isBlank()) {
            return POLICY_EDITOR;
        }
        return POLICY_EDITOR + "?version=" + URLEncoder.encode(schemaVersion, StandardCharsets.UTF_8);
    }

    private String schemaVersion() {
        try {
            return client.health().getRedactionPolicySchemaVersion();
        } catch (final ClientException | ServiceUnavailableException | IOException e) {
            return null;
        }
    }

    private static String messageOr(final ClientException e, final String fallback) {
        return e.getErrorMessage() == null ? fallback : e.getErrorMessage();
    }

    private static void invalid(final TextField field, final String message) {
        field.setErrorMessage(message);
        field.setInvalid(true);
    }

    /** The fields of the new and edit policy dialogs. */
    private static final class PolicyForm {

        private final TextField name = new TextField("Policy name");
        private final TextField description = new TextField("Description");
        private final TextArea notes = new TextArea("Notes");
        private final TextArea json = new TextArea("Policy (JSON)");
        private final TabSheet tabs = new TabSheet();

        PolicyForm(final String policyName, final boolean existing, final String policyJson, final String policyDescription,
                   final String policyNotes, final String policyEditorUrl, final PhilterRules rules) {
            name.setWidthFull();
            name.setValue(policyName);
            name.setReadOnly(existing);
            name.setRequired(!existing);
            if (rules.policyNameMaximumLength() != null) {
                name.setMaxLength(rules.policyNameMaximumLength());
            }
            if (!existing) {
                name.setHelperText(rules.policyNameRule());
            }
            description.setWidthFull();
            if (rules.policyDescriptionMaximumLength() != null) {
                description.setMaxLength(rules.policyDescriptionMaximumLength());
            }
            description.setPlaceholder("Optional description of the policy");
            description.setValue(policyDescription == null ? "" : policyDescription);
            notes.setWidthFull();
            notes.setHeight("150px");
            if (rules.policyNotesMaximumLength() != null) {
                notes.setMaxLength(rules.policyNotesMaximumLength());
            }
            notes.setPlaceholder("Optional notes about the policy");
            notes.setValue(policyNotes == null ? "" : policyNotes);
            json.setWidthFull();
            json.setHeight("400px");
            json.setValue(policyJson);
            final Anchor editor = new Anchor(policyEditorUrl, "Build a policy in the policy editor, then paste the JSON here.");
            editor.setTarget("_blank");
            json.setHelperComponent(editor);
            tabs.add("Policy (JSON)", new VerticalLayout(json));
            tabs.add("Description and Notes", new VerticalLayout(description, notes));
            tabs.setWidthFull();
        }

        Dialog dialog(final String title) {
            final Dialog dialog = new Dialog();
            dialog.setWidth("900px");
            dialog.add(new H3(title), name, tabs);
            return dialog;
        }

        void clearErrors() {
            name.setInvalid(false);
            json.setInvalid(false);
        }

        void nameError(final String message) {
            name.setErrorMessage(message);
            name.setInvalid(true);
        }

        void jsonError(final String message) {
            tabs.setSelectedIndex(0);
            json.setErrorMessage(message);
            json.setInvalid(true);
        }

    }

}
