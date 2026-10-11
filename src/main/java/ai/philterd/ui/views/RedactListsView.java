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
import ai.philterd.philter.model.RedactList;
import ai.philterd.philter.model.RedactListTerms;
import ai.philterd.philter.model.RedactLists;
import ai.philterd.philter.model.exceptions.ClientException;
import ai.philterd.philter.model.exceptions.ErrorReasons;
import ai.philterd.philter.model.exceptions.ServiceUnavailableException;
import ai.philterd.ui.model.PhilterRules;
import ai.philterd.ui.model.Lines;
import ai.philterd.ui.security.PhilterClients;
import ai.philterd.ui.security.Roles;
import ai.philterd.ui.security.Sessions;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The person's always-redact and never-redact lists, through Philter's redact lists API. Each list is saved on
 * its own with the revision it was read at, so Philter refuses the save if someone changed that list since.
 */
@Route(value = "redact-lists", layout = MainLayout.class)
@PageTitle("Always/Never Redact Lists | Philter UI")
@RolesAllowed(Roles.USER)
public class RedactListsView extends VerticalLayout {

    /** The two lists, and how each is named on the page and in messages. */
    enum Kind {
        ALWAYS("Always Redact List", "always-redact", "will always be redacted", RedactList.ALWAYS),
        NEVER("Never Redact List", "never-redact", "will never be redacted", RedactList.NEVER);

        private final String title;
        private final String label;
        private final String effect;
        private final RedactList list;

        Kind(final String title, final String label, final String effect, final RedactList list) {
            this.title = title;
            this.label = label;
            this.effect = effect;
            this.list = list;
        }
    }

    private final transient PhilterClient client;
    private final Map<Kind, TextArea> texts = new EnumMap<>(Kind.class);
    /** The revision of each list as it was last read or saved, sent with the next save of that list. */
    private final Map<Kind, Long> revisions = new EnumMap<>(Kind.class);

    public RedactListsView(final PhilterClients clients) {

        this.client = clients.forUser(Sessions.currentUser().orElseThrow());

        setSizeFull();

        final RedactLists lists = ViewSupport.unchecked(client::listRedactLists);

        final TabSheet tabs = new TabSheet();
        for (final Kind kind : Kind.values()) {
            tabs.add(kind.title, listTab(kind, terms(lists, kind)));
            revisions.put(kind, revision(lists, kind));
        }
        tabs.setSizeFull();

        add(new H2("Always/Never Redact Lists"), tabs);

    }

    private VerticalLayout listTab(final Kind kind, final List<String> terms) {

        final Span description = new Span("These terms, one per line, " + kind.effect + " in your redactions, "
                + "regardless of the policy. ");
        description.add(PhilterDocs.link("Learn more about always and never redact lists.", "redaction/redact_lists.html"));

        final TextArea text = new TextArea();
        text.setSizeFull();
        texts.put(kind, text);
        text.setValue(String.join("\n", terms));
        text.setHelperText(termsHelp(ViewSupport.rules(client)));

        final Button save = new Button("Save " + kind.title, e -> save(kind, text));
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        final VerticalLayout layout = new VerticalLayout(description, text, save);
        layout.setSizeFull();
        return layout;

    }

    private void save(final Kind kind, final TextArea text) {

        final List<String> terms = Lines.terms(text.getValue());
        final String problem = termsProblem(kind, terms, ViewSupport.rules(client));
        if (problem != null) {
            Notifications.failure(problem);
            return;
        }

        final String unsaved = text.getValue();
        try {
            final Saved saved = save(store(), kind, terms, revisions.get(kind));
            show(kind, saved.list());
            if (saved.changed()) {
                showConflict(kind, unsaved);
            } else {
                Notifications.success(kind.title + " saved.");
            }
        } catch (final ClientException | ServiceUnavailableException | IOException e) {
            Notifications.failure(e, "The " + kind.label + " list could not be saved.");
        }

    }

    /** Philter's per-list reads and writes, so a save can be tested without Philter. */
    interface ListStore {
        RedactListTerms read(RedactList list) throws IOException;

        RedactListTerms replace(RedactList list, List<String> terms, Long expectedRevision) throws IOException;
    }

    private ListStore store() {
        return new ListStore() {
            @Override
            public RedactListTerms read(final RedactList list) throws IOException {
                return client.getRedactList(list);
            }

            @Override
            public RedactListTerms replace(final RedactList list, final List<String> terms, final Long expectedRevision)
                    throws IOException {
                return client.replaceRedactList(list, terms, expectedRevision);
            }
        };
    }

    /**
     * The list after a save: as written, or, when someone changed it since it was read ({@code changed}), as
     * Philter now has it, with nothing written.
     */
    record Saved(RedactListTerms list, boolean changed) {
    }

    /**
     * Replaces only {@code kind}'s list, if it is still at {@code revision}. When it is not, nothing is written
     * and the list is read again so the person can start from what Philter has.
     */
    static Saved save(final ListStore store, final Kind kind, final List<String> terms, final Long revision)
            throws IOException {
        try {
            return new Saved(store.replace(kind.list, terms, revision), false);
        } catch (final ClientException e) {
            if (!ErrorReasons.REDACT_LIST_CHANGED.equals(e.getReason())) {
                throw e;
            }
            return new Saved(store.read(kind.list), true);
        }
    }

    /** What the person is told when their save was refused because someone else changed the list. */
    static String conflictMessage(final Kind kind) {
        return "Someone changed the " + kind.label + " list after you opened it, so your edits were not saved. "
                + "The list now shows Philter's current terms. Your edits are below: copy what you need, apply "
                + "it to the current list, and save again.";
    }

    private void showConflict(final Kind kind, final String unsaved) {
        final TextArea edits = new TextArea("Your unsaved edits");
        edits.setValue(unsaved);
        edits.setReadOnly(true);
        edits.setWidthFull();
        edits.setHeight("240px");

        final Dialog dialog = new Dialog();
        dialog.setWidth("600px");
        dialog.add(new H3(kind.title + " Changed"), new Paragraph(conflictMessage(kind)), edits);
        final Button close = new Button("Close", e -> dialog.close());
        close.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(close);
        dialog.open();
    }

    /** What is wrong with the terms under Philter's limits, or {@code null} if nothing is. */
    static String termsProblem(final Kind kind, final List<String> terms, final PhilterRules rules) {
        final Integer maximumTerms = rules.redactListMaximumTerms();
        if (maximumTerms != null && terms.size() > maximumTerms) {
            return "The " + kind.label + " list can have at most " + maximumTerms + " terms; this one has "
                    + terms.size() + ".";
        }
        final Integer maximumLength = rules.redactListTermMaximumLength();
        for (final String term : terms) {
            if (maximumLength != null && term.length() > maximumLength) {
                return "Each term can be at most " + maximumLength + " characters: \"" + term + "\" is longer.";
            }
        }
        return null;
    }

    /** The terms field's helper text, stating Philter's limits when they are known. */
    static String termsHelp(final PhilterRules rules) {
        final String limits = rules.redactListMaximumTerms() == null || rules.redactListTermMaximumLength() == null
                ? "One term per line. "
                : "Up to " + rules.redactListMaximumTerms() + " terms of up to " + rules.redactListTermMaximumLength()
                        + " characters each. ";
        return limits + "Saving an empty list clears it.";
    }

    /** Shows a list as Philter has it, and keeps its revision for the next save. */
    private void show(final Kind kind, final RedactListTerms list) {
        texts.get(kind).setValue(String.join("\n", ViewSupport.orEmpty(list.getTerms())));
        revisions.put(kind, list.getRevision());
    }

    private static Long revision(final RedactLists lists, final Kind kind) {
        if (lists == null) {
            return null;
        }
        return kind == Kind.ALWAYS ? lists.getAlwaysRedactRevision() : lists.getNeverRedactRevision();
    }

    private static List<String> terms(final RedactLists lists, final Kind kind) {
        if (lists == null) {
            return List.of();
        }
        return ViewSupport.orEmpty(kind == Kind.ALWAYS ? lists.getAlwaysRedact() : lists.getNeverRedact());
    }

}
