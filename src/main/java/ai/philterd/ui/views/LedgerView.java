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
import ai.philterd.philter.model.GetLedgerResponse;
import ai.philterd.philter.model.LedgerChain;
import ai.philterd.philter.model.LedgerEntry;
import ai.philterd.philter.model.exceptions.ClientException;
import ai.philterd.philter.model.exceptions.ServiceUnavailableException;
import ai.philterd.ui.model.Listing;
import ai.philterd.ui.model.PhilterRules;
import ai.philterd.ui.security.PhilterClients;
import ai.philterd.ui.security.PhilterUser;
import ai.philterd.ui.security.Roles;
import ai.philterd.ui.security.Sessions;
import com.google.gson.Gson;
import com.vaadin.flow.component.Component;
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
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.List;

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

    public LedgerView(final PhilterClients clients) {

        final PhilterUser user = Sessions.currentUser().orElseThrow();
        this.client = clients.forUser(user);

        final PhilterRules rules = ViewSupport.rules(client);
        // Philter says whether the person may delete chains: an administrator, with LEDGER_DELETION_ENABLED on.
        this.canDelete = rules.mayDeleteLedgerChains();

        setSizeFull();

        grid.addColumn(LedgerEntry::getDocumentId).setHeader("Document ID").setResizable(true).setAutoWidth(true);
        grid.addColumn(LedgerEntry::getFilename).setHeader("Filename").setResizable(true).setSortProperty("filename");
        grid.addColumn(entry -> ViewSupport.utc(entry.getTimestamp())).setHeader("Created").setAutoWidth(true)
                .setSortProperty("created");
        grid.addComponentColumn(LedgerView::readStatus).setHeader("Status").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(entry -> ViewSupport.button("View", VaadinIcon.EYE,
                "View ledger chain " + entry.getDocumentId(),
                () -> openChain(entry.getDocumentId(), null, entry.getReadError())))
                .setHeader("View").setAutoWidth(true).setFlexGrow(0);
        if (canDelete) {
            grid.addComponentColumn(entry -> ViewSupport.button(null, VaadinIcon.TRASH,
                    "Delete ledger chain " + entry.getDocumentId(), () -> openDelete(entry.getDocumentId())))
                    .setHeader("Delete").setAutoWidth(true).setFlexGrow(0);
        }
        final ListingProvider<LedgerEntry> listing = ListingProvider.bind(grid, options -> ledger(
                client.listLedgerChains(null, options)));
        grid.setSizeFull();

        final TextField searchField = listing.searchField(grid, "Search by document ID or filename");
        searchField.setWidth("320px");
        listing.onTotal(total -> count.setText(countLabel(total, !searchField.getValue().isBlank())));
        final HorizontalLayout searchRow = new HorizontalLayout(searchField);
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
        if (rules.mayReachOtherUsers()) {
            tabs.add("All Ledgers", allLedgers());
        }
        tabs.setSizeFull();

        add(new H2("Redaction Ledgers"), tabs);

    }

    private VerticalLayout allLedgers() {

        final Grid<LedgerEntry> all = new Grid<>();
        all.addColumn(LedgerEntry::getDocumentId).setHeader("Document ID").setResizable(true).setAutoWidth(true);
        all.addColumn(LedgerEntry::getOwner).setHeader("Owner").setResizable(true);
        all.addColumn(entry -> ViewSupport.utc(entry.getTimestamp())).setHeader("Created").setAutoWidth(true)
                .setSortProperty("created");
        all.addComponentColumn(LedgerView::readStatus).setHeader("Status").setAutoWidth(true).setFlexGrow(0);
        all.addComponentColumn(entry -> ViewSupport.button("View", VaadinIcon.EYE,
                "View ledger chain " + entry.getDocumentId(),
                () -> openChain(entry.getDocumentId(), entry.getOwner(), entry.getReadError())))
                .setHeader("View").setAutoWidth(true).setFlexGrow(0);
        final ListingProvider<LedgerEntry> listing = ListingProvider.bind(all, options -> ledger(
                client.listLedgerChainsAcrossUsers(options)));
        all.setSizeFull();

        final VerticalLayout layout = new VerticalLayout(
                new Span("All redaction ledger chains across all users, including users who have been deactivated."),
                listing.toolbar(all, "Search by document ID or filename", "chain", "chains"), all);
        layout.setSizeFull();
        return layout;

    }

    /**
     * Shows a chain's entries and whether it verifies. The original values are never shown, only exported.
     *
     * @param readError Why Philter could not read the chain's head entry, from the listing, or {@code null}.
     */
    private void openChain(final String documentId, final String owner, final String readError) {

        final LedgerChain chain;
        try {
            // One entry is enough here: the verification covers the whole chain on every page, and the grid
            // reads the entries itself.
            chain = client.getLedgerChain(documentId, owner, 0, 1);
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
        if (readError != null) {
            dialog.add(new Paragraph(unreadableDetail(readError)));
        }

        if (chain.getValidationError() != null) {
            // Philter does not return the entries of a chain it could not check.
            final Button close = new Button("Close", e -> dialog.close());
            close.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            dialog.add(new Paragraph("Philter does not return the entries of a chain it could not check."));
            dialog.add(exportDownloader);
            dialog.getFooter().add(exportButton(documentId, owner, readError, exportDownloader), close);
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
        final ListingProvider<LedgerEntry> listing = ListingProvider.bind(entries,
                chainEntries((offset, limit) -> client.getLedgerChain(documentId, owner, offset, limit)));
        entries.setSizeFull();
        dialog.add(listing.toolbar(entries, null, "entry", "entries"), entries);

        final Button close = new Button("Close", e -> dialog.close());
        close.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.add(exportDownloader);
        dialog.getFooter().add(exportButton(documentId, owner, readError, exportDownloader), close);
        dialog.open();

    }

    /**
     * Fetches the export when clicked, so viewing a chain does not record an export in Philter's audit
     * log, and so a refusal can be shown with Philter's reason. The download starts only once Philter
     * has returned the export.
     */
    private Button exportButton(final String documentId, final String owner, final String readError,
                                final Anchor downloader) {
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
        if (readError != null) {
            // Philter refuses with entry_unreadable; a refusal for a later entry is still shown when it comes.
            export.setEnabled(false);
            export.setTooltipText(EXPORT_UNAVAILABLE);
        }
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

    /** Reads a range of a chain's entries, with the chain's checks and total. */
    interface ChainReader extends Serializable {
        LedgerChain read(int offset, int limit) throws IOException;
    }

    /** A chain's entries as a listing, a page at a time, so every entry of a long chain can be reached. */
    static ListingProvider.Reader<LedgerEntry> chainEntries(final ChainReader reader) {
        return options -> entries(reader.read(options.getOffset(), options.getLimit()));
    }

    /** A page of a chain's entries and how many the whole chain has. */
    static Listing<LedgerEntry> entries(final LedgerChain chain) {
        final List<LedgerEntry> items = ViewSupport.orEmpty(chain.getEntries());
        return new Listing<>(items, chain.getTotal() == null ? items.size() : chain.getTotal());
    }

    private static Listing<LedgerEntry> ledger(final GetLedgerResponse page) {
        return new Listing<>(page.getChains(), page.getTotal());
    }

    /** How many chains the list holds, worded for a search or for the whole ledger. */
    static String countLabel(final long total, final boolean searching) {
        final String chains = total == 1 ? "1 chain" : total + " chains";
        return searching ? chains + " found." : chains + " in your ledger.";
    }

    static final String UNREADABLE_LABEL = "Cannot be read";

    static final String EXPORT_UNAVAILABLE = "Philter does not export a chain with an entry it cannot read.";

    /** A listing row's status: marked when Philter could not read the chain's head entry, with why. */
    private static Component readStatus(final LedgerEntry entry) {
        if (entry.getReadError() == null) {
            return new Span();
        }
        final Span status = new Span(VaadinIcon.WARNING.create(), new Span(UNREADABLE_LABEL));
        status.getElement().getThemeList().add("badge error");
        status.getElement().setAttribute("title", unreadableDetail(entry.getReadError()));
        return status;
    }

    /** What the person is told about a chain whose head entry Philter could not read. */
    static String unreadableDetail(final String readError) {
        return "Philter could not read this chain's first entry, so the chain cannot be verified or exported. "
                + "Philter says: " + readError.strip();
    }

    static String exportFilename(final String documentId) {
        return "ledger-" + documentId.replaceAll("[^A-Za-z0-9._-]", "_") + "-export.json";
    }


}
