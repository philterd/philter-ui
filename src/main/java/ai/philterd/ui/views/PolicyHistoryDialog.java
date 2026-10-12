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
import ai.philterd.philter.model.ListOptions;
import ai.philterd.philter.model.PolicyDiff;
import ai.philterd.philter.model.PolicyVersionSummary;
import ai.philterd.philter.model.exceptions.ClientException;
import ai.philterd.philter.model.exceptions.ServiceUnavailableException;
import com.google.gson.JsonElement;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.io.IOException;
import java.util.List;

/**
 * A policy's retained revisions, with who made each: view one or compare two, and roll back to one or, for a
 * deleted policy, restore it from one.
 */
final class PolicyHistoryDialog {

    private final PhilterClient client;
    private final String name;
    private final boolean deleted;
    private final Runnable onChange;

    /**
     * @param deleted  Whether the policy is deleted: its history is read the same way, but it is restored from a
     *                 revision rather than rolled back.
     * @param onChange Run after a rollback or restore, so the page can show the change.
     */
    PolicyHistoryDialog(final PhilterClient client, final String name, final boolean deleted, final Runnable onChange) {
        this.client = client;
        this.name = name;
        this.deleted = deleted;
        this.onChange = onChange;
    }

    void open() {

        final List<PolicyVersionSummary> versions;
        try {
            // As many as Philter returns in one page, newest first.
            versions = ViewSupport.orEmpty(client.listPolicyVersions(name, null,
                    new ListOptions().offset(0).limit(ViewSupport.rules(client).maximumPageSize())).getItems());
        } catch (final ClientException | ServiceUnavailableException | IOException e) {
            Notifications.failure(e, "The policy's history could not be read.");
            return;
        }
        final int latest = versions.isEmpty() ? -1 : versions.get(0).getRevision();

        final Dialog dialog = new Dialog();
        dialog.setWidth("900px");
        dialog.add(new H3("Version History: " + name + (deleted ? " (deleted)" : "")));

        if (versions.isEmpty()) {
            dialog.add(new Paragraph("No retained versions of this policy."));
        } else {
            final Grid<PolicyVersionSummary> grid = new Grid<>();
            grid.addColumn(PolicyVersionSummary::getRevision).setHeader("Revision").setAutoWidth(true).setFlexGrow(0);
            grid.addColumn(v -> ViewSupport.utc(v.getCapturedTimestamp())).setHeader("Captured").setAutoWidth(true);
            grid.addColumn(PolicyHistoryDialog::author).setHeader("Author").setAutoWidth(true);
            grid.addColumn(v -> shortHash(v.getContentHash())).setHeader("Hash").setAutoWidth(true);
            grid.addComponentColumn(v -> ViewSupport.button("View", VaadinIcon.EYE, "View revision " + v.getRevision(),
                    () -> openRevision(v.getRevision()))).setHeader("View").setAutoWidth(true).setFlexGrow(0);
            if (deleted) {
                grid.addComponentColumn(v -> ViewSupport.button("Restore", VaadinIcon.ARROW_BACKWARD,
                        "Restore " + name + " from revision " + v.getRevision(),
                        () -> openRestore(client, name, v.getRevision(), () -> {
                            dialog.close();
                            onChange.run();
                        }))).setHeader("Restore").setAutoWidth(true).setFlexGrow(0);
            } else {
                // A deleted policy has no current revision to roll back from, so it is restored instead.
                grid.addComponentColumn(v -> {
                    final Button rollback = ViewSupport.button("Roll Back", VaadinIcon.BACKWARDS,
                            "Roll back to revision " + v.getRevision(), () -> openRollback(v.getRevision(), dialog));
                    rollback.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
                    if (v.getRevision() == latest) {
                        rollback.setEnabled(false);
                        rollback.setTooltipText("This is the current revision.");
                    }
                    return rollback;
                }).setHeader("Roll Back").setAutoWidth(true).setFlexGrow(0);
            }
            grid.setItems(versions);
            grid.setAllRowsVisible(versions.size() <= 10);
            dialog.add(grid);
        }

        final Button compare = new Button("Compare Revisions", VaadinIcon.SPLIT.create(), e -> openCompare(versions));
        compare.setEnabled(versions.size() >= 2);
        final Button close = new Button("Close", e -> dialog.close());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getFooter().add(compare, close);
        dialog.open();

    }

    private void openRevision(final int revision) {
        try {
            PoliciesView.showJson("Revision " + revision + " of " + name, client.getPolicyVersion(name, revision));
        } catch (final ClientException | ServiceUnavailableException | IOException e) {
            Notifications.failure(e, "The revision could not be read.");
        }
    }

    private void openRollback(final int revision, final Dialog history) {

        final Dialog dialog = new Dialog();
        dialog.setWidth("450px");
        dialog.add(new H3("Confirm Rollback"), new Paragraph("Roll back " + name + " to revision " + revision
                + "? Its content becomes a new revision; no revision is removed. This is audited."));

        final Button rollback = new Button("Roll Back", e -> {
            try {
                final int created = client.rollbackPolicy(name, revision).getRevision();
                dialog.close();
                history.close();
                onChange.run();
                Notifications.success(name + " rolled back to revision " + revision + ", saved as revision " + created + ".");
            } catch (final ClientException ex) {
                dialog.close();
                Notifications.failure(rollbackFailure(ex));
            } catch (final IOException | ServiceUnavailableException ex) {
                Notifications.failure(ex, "The policy could not be rolled back.");
            }
        });
        rollback.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        dialog.getFooter().add(ViewSupport.cancel(dialog), rollback);
        dialog.open();

    }

    private void openCompare(final List<PolicyVersionSummary> versions) {

        final List<Integer> revisions = versions.stream().map(PolicyVersionSummary::getRevision).toList();
        final ComboBox<Integer> from = new ComboBox<>("From revision", revisions);
        final ComboBox<Integer> to = new ComboBox<>("To revision", revisions);
        from.setValue(revisions.get(1));
        to.setValue(revisions.get(0));
        final VerticalLayout result = new VerticalLayout();
        result.setPadding(false);

        final Button compare = new Button("Compare", VaadinIcon.EXCHANGE.create(), e -> {
            result.removeAll();
            if (from.getValue() == null || to.getValue() == null) {
                result.add(new Span("Choose two revisions to compare."));
                return;
            }
            try {
                result.add(differences(client.diffPolicy(name, from.getValue(), to.getValue())));
            } catch (final ClientException | ServiceUnavailableException | IOException ex) {
                Notifications.failure(ex, "The revisions could not be read.");
            }
        });
        compare.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        final HorizontalLayout choose = new HorizontalLayout(from, to, compare);
        choose.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);

        final Dialog dialog = new Dialog();
        dialog.setWidth("950px");
        dialog.add(new H3("Compare Revisions: " + name), choose, result);
        final Button close = new Button("Close", e -> dialog.close());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getFooter().add(close);
        dialog.open();
        compare.click();

    }

    private static Component differences(final PolicyDiff diff) {
        final List<Change> changes = changes(diff);
        if (changes.isEmpty()) {
            return new Span(sameContent(diff));
        }
        final Grid<Change> grid = new Grid<>();
        grid.addColumn(Change::operation).setHeader("Change").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(Change::path).setHeader("Path").setFlexGrow(2).setResizable(true);
        grid.addColumn(Change::before).setHeader("Before").setFlexGrow(1).setResizable(true);
        grid.addColumn(Change::after).setHeader("After").setFlexGrow(1).setResizable(true);
        grid.setItems(changes);
        grid.setAllRowsVisible(true);
        return grid;
    }

    /** One change between two revisions as shown: the operation, the path as Philter gives it, and the values. */
    record Change(String operation, String path, String before, String after) {
    }

    /**
     * The changes in Philter's diff, each with the value before it (for a replace or remove) and after it (for
     * an add or replace). Paths are shown as Philter returns them, escaped per RFC 6901.
     */
    static List<Change> changes(final PolicyDiff diff) {
        return ViewSupport.orEmpty(diff.getChanges()).stream()
                .map(change -> new Change(change.getOp(), change.getPath(),
                        PolicyDiff.Change.ADD.equals(change.getOp()) ? "" : text(change.getOldValue()),
                        PolicyDiff.Change.REMOVE.equals(change.getOp()) ? "" : text(change.getValue())))
                .toList();
    }

    static String sameContent(final PolicyDiff diff) {
        return "Revisions " + diff.getFrom() + " and " + diff.getTo() + " have the same content.";
    }

    private static String text(final JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return element == null ? "" : "null";
        }
        return element.isJsonPrimitive() ? element.getAsString() : element.toString();
    }

    /** Reads a revision of a deleted policy and saves a policy, so a restore can be tested without Philter. */
    interface Restorer {
        String read(String name, int revision) throws IOException;

        void save(String name, String json) throws IOException;
    }

    /**
     * Restores a deleted policy from one of its revisions: the revision's content is saved as a policy under the
     * same name, which continues its history at a new revision.
     */
    static void restore(final Restorer restorer, final String name, final int revision) throws IOException {
        restorer.save(name, restorer.read(name, revision));
    }

    /** Asks before restoring a deleted policy from {@code revision}, and runs {@code onRestored} once it is. */
    static void openRestore(final PhilterClient client, final String name, final int revision, final Runnable onRestored) {

        final Dialog dialog = new Dialog();
        dialog.setWidth("450px");
        dialog.add(new H3("Restore Policy"), new Paragraph("Restore " + name + " from revision " + revision
                + "? It is saved as your policy " + name + " again, at a new revision, and its history continues. "
                + "This is audited."));

        final Button restore = new Button("Restore", e -> {
            try {
                restore(new Restorer() {
                    @Override
                    public String read(final String policy, final int number) throws IOException {
                        return client.getPolicyVersion(policy, number);
                    }

                    @Override
                    public void save(final String policy, final String json) throws IOException {
                        client.savePolicy(policy, json);
                    }
                }, name, revision);
                dialog.close();
                onRestored.run();
                Notifications.success(name + " restored from revision " + revision + ".");
            } catch (final ClientException ex) {
                dialog.close();
                Notifications.failure(restoreFailure(name, ex));
            } catch (final IOException | ServiceUnavailableException ex) {
                Notifications.failure(ex, "The policy could not be restored.");
            }
        });
        restore.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(ViewSupport.cancel(dialog), restore);
        dialog.open();

    }

    /** Why Philter refused to restore a deleted policy. */
    static String restoreFailure(final String name, final ClientException e) {
        if ("policy_exists".equals(e.getReason())) {
            return "You already have a policy named " + name + ", so the deleted one cannot be restored under that "
                    + "name. Delete the current one first to restore it.";
        }
        return e.getErrorMessage() == null ? "The policy could not be restored." : e.getErrorMessage();
    }

    /** Who made a revision, or blank when Philter does not know. */
    static String author(final PolicyVersionSummary version) {
        return version.getAuthor() == null ? "" : version.getAuthor();
    }

    /** Why Philter refused a rollback. */
    static String rollbackFailure(final ClientException e) {
        if ("policy_changed".equals(e.getReason())) {
            return "The policy changed while it was being rolled back. Open its history again and retry.";
        }
        if ("policy_managed".equals(e.getReason())) {
            return "Managed policies cannot be rolled back.";
        }
        return e.getErrorMessage() == null ? "The policy could not be rolled back." : e.getErrorMessage();
    }

    static String shortHash(final String hash) {
        if (hash == null || hash.isEmpty()) {
            return "";
        }
        return hash.length() <= 8 ? hash : hash.substring(0, 8) + "…";
    }

}
