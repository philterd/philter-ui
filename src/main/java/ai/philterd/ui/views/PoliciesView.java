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
import ai.philterd.philter.model.ManagedPolicySummary;
import ai.philterd.philter.model.OwnedName;
import ai.philterd.philter.model.PolicyDetails;
import ai.philterd.philter.model.exceptions.ClientException;
import ai.philterd.philter.model.exceptions.ServiceUnavailableException;
import ai.philterd.ui.model.Pages;
import ai.philterd.ui.model.PolicyJson;
import ai.philterd.ui.model.PolicyNames;
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

    /** Philter's limits on a policy's description and notes. */
    static final int MAXIMUM_DESCRIPTION_LENGTH = 200;
    static final int MAXIMUM_NOTES_LENGTH = 1000;

    static final String POLICY_EDITOR = "https://policies.philterd.ai/";

    /** The policy every user has, which Philter does not let anyone delete. */
    static final String DEFAULT_POLICY = "default";

    private final transient PhilterClient client;
    private final Grid<String> grid = new Grid<>();
    private final String policyEditorUrl;

    public PoliciesView(final PhilterClients clients) {

        final PhilterUser user = Sessions.currentUser().orElseThrow();
        this.client = clients.forUser(user);
        this.policyEditorUrl = policyEditorUrl(schemaVersion());

        setSizeFull();

        grid.addColumn(name -> name).setHeader("Name").setResizable(true);
        grid.addComponentColumn(name -> ViewSupport.button("Edit", VaadinIcon.EDIT, "Edit policy " + name,
                () -> openEdit(name))).setHeader("Edit").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(name -> ViewSupport.button("Duplicate", VaadinIcon.COPY, "Duplicate policy " + name,
                () -> openCopy(name, "Duplicate Policy", "Enter a name for the copy of " + name + ".")))
                .setHeader("Duplicate").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::deleteButton).setHeader("Delete").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(name -> ViewSupport.button("History", VaadinIcon.CLOCK, "Version history of policy " + name,
                () -> new PolicyHistoryDialog(client, name, this::refresh).open()))
                .setHeader("History").setAutoWidth(true).setFlexGrow(0);
        // Philter's listing has no total, so the grid pages until a page comes back short.
        grid.setItems(query -> ViewSupport.unchecked(() -> Pages.read(query.getOffset(), query.getLimit(),
                (offset, limit) -> ViewSupport.orEmpty(client.getPolicies(null, offset, limit)))).stream());
        grid.setSizeFull();

        final Span description = new Span("A redaction policy defines which types of sensitive information Philter "
                + "detects and how each is redacted. ");
        description.add(PhilterDocs.link("Learn more about redaction policies.", "policies/filter_policies.html"));
        final VerticalLayout mine = new VerticalLayout(description, grid);
        mine.setSizeFull();

        final TabSheet tabs = new TabSheet();
        tabs.add("My Policies", mine);
        tabs.add("Managed Policies", managedPolicies());
        if (ViewSupport.showAllUsers(user, client)) {
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
    }

    private Button deleteButton(final String name) {
        final Button delete = ViewSupport.button("Delete", VaadinIcon.TRASH, "Delete policy " + name,
                () -> openDelete(name));
        delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
        if (DEFAULT_POLICY.equals(name)) {
            delete.setEnabled(false);
            delete.setTooltipText("The default policy cannot be deleted.");
        }
        return delete;
    }

    private VerticalLayout managedPolicies() {

        final Grid<ManagedPolicySummary> managed = new Grid<>();
        managed.addColumn(ManagedPolicySummary::getName).setHeader("Name").setResizable(true).setAutoWidth(true);
        managed.addColumn(ManagedPolicySummary::getDescription).setHeader("Description").setResizable(true);
        managed.addComponentColumn(policy -> ViewSupport.button("View", VaadinIcon.FILE_TEXT,
                "View managed policy " + policy.getName(), () -> openView(policy.getName(), null)))
                .setHeader("View").setAutoWidth(true).setFlexGrow(0);
        managed.addComponentColumn(policy -> ViewSupport.button("Create Policy From", VaadinIcon.PLUS,
                "Create a policy from " + policy.getName(),
                () -> openCopy(policy.getName(), "Create Policy", "Enter a name for your new policy, copied from "
                        + policy.getName() + ".")))
                .setHeader("Create Policy").setAutoWidth(true).setFlexGrow(0);
        managed.setItems(query -> ViewSupport.unchecked(() -> Pages.read(query.getOffset(), query.getLimit(),
                (offset, limit) -> ViewSupport.orEmpty(client.listManagedPolicies(offset, limit)))).stream());
        managed.setSizeFull();

        final VerticalLayout layout = new VerticalLayout(new Span("Managed policies are ready-made policies for common "
                + "needs. You can use them as any other policy, but not change or delete them; copy one to make "
                + "your own version."), managed);
        layout.setSizeFull();
        return layout;

    }

    private VerticalLayout allPolicies() {

        final Grid<OwnedName> all = new Grid<>();
        all.addColumn(OwnedName::getName).setHeader("Policy").setResizable(true);
        all.addColumn(OwnedName::getOwner).setHeader("Owner").setResizable(true);
        all.addComponentColumn(policy -> ViewSupport.button("View", VaadinIcon.FILE_TEXT,
                "View policy " + policy.getName() + " of " + policy.getOwner(),
                () -> openView(policy.getName(), policy.getOwner())))
                .setHeader("View").setAutoWidth(true).setFlexGrow(0);
        all.setItems(query -> ViewSupport.unchecked(() -> Pages.read(query.getOffset(), query.getLimit(),
                (offset, limit) -> ViewSupport.orEmpty(client.getPoliciesAcrossUsers(offset, limit)))).stream());
        all.setSizeFull();

        final VerticalLayout layout = new VerticalLayout(
                new Span("All policies across all users, including users who have been deactivated."), all);
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

        final String json;
        final PolicyDetails details;
        try {
            json = client.getPolicy(name);
            details = client.getPolicyDetails(name);
        } catch (final ClientException | ServiceUnavailableException | IOException e) {
            Notifications.failure(e, "The policy could not be read.");
            refresh();
            return;
        }

        final PolicyForm form = new PolicyForm(name, true, PolicyJson.pretty(json),
                details.getDescription(), details.getNotes(), policyEditorUrl);
        final Dialog dialog = form.dialog("Edit Policy");
        // What Philter has now, so a second Save after the details failed does not replace it again.
        final String[] saved = {json};

        final Button save = new Button("Save", e -> {
            form.clearErrors();
            final String problem = PolicyJson.problem(form.json.getValue());
            if (problem != null) {
                form.jsonError(problem);
                return;
            }
            try {
                // Only replace the policy when its JSON changed, so a details-only edit adds no revision.
                if (!PolicyJson.sameJson(saved[0], form.json.getValue())) {
                    client.replacePolicy(name, form.json.getValue());
                    saved[0] = form.json.getValue();
                }
            } catch (final ClientException ex) {
                form.jsonError(replaceFailure(ex));
                return;
            } catch (final IOException | ServiceUnavailableException ex) {
                Notifications.failure(ex, "The policy could not be saved.");
                return;
            }
            if (saveDetails(name, details.getDescription(), details.getNotes(), form, "The policy was saved, but")) {
                dialog.close();
                refresh();
                Notifications.success("Policy updated.");
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(ViewSupport.cancel(dialog), save);
        dialog.open();

    }

    private void openCreate() {

        final PolicyForm form = new PolicyForm("", false, PolicyJson.template(), "", "", policyEditorUrl);
        final Dialog dialog = form.dialog("New Policy");

        final Button save = new Button("Save", e -> {
            form.clearErrors();
            final String name = form.name.getValue().trim();
            final String nameProblem = PolicyNames.problem(name);
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
                client.savePolicy(name, form.json.getValue());
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
            // Create does not take a description or notes; Philter sets them through the details call.
            final boolean detailsSaved = saveDetails(name, "", "", form, "The policy was created, but");
            dialog.close();
            refresh();
            if (detailsSaved) {
                Notifications.success("Policy created.");
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(ViewSupport.cancel(dialog), save);
        dialog.open();

    }

    /**
     * Saves the description and notes if they changed. Reports a failure and returns {@code false}, so
     * the caller can keep the dialog open, unless the policy itself was just created.
     */
    private boolean saveDetails(final String name, final String description, final String notes,
                                final PolicyForm form, final String failurePrefix) {
        final String newDescription = ViewSupport.change(description, form.description.getValue());
        final String newNotes = ViewSupport.change(notes, form.notes.getValue());
        if (newDescription == null && newNotes == null) {
            return true;
        }
        try {
            client.setPolicyDetails(name, newDescription, newNotes);
            return true;
        } catch (final ClientException | ServiceUnavailableException | IOException e) {
            final String reason = e instanceof ClientException refused && refused.getErrorMessage() != null
                    ? refused.getErrorMessage() : "Philter could not be reached.";
            Notifications.failure(failurePrefix + " its description and notes were not: " + reason);
            return false;
        }
    }

    /** Copies a policy, the person's own or a managed one, to a new name. */
    private void openCopy(final String source, final String title, final String explanation) {

        final TextField name = new TextField("New policy name");
        name.setWidthFull();
        name.setRequired(true);
        name.setMaxLength(PolicyNames.MAXIMUM_LENGTH);
        name.setHelperText(PolicyNames.RULE);

        final Dialog dialog = new Dialog();
        dialog.setWidth("450px");
        dialog.add(new H3(title), new Paragraph(explanation), name);

        final Button copy = new Button(title.startsWith("Duplicate") ? "Duplicate" : "Create Policy", e -> {
            name.setInvalid(false);
            final String problem = PolicyNames.problem(name.getValue().trim());
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
                   final String policyNotes, final String policyEditorUrl) {
            name.setWidthFull();
            name.setValue(policyName);
            name.setReadOnly(existing);
            name.setRequired(!existing);
            name.setMaxLength(PolicyNames.MAXIMUM_LENGTH);
            if (!existing) {
                name.setHelperText(PolicyNames.RULE);
            }
            description.setWidthFull();
            description.setMaxLength(MAXIMUM_DESCRIPTION_LENGTH);
            description.setPlaceholder("Optional description of the policy");
            description.setValue(policyDescription == null ? "" : policyDescription);
            notes.setWidthFull();
            notes.setHeight("150px");
            notes.setMaxLength(MAXIMUM_NOTES_LENGTH);
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
