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
import ai.philterd.philter.model.AdminSettings;
import ai.philterd.philter.model.GetLedgerResponse;
import ai.philterd.philter.model.LedgerChain;
import ai.philterd.philter.model.LedgerEntry;
import ai.philterd.philter.model.exceptions.ClientException;
import ai.philterd.philter.model.exceptions.ServiceUnavailableException;
import ai.philterd.ui.model.Pages;
import ai.philterd.ui.security.PhilterClients;
import ai.philterd.ui.security.PhilterUser;
import ai.philterd.ui.security.Roles;
import ai.philterd.ui.security.Sessions;
import com.google.gson.Gson;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AttachmentType;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import jakarta.annotation.security.RolesAllowed;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * The person's redaction ledgers, through Philter's ledger API. Administrators also see every user's
 * chains when Philter allows cross-user access, and can delete and purge chains when Philter allows
 * ledger deletion.
 */
@Route(value = "ledgers", layout = MainLayout.class)
@PageTitle("Redaction Ledgers | Philter UI")
@RolesAllowed(Roles.USER)
public class LedgerView extends VerticalLayout {

    private static final Gson GSON = new Gson();

    /** How a chain's verification is shown. */
    enum Verification {
        VERIFIED("Chain verified", "success"),
        INVALID("Chain invalid", "error"),
        NOT_VERIFIED("Not verified", "contrast");

        final String label;
        final String theme;

        Verification(final String label, final String theme) {
            this.label = label;
            this.theme = theme;
        }
    }

    private final transient PhilterClient client;
    private final Grid<LedgerEntry> grid = new Grid<>();
    private final Span count = new Span();
    private final boolean canDelete;
    private String search;

    public LedgerView(final PhilterClients clients) {

        final PhilterUser user = Sessions.currentUser().orElseThrow();
        this.client = clients.forUser(user);

        final Optional<AdminSettings> settings = ViewSupport.adminSettings(user, client);
        // Philter refuses deletion unless the person is an administrator and LEDGER_DELETION_ENABLED is set.
        this.canDelete = settings.map(AdminSettings::isLedgerDeletionEnabled).orElse(false);

        setSizeFull();

        grid.addColumn(LedgerEntry::getDocumentId).setHeader("Document ID").setResizable(true).setAutoWidth(true);
        grid.addColumn(LedgerEntry::getFilename).setHeader("Filename").setResizable(true);
        grid.addColumn(entry -> ViewSupport.utc(entry.getTimestamp())).setHeader("Created").setAutoWidth(true);
        grid.addComponentColumn(entry -> ViewSupport.button("View", VaadinIcon.EYE,
                "View ledger chain " + entry.getDocumentId(), () -> openChain(entry.getDocumentId(), null)))
                .setHeader("View").setAutoWidth(true).setFlexGrow(0);
        if (canDelete) {
            grid.addComponentColumn(entry -> ViewSupport.button(null, VaadinIcon.TRASH,
                    "Delete ledger chain " + entry.getDocumentId(), () -> openDelete(entry.getDocumentId())))
                    .setHeader("Delete").setAutoWidth(true).setFlexGrow(0);
        }
        grid.setItems(
                query -> ViewSupport.unchecked(() -> Pages.read(query.getOffset(), query.getLimit(),
                        (offset, limit) -> ViewSupport.orEmpty(
                                client.listLedgerChains(blankToNull(search), null, offset, limit).getChains()))).stream(),
                query -> {
                    final int total = ViewSupport.unchecked(
                            () -> client.listLedgerChains(blankToNull(search), null, 0, 1).getTotal());
                    count.setText(countLabel(total, blankToNull(search) != null));
                    return total;
                });
        grid.setSizeFull();

        final TextField searchField = new TextField();
        searchField.setPlaceholder("Search by document ID or filename");
        searchField.setClearButtonVisible(true);
        searchField.setWidth("320px");
        searchField.addValueChangeListener(e -> {
            search = e.getValue();
            grid.getDataProvider().refreshAll();
        });
        final Button searchButton = new Button("Search", VaadinIcon.SEARCH.create(), e -> {
            search = searchField.getValue();
            grid.getDataProvider().refreshAll();
        });
        final HorizontalLayout searchRow = new HorizontalLayout(searchField, searchButton);
        searchRow.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);
        if (canDelete) {
            final Button purge = new Button("Purge old entries", VaadinIcon.TRASH.create(), e -> openPurge());
            purge.addThemeVariants(ButtonVariant.LUMO_ERROR);
            purge.setTooltipText("Delete ledger chains older than a number of days.");
            searchRow.add(purge);
        }

        final Span description = new Span("The redaction ledger is a tamper-evident, hash-chained record of the "
                + "redactions made in contexts that have the ledger enabled. Entries are kept indefinitely by default. ");
        description.add(PhilterDocs.link("Learn more about the redaction ledger.", "redaction/ledgers.html"));

        final VerticalLayout mine = new VerticalLayout(description, searchRow, count, grid);
        mine.setSizeFull();

        final TabSheet tabs = new TabSheet();
        tabs.add("My Ledgers", mine);
        if (settings.map(AdminSettings::isCrossUserAccessEnabled).orElse(false)) {
            tabs.add("All Ledgers", allLedgers());
        }
        tabs.setSizeFull();

        add(new H2("Redaction Ledgers"), tabs);

    }

    private VerticalLayout allLedgers() {

        final Grid<LedgerEntry> all = new Grid<>();
        all.addColumn(LedgerEntry::getDocumentId).setHeader("Document ID").setResizable(true).setAutoWidth(true);
        all.addColumn(LedgerEntry::getOwner).setHeader("Owner").setResizable(true);
        all.addColumn(entry -> ViewSupport.utc(entry.getTimestamp())).setHeader("Created").setAutoWidth(true);
        all.addComponentColumn(entry -> ViewSupport.button("View", VaadinIcon.EYE,
                "View ledger chain " + entry.getDocumentId(), () -> openChain(entry.getDocumentId(), entry.getOwner())))
                .setHeader("View").setAutoWidth(true).setFlexGrow(0);
        all.setItems(
                query -> ViewSupport.unchecked(() -> Pages.read(query.getOffset(), query.getLimit(),
                        (offset, limit) -> ViewSupport.orEmpty(
                                client.listLedgerChainsAcrossUsers(offset, limit).getChains()))).stream(),
                query -> ViewSupport.unchecked(() -> client.listLedgerChainsAcrossUsers(0, 1).getTotal()));
        all.setSizeFull();

        final VerticalLayout layout = new VerticalLayout(
                new Span("All redaction ledger chains across all users, including users who have been deactivated."), all);
        layout.setSizeFull();
        return layout;

    }

    /** Shows a chain's entries and whether it verifies. The original values are never shown, only exported. */
    private void openChain(final String documentId, final String owner) {

        final LedgerChain chain;
        try {
            chain = client.getLedgerChain(documentId, owner);
        } catch (final ClientException | ServiceUnavailableException | IOException e) {
            Notifications.failure(e, "The ledger chain could not be read.");
            return;
        }

        final Anchor exportDownloader = new Anchor();
        exportDownloader.getStyle().set("display", "none");

        final Verification verification = verification(chain);
        final Span badge = new Span(verification.label);
        badge.getElement().getThemeList().add("badge " + verification.theme);

        final Dialog dialog = new Dialog();
        dialog.setWidth("900px");
        dialog.setHeight("650px");
        dialog.add(new H3("Ledger Chain"),
                new Paragraph("Document ID: " + documentId + (owner == null ? "" : " (owner: " + owner + ")")),
                badge, new Paragraph(verificationDetail(chain)));

        if (chain.getValidationError() != null) {
            // Philter does not return the entries of a chain it could not check.
            final Button close = new Button("Close", e -> dialog.close());
            close.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            dialog.add(new Paragraph("Philter does not return the entries of a chain it could not check."));
            dialog.add(exportDownloader);
            dialog.getFooter().add(exportButton(documentId, owner, exportDownloader), close);
            dialog.open();
            return;
        }

        final Grid<LedgerEntry> entries = new Grid<>();
        entries.addColumn(LedgerEntry::getType).setHeader("Type").setAutoWidth(true);
        entries.addColumn(LedgerEntry::getReplacement).setHeader("Replacement").setAutoWidth(true);
        entries.addColumn(LedgerEntry::getStartPosition).setHeader("Position").setAutoWidth(true);
        entries.addColumn(LedgerEntry::getPolicyName).setHeader("Policy").setAutoWidth(true);
        entries.addColumn(LedgerEntry::getPolicyVersion).setHeader("Version").setAutoWidth(true);
        entries.addColumn(entry -> ViewSupport.utc(entry.getTimestamp())).setHeader("Timestamp").setAutoWidth(true);
        entries.setItems(ViewSupport.orEmpty(chain.getEntries()));
        entries.setSizeFull();
        dialog.add(entries);

        final Button close = new Button("Close", e -> dialog.close());
        close.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.add(exportDownloader);
        dialog.getFooter().add(exportButton(documentId, owner, exportDownloader), close);
        dialog.open();

    }

    /**
     * Fetches the export when clicked, so viewing a chain does not record an export in Philter's audit
     * log, and so a refusal can be shown with Philter's reason. The download starts only once Philter
     * has returned the export.
     */
    private Button exportButton(final String documentId, final String owner, final Anchor downloader) {
        final Button export = new Button("Export (JSON)", VaadinIcon.DOWNLOAD.create(), e -> {
            final byte[] json;
            try {
                json = GSON.toJson(client.getLedgerExport(documentId, owner)).getBytes(StandardCharsets.UTF_8);
            } catch (final ClientException | ServiceUnavailableException | IOException ex) {
                Notifications.failure(ex, "Philter could not export this chain.");
                return;
            }
            downloader.setHref(DownloadHandler.fromInputStream(event -> new DownloadResponse(
                    new ByteArrayInputStream(json), exportFilename(documentId), "application/json", json.length)),
                    AttachmentType.DOWNLOAD);
            downloader.getElement().executeJs("this.click()");
        });
        export.setTooltipText("Includes the original redacted values. Store it securely.");
        return export;
    }

    private void openDelete(final String documentId) {

        final Dialog dialog = new Dialog();
        dialog.add(new H3("Confirm Deletion"), new Paragraph("Delete the ledger chain for document " + documentId
                + "? This permanently removes its entries and cannot be undone."));

        final Button delete = new Button("Delete", e -> {
            try {
                client.deleteLedgerEntry(documentId);
                dialog.close();
                grid.getDataProvider().refreshAll();
                Notifications.success("Ledger chain deleted.");
            } catch (final ClientException | ServiceUnavailableException | IOException ex) {
                // A legal hold refuses with 423 and names the holds in the message.
                Notifications.failure(ex, "The ledger chain could not be deleted.");
            }
        });
        delete.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        dialog.getFooter().add(ViewSupport.cancel(dialog), delete);
        dialog.open();

    }

    private void openPurge() {

        final IntegerField days = new IntegerField("Delete chains older than (days)");
        days.setMin(0);
        days.setValue(90);
        days.setStepButtonsVisible(true);
        days.setWidthFull();

        final Dialog dialog = new Dialog();
        dialog.setWidth("420px");
        dialog.add(new H3("Purge Old Ledger Entries"), new Paragraph("Permanently delete your completed ledger "
                + "chains older than the given number of days. This cannot be undone. Any active legal hold on "
                + "your evidence blocks the whole purge."), days);

        final Button purge = new Button("Purge", e -> {
            if (days.getValue() == null || days.getValue() < 0) {
                days.setErrorMessage("Enter zero or more days.");
                days.setInvalid(true);
                return;
            }
            try {
                final String message = client.purgeLedger(days.getValue()).getMessage();
                dialog.close();
                grid.getDataProvider().refreshAll();
                Notifications.success(message == null ? "Ledger purged." : message);
            } catch (final ClientException | ServiceUnavailableException | IOException ex) {
                Notifications.failure(ex, "The ledger could not be purged.");
            }
        });
        purge.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        dialog.getFooter().add(ViewSupport.cancel(dialog), purge);
        dialog.open();

    }

    /**
     * How a chain verifies. A chain Philter could not check is "not verified", which is different from
     * a chain whose hashes or signatures do not match.
     */
    static Verification verification(final LedgerChain chain) {
        if (chain.getValidationError() != null) {
            return Verification.NOT_VERIFIED;
        }
        return chain.isValid() ? Verification.VERIFIED : Verification.INVALID;
    }

    /** What the verification found, in a sentence. */
    static String verificationDetail(final LedgerChain chain) {
        if (chain.getValidationError() != null) {
            return "Philter could not check this chain: " + chain.getValidationError();
        }
        if (chain.isValid()) {
            return "The hash chain is intact and every signed entry's signature matches.";
        }
        final StringBuilder detail = new StringBuilder();
        if (Boolean.FALSE.equals(chain.getHashChainValid())) {
            detail.append("The hash chain is broken: an entry has been changed, removed, or reordered. ");
        }
        if (Boolean.FALSE.equals(chain.getSignaturesValid())) {
            detail.append("A signature does not match its entry. ");
        }
        return detail.length() == 0 ? "The chain does not verify." : detail.toString().trim();
    }

    /** How many chains the list holds, worded for a search or for the whole ledger. */
    static String countLabel(final int total, final boolean searching) {
        final String chains = total == 1 ? "1 chain" : total + " chains";
        return searching ? chains + " found." : chains + " in your ledger.";
    }

    static String exportFilename(final String documentId) {
        return "ledger-" + documentId.replaceAll("[^A-Za-z0-9._-]", "_") + "-export.json";
    }

    private static String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

}
