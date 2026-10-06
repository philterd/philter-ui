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
import ai.philterd.philter.model.LegalHoldRequest;
import ai.philterd.philter.model.LegalHoldResponse;
import ai.philterd.philter.model.OwnedLegalHoldResponse;
import ai.philterd.philter.model.exceptions.ClientException;
import ai.philterd.philter.model.exceptions.ServiceUnavailableException;
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
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.io.IOException;

/**
 * The person's legal holds, through Philter's legal holds API. Administrators also see every user's
 * holds, and can release them, when Philter allows cross-user access.
 */
@Route(value = "legal-holds", layout = MainLayout.class)
@PageTitle("Legal Holds | Philter UI")
@RolesAllowed(Roles.USER)
public class HoldsView extends VerticalLayout {

    /** Philter's scope types. */
    static final String SCOPE_DOCUMENT_CHAIN = "document_chain";
    static final String SCOPE_USER = "user";

    private final transient PhilterClient client;
    private final PhilterUser user;
    private final Grid<LegalHoldResponse> grid = new Grid<>();
    private Grid<OwnedLegalHoldResponse> allGrid;

    public HoldsView(final PhilterClients clients) {

        this.user = Sessions.currentUser().orElseThrow();
        this.client = clients.forUser(user);

        setSizeFull();

        grid.addColumn(LegalHoldResponse::getReference).setHeader("Reference").setAutoWidth(true).setResizable(true);
        grid.addColumn(hold -> scopeLabel(hold.getScopeType())).setHeader("Scope").setAutoWidth(true);
        grid.addColumn(LegalHoldResponse::getScopeValue).setHeader("Document or User").setAutoWidth(true).setResizable(true);
        grid.addColumn(LegalHoldResponse::getReason).setHeader("Reason").setFlexGrow(1).setResizable(true);
        grid.addColumn(hold -> ViewSupport.utc(hold.getSetAt())).setHeader("Set At").setAutoWidth(true);
        grid.addComponentColumn(hold -> releaseButton(hold, null)).setAutoWidth(true).setFlexGrow(0);
        // Philter's listing has no total, so the grid pages until a page comes back short.
        grid.setItems(query -> ViewSupport.unchecked(() -> Pages.read(query.getOffset(), query.getLimit(),
                (offset, limit) -> ViewSupport.orEmpty(client.getHolds(null, offset, limit)))).stream());
        grid.setSizeFull();

        final Span description = new Span("Legal holds block deletion and purge of redaction evidence until they are "
                + "released. Every hold and release is audited. ");
        description.add(PhilterDocs.link("Learn more about legal holds.", "redaction/legal_holds.html"));

        final VerticalLayout mine = new VerticalLayout(description, grid);
        mine.setSizeFull();

        final TabSheet tabs = new TabSheet();
        tabs.add("My Legal Holds", mine);
        if (ViewSupport.showAllUsers(user, client)) {
            tabs.add("All Legal Holds", allHolds());
        }
        tabs.setSizeFull();

        final Button set = new Button("Set Hold", VaadinIcon.LOCK.create(), e -> openSetHold());
        set.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        set.setTooltipText("Set a legal hold on your evidence.");
        tabs.setSuffixComponent(set);

        add(new H2("Legal Holds"), tabs);

    }

    private VerticalLayout allHolds() {

        allGrid = new Grid<>();
        allGrid.addColumn(LegalHoldResponse::getReference).setHeader("Reference").setAutoWidth(true).setResizable(true);
        allGrid.addColumn(OwnedLegalHoldResponse::getOwner).setHeader("Owner").setAutoWidth(true).setResizable(true);
        allGrid.addColumn(hold -> scopeLabel(hold.getScopeType())).setHeader("Scope").setAutoWidth(true);
        allGrid.addColumn(LegalHoldResponse::getScopeValue).setHeader("Document or User").setAutoWidth(true).setResizable(true);
        allGrid.addColumn(LegalHoldResponse::getReason).setHeader("Reason").setFlexGrow(1).setResizable(true);
        allGrid.addColumn(hold -> ViewSupport.utc(hold.getSetAt())).setHeader("Set At").setAutoWidth(true);
        allGrid.addComponentColumn(hold -> releaseButton(hold, hold.getOwner())).setAutoWidth(true).setFlexGrow(0);
        allGrid.setItems(query -> ViewSupport.unchecked(() -> Pages.read(query.getOffset(), query.getLimit(),
                (offset, limit) -> ViewSupport.orEmpty(client.getHoldsAcrossUsers(offset, limit)))).stream());
        allGrid.setSizeFull();

        final VerticalLayout layout = new VerticalLayout(
                new Span("All legal holds across all users, including users who have been deactivated."), allGrid);
        layout.setSizeFull();
        return layout;

    }

    private Button releaseButton(final LegalHoldResponse hold, final String owner) {
        final Button release = ViewSupport.button("Release", VaadinIcon.UNLOCK,
                "Release hold " + hold.getReference(), () -> openRelease(hold.getReference(), owner));
        release.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
        return release;
    }

    private void openSetHold() {

        final TextField reference = new TextField("Reference");
        reference.setPlaceholder("e.g. LITIGATION-2026-001");
        reference.setWidthFull();
        reference.setRequired(true);

        final RadioButtonGroup<String> scope = new RadioButtonGroup<>("Protects");
        scope.setItems(SCOPE_DOCUMENT_CHAIN, SCOPE_USER);
        scope.setItemLabelGenerator(HoldsView::scopeLabel);
        scope.setValue(SCOPE_DOCUMENT_CHAIN);

        final TextField documentId = new TextField("Document ID");
        documentId.setPlaceholder("The ID of the document whose ledger chain to protect");
        documentId.setWidthFull();
        documentId.setRequired(true);
        scope.addValueChangeListener(e -> documentId.setVisible(SCOPE_DOCUMENT_CHAIN.equals(e.getValue())));

        final TextArea reason = new TextArea("Reason (optional)");
        reason.setPlaceholder("e.g. Outside counsel directive, regulatory audit");
        reason.setWidthFull();
        reason.setMaxHeight("120px");

        final Dialog dialog = new Dialog();
        dialog.setWidth("520px");
        dialog.add(new H3("Set Legal Hold"),
                new Paragraph("A hold blocks deletion and purge of the evidence it covers until it is released."),
                new VerticalLayout(reference, scope, documentId, reason));

        final Button set = new Button("Set Hold", e -> {
            reference.setInvalid(false);
            documentId.setInvalid(false);
            final String referenceProblem = referenceProblem(reference.getValue());
            if (referenceProblem != null) {
                invalid(reference, referenceProblem);
                return;
            }
            if (SCOPE_DOCUMENT_CHAIN.equals(scope.getValue()) && documentId.getValue().isBlank()) {
                invalid(documentId, "Enter the document ID.");
                return;
            }
            try {
                client.createHold(request(reference.getValue(), scope.getValue(), documentId.getValue(),
                        reason.getValue(), user.getUsername()));
                dialog.close();
                refresh();
                Notifications.success("Hold \"" + reference.getValue().trim() + "\" set.");
            } catch (final ClientException ex) {
                if ("hold_exists".equals(ex.getReason())) {
                    invalid(reference, "You already have a hold with this reference.");
                } else {
                    Notifications.failure(ex, "The hold could not be set.");
                }
            } catch (final IOException | ServiceUnavailableException ex) {
                Notifications.failure(ex, "The hold could not be set.");
            }
        });
        set.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(ViewSupport.cancel(dialog), set);
        dialog.open();

    }

    private void openRelease(final String reference, final String owner) {

        final Dialog dialog = new Dialog();
        dialog.setWidth("480px");
        dialog.add(new H3("Release Hold"), new Paragraph("Release hold \"" + reference + "\""
                + (owner == null ? "" : " of " + owner) + "? Once released, evidence it covered may become "
                + "eligible for deletion or purge if no other hold remains. This is audited and cannot be undone."));

        final Button release = new Button("Release", e -> {
            try {
                // The SDK sends a reference that cannot be used in a path in the query instead.
                if (owner == null) {
                    client.deleteHold(reference);
                } else {
                    client.deleteHold(reference, owner);
                }
                dialog.close();
                refresh();
                Notifications.success("Hold \"" + reference + "\" released.");
            } catch (final ClientException | ServiceUnavailableException | IOException ex) {
                Notifications.failure(ex, "The hold could not be released.");
                if (ex instanceof ClientException refused && refused.getStatusCode() == 404) {
                    // Released elsewhere since the page was loaded.
                    dialog.close();
                    refresh();
                }
            }
        });
        release.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        dialog.getFooter().add(ViewSupport.cancel(dialog), release);
        dialog.open();

    }

    private void refresh() {
        grid.getDataProvider().refreshAll();
        if (allGrid != null) {
            allGrid.getDataProvider().refreshAll();
        }
    }

    /**
     * The request that sets a hold. Philter requires a scope value for a {@code user} hold but does not
     * use it, since such a hold covers all of its owner's evidence; the owner's username is sent so the
     * hold says whose it is.
     */
    static LegalHoldRequest request(final String reference, final String scope, final String documentId,
                                    final String reason, final String owner) {
        final LegalHoldRequest request = new LegalHoldRequest();
        request.setReference(reference.trim());
        request.setScopeType(scope);
        request.setScopeValue(SCOPE_USER.equals(scope) ? owner : documentId.trim());
        request.setReason(reason == null || reason.isBlank() ? null : reason.trim());
        return request;
    }

    /** What is wrong with a reference, or {@code null} if Philter will accept it. */
    static String referenceProblem(final String reference) {
        if (reference == null || reference.isBlank()) {
            return "Enter a reference.";
        }
        if (!PathSafeNames.isPathSafe(reference.trim())) {
            return "The reference " + PathSafeNames.RULE + ".";
        }
        return null;
    }

    static String scopeLabel(final String scopeType) {
        if (SCOPE_DOCUMENT_CHAIN.equals(scopeType)) {
            return "A document's ledger chain";
        }
        if (SCOPE_USER.equals(scopeType)) {
            return "All of the user's evidence";
        }
        return scopeType;
    }

    private static void invalid(final TextField field, final String message) {
        field.setErrorMessage(message);
        field.setInvalid(true);
    }

}
