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
import ai.philterd.philter.model.PolicyVersionSummary;
import ai.philterd.philter.model.exceptions.ClientException;
import ai.philterd.philter.model.exceptions.ServiceUnavailableException;
import ai.philterd.ui.model.PolicyJson;
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

/** A policy's retained revisions: view one, roll back to one, or compare two. */
final class PolicyHistoryDialog {

    /** The most revisions Philter returns at once, newest first. */
    static final int REVISIONS = 100;

    private final PhilterClient client;
    private final String name;
    private final Runnable onRollback;

    PolicyHistoryDialog(final PhilterClient client, final String name, final Runnable onRollback) {
        this.client = client;
        this.name = name;
        this.onRollback = onRollback;
    }

    void open() {

        final List<PolicyVersionSummary> versions;
        try {
            versions = ViewSupport.orEmpty(client.getPolicyVersions(name, null, 0, REVISIONS));
        } catch (final ClientException | ServiceUnavailableException | IOException e) {
            Notifications.failure(e, "The policy's history could not be read.");
            return;
        }
        final int latest = versions.isEmpty() ? -1 : versions.get(0).getRevision();

        final Dialog dialog = new Dialog();
        dialog.setWidth("900px");
        dialog.add(new H3("Version History: " + name));

        if (versions.isEmpty()) {
            dialog.add(new Paragraph("No retained versions of this policy."));
        } else {
            final Grid<PolicyVersionSummary> grid = new Grid<>();
            grid.addColumn(PolicyVersionSummary::getRevision).setHeader("Revision").setAutoWidth(true).setFlexGrow(0);
            grid.addColumn(v -> ViewSupport.utc(v.getCapturedTimestamp())).setHeader("Captured").setAutoWidth(true);
            grid.addColumn(v -> shortHash(v.getContentHash())).setHeader("Hash").setAutoWidth(true);
            grid.addComponentColumn(v -> ViewSupport.button("View", VaadinIcon.EYE, "View revision " + v.getRevision(),
                    () -> openRevision(v.getRevision()))).setHeader("View").setAutoWidth(true).setFlexGrow(0);
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
                onRollback.run();
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
                result.add(differences(from.getValue(), to.getValue(),
                        client.getPolicyVersion(name, from.getValue()), client.getPolicyVersion(name, to.getValue())));
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

    private static com.vaadin.flow.component.Component differences(final int fromRevision, final int toRevision,
                                                                   final String from, final String to) {
        final List<PolicyJson.Difference> differences = PolicyJson.differences(from, to);
        if (differences.isEmpty()) {
            return new Span("Revisions " + fromRevision + " and " + toRevision + " have the same content.");
        }
        final Grid<PolicyJson.Difference> grid = new Grid<>();
        grid.addColumn(PolicyJson.Difference::operation).setHeader("Change").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(PolicyJson.Difference::path).setHeader("Path").setFlexGrow(2).setResizable(true);
        grid.addColumn(PolicyJson.Difference::before).setHeader("Before").setFlexGrow(1).setResizable(true);
        grid.addColumn(PolicyJson.Difference::after).setHeader("After").setFlexGrow(1).setResizable(true);
        grid.setItems(differences);
        grid.setAllRowsVisible(true);
        return grid;
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
