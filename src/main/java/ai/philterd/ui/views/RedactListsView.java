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
import ai.philterd.philter.model.RedactLists;
import ai.philterd.philter.model.RedactListsRequest;
import ai.philterd.philter.model.exceptions.ClientException;
import ai.philterd.philter.model.exceptions.ServiceUnavailableException;
import ai.philterd.ui.model.PhilterRules;
import ai.philterd.ui.model.Lines;
import ai.philterd.ui.security.PhilterClients;
import ai.philterd.ui.security.Roles;
import ai.philterd.ui.security.Sessions;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
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
 * The person's always-redact and never-redact lists, through Philter's redact lists API. Philter stores
 * both lists together and a save replaces both, so saving one list sends the other as Philter has it.
 */
@Route(value = "redact-lists", layout = MainLayout.class)
@PageTitle("Always/Never Redact Lists | Philter UI")
@RolesAllowed(Roles.USER)
public class RedactListsView extends VerticalLayout {

    /** Philter's limits on each list, checked here too so a mistake shows before the request. */

    /** The two lists, and how each is named on the page and in messages. */
    enum Kind {
        ALWAYS("Always Redact List", "always-redact", "will always be redacted"),
        NEVER("Never Redact List", "never-redact", "will never be redacted");

        private final String title;
        private final String label;
        private final String effect;

        Kind(final String title, final String label, final String effect) {
            this.title = title;
            this.label = label;
            this.effect = effect;
        }

        Kind other() {
            return this == ALWAYS ? NEVER : ALWAYS;
        }
    }

    private final transient PhilterClient client;
    private final Map<Kind, TextArea> texts = new EnumMap<>(Kind.class);
    /** What each text area last showed from Philter, to tell whether the person has edited it since. */
    private final Map<Kind, String> shown = new EnumMap<>(Kind.class);

    public RedactListsView(final PhilterClients clients) {

        this.client = clients.forUser(Sessions.currentUser().orElseThrow());

        setSizeFull();

        final RedactLists lists = ViewSupport.unchecked(client::listRedactLists);

        final TabSheet tabs = new TabSheet();
        for (final Kind kind : Kind.values()) {
            tabs.add(kind.title, listTab(kind, terms(lists, kind)));
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
        show(kind, terms);
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

        try {
            // Read the other list now rather than when the page opened, so a change made elsewhere is kept.
            final RedactLists current = client.listRedactLists();
            client.createRedactList(request(kind, terms, current));
            show(kind, terms);
            // Show the other list as Philter has it too, unless the person has unsaved edits there, so a
            // later save of that list does not send stale terms.
            if (!edited(kind.other())) {
                show(kind.other(), terms(current, kind.other()));
            }
            Notifications.success(kind.title + " saved.");
        } catch (final ClientException | ServiceUnavailableException | IOException e) {
            Notifications.failure(e, "The " + kind.label + " list could not be saved.");
        }

    }

    /**
     * The request that saves one list: its new terms, and the other list as Philter has it. Philter's
     * save replaces both lists, so sending the other one is what keeps it.
     */
    static RedactListsRequest request(final Kind kind, final List<String> terms, final RedactLists current) {
        return kind == Kind.ALWAYS
                ? new RedactListsRequest(terms, terms(current, Kind.NEVER))
                : new RedactListsRequest(terms(current, Kind.ALWAYS), terms);
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

    private void show(final Kind kind, final List<String> terms) {
        final String value = String.join("\n", terms);
        texts.get(kind).setValue(value);
        shown.put(kind, value);
    }

    private boolean edited(final Kind kind) {
        return !texts.get(kind).getValue().equals(shown.get(kind));
    }

    private static List<String> terms(final RedactLists lists, final Kind kind) {
        if (lists == null) {
            return List.of();
        }
        return ViewSupport.orEmpty(kind == Kind.ALWAYS ? lists.getAlwaysRedact() : lists.getNeverRedact());
    }

}
