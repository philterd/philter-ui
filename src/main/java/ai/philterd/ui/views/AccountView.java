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
import ai.philterd.philter.model.ApiKey;
import ai.philterd.philter.model.ApiKeyScopeDescription;
import ai.philterd.philter.model.CurrentUser;
import ai.philterd.philter.model.GetApiKeysResponse;
import ai.philterd.philter.model.GetWebhookDeliveriesResponse;
import ai.philterd.philter.model.MfaEnrollment;
import ai.philterd.philter.model.Webhook;
import ai.philterd.philter.model.WebhookDelivery;
import ai.philterd.philter.model.WebhookEvents;
import ai.philterd.philter.model.WebhookTestResult;
import ai.philterd.philter.model.exceptions.ClientException;
import ai.philterd.philter.model.exceptions.ErrorReasons;
import ai.philterd.philter.model.exceptions.ServiceUnavailableException;
import ai.philterd.ui.model.Listing;
import ai.philterd.ui.security.Notice;
import ai.philterd.ui.security.PhilterClients;
import ai.philterd.ui.security.PhilterUser;
import ai.philterd.ui.security.Roles;
import ai.philterd.ui.security.SignInMessages;
import ai.philterd.ui.security.Sessions;
import ai.philterd.ui.model.PhilterRules;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The signed-in person's own account, through Philter's API: details and password, MFA, API keys and
 * sign-in sessions, and webhook. Philter UI does not create API keys; Philter refuses to create one from
 * a sign-in session.
 */
@Route(value = "account", layout = MainLayout.class)
@PageTitle("My Account | Philter UI")
@RolesAllowed(Roles.USER)
public class AccountView extends VerticalLayout {

    /** How long a generated webhook secret is, unless Philter requires a longer one. */
    static final int GENERATED_SECRET_LENGTH = 32;

    private static final String SECRET_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final transient PhilterClient client;
    private final PhilterUser user;
    private final transient Sessions sessions;
    private final Grid<ApiKey> keys = new Grid<>();
    private final Grid<ApiKey> signIns = new Grid<>();
    private final Grid<WebhookDelivery> deliveries = new Grid<>();
    /** Whether the person has a webhook, and whether it has a secret, as of the last read or save. */
    private boolean webhookSet;
    private boolean webhookSecretSet;

    public AccountView(final PhilterClients clients, final Sessions sessions) {

        this.user = Sessions.currentUser().orElseThrow();
        this.client = clients.forUser(user);
        this.sessions = sessions;

        setSizeFull();

        final CurrentUser account = ViewSupport.unchecked(client::getCurrentUser);

        final TabSheet tabs = new TabSheet();
        tabs.add("Account", accountTab(account));
        if (account.isMfaAvailable() || account.isMfaEnabled()) {
            tabs.add("MFA", mfaTab(account));
        }
        tabs.add("API Keys", apiKeysTab());
        tabs.add("Webhook", webhookTab());
        tabs.setSizeFull();

        add(new H2("My Account"), tabs);

    }

    // Account and password.

    private Component accountTab(final CurrentUser account) {

        final TextField username = readOnly("Username", account.getUsername());
        final TextField email = readOnly("Email", account.getEmail());
        final TextField role = readOnly("Role", account.getRole());

        final Button change = new Button("Change Password", VaadinIcon.PASSWORD.create(), e -> openChangePassword());

        final VerticalLayout layout = new VerticalLayout(username, email, role, change);
        layout.setMaxWidth("500px");
        return layout;

    }

    private void openChangePassword() {

        final PasswordField current = new PasswordField("Current password");
        final PasswordField password = new PasswordField("New password");
        final PasswordField confirm = new PasswordField("Confirm new password");
        final PhilterRules rules = ViewSupport.rules(client);
        password.setHelperText(rules.passwordRules());
        for (final PasswordField field : List.of(current, password, confirm)) {
            field.setWidthFull();
        }

        final Dialog dialog = new Dialog();
        dialog.setWidth("450px");
        dialog.add(new H3("Change Password"),
                new Paragraph("After the change you are signed out, and sign in again with the new password."),
                new VerticalLayout(current, password, confirm));

        final Button save = new Button("Change Password", e -> {
            for (final PasswordField field : List.of(current, password, confirm)) {
                field.setInvalid(false);
            }
            final String problem = rules.passwordProblem(password.getValue());
            if (problem != null) {
                invalid(password, problem);
                return;
            }
            if (!password.getValue().equals(confirm.getValue())) {
                invalid(confirm, "The passwords do not match.");
                return;
            }
            if (password.getValue().equals(current.getValue())) {
                invalid(password, "The new password must be different from the current one.");
                return;
            }
            try {
                client.changePassword(current.getValue(), password.getValue());
                // Philter revoked every session key of this user, including this one.
                sessions.end(Notice.PASSWORD_CHANGED);
            } catch (final ClientException ex) {
                switch (ChangePasswordView.passwordRefusal(ex)) {
                    case WRONG_CURRENT -> invalid(current, ChangePasswordView.WRONG_CURRENT_PASSWORD);
                    // The password was changed by another request meanwhile, which ended this session.
                    case CHANGED_ELSEWHERE -> sessions.end(Notice.ENDED);
                    default -> invalid(password, ex.getErrorMessage() == null ? "Philter did not accept the new password."
                            : ex.getErrorMessage());
                }
            } catch (final IOException | ServiceUnavailableException ex) {
                invalid(password, SignInMessages.UNAVAILABLE);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(ViewSupport.cancel(dialog), save);
        dialog.open();

    }

    // MFA.

    private Component mfaTab(final CurrentUser account) {

        final VerticalLayout layout = new VerticalLayout();
        layout.setMaxWidth("600px");

        if (account.isMfaEnabled()) {
            layout.add(new Paragraph("Multi-factor authentication is on. You sign in with your password and a code "
                    + "from your authenticator app."));
            if (account.isMfaRequired()) {
                layout.add(new Paragraph("Philter requires it, so if you remove it you will be asked to set it up "
                        + "again at your next sign-in."));
            }
            final Button remove = new Button("Remove MFA", VaadinIcon.CLOSE_SMALL.create(), e -> openRemoveMfa());
            remove.addThemeVariants(ButtonVariant.LUMO_ERROR);
            layout.add(remove);
        } else {
            layout.add(new Paragraph("Multi-factor authentication is off. Turn it on to sign in with a code from an "
                    + "authenticator app as well as your password."));
            layout.add(new Button("Set Up MFA", VaadinIcon.KEY.create(), e -> openEnrollMfa()));
        }
        return layout;

    }

    private void openEnrollMfa() {

        final MfaEnrollment enrollment;
        try {
            enrollment = client.startMfaEnrollment();
        } catch (final ClientException | ServiceUnavailableException | IOException e) {
            Notifications.failure(e, "Multi-factor authentication could not be started.");
            return;
        }

        final Image qrCode = new Image(QrCodes.svgDataUri(enrollment.getOtpauthUri()), "QR code for your authenticator app");
        qrCode.setWidth("200px");
        qrCode.setHeight("200px");
        final TextField code = codeField();

        final Dialog dialog = new Dialog();
        dialog.setWidth("450px");
        dialog.add(new H3("Set Up Multi-Factor Authentication"),
                new Paragraph("Scan the QR code with your authenticator app, or type in the setup key, then enter "
                        + "the code the app shows. You are then signed out, and sign in again with a code."),
                qrCode, new Paragraph("Setup key: " + MfaEnrollmentView.grouped(enrollment.getSecret())), code);

        final Button confirm = new Button("Confirm", e -> {
            try {
                client.confirmMfaEnrollment(code.getValue());
                // Philter revoked the session key; the person signs in again, with a code.
                sessions.end(Notice.MFA_ENROLLED);
            } catch (final ClientException ex) {
                invalid(code, MfaEnrollmentView.confirmFailure(ex));
            } catch (final IOException | ServiceUnavailableException ex) {
                invalid(code, SignInMessages.UNAVAILABLE);
            }
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(ViewSupport.cancel(dialog), confirm);
        dialog.open();

    }

    private void openRemoveMfa() {

        final TextField code = codeField();
        final Dialog dialog = new Dialog();
        dialog.setWidth("420px");
        dialog.add(new H3("Remove MFA"), new Paragraph("Enter a code from your authenticator app to turn "
                + "multi-factor authentication off. A wrong code counts toward locking your MFA."), code);

        final Button remove = new Button("Remove MFA", e -> {
            try {
                client.removeMfaEnrollment(code.getValue());
                dialog.close();
                Notifications.success("Multi-factor authentication removed.");
                getUI().ifPresent(ui -> ui.getPage().reload());
            } catch (final ClientException ex) {
                // Philter's message says whether the code was wrong or MFA is locked.
                invalid(code, messageOr(ex, "Multi-factor authentication could not be removed."));
            } catch (final IOException | ServiceUnavailableException ex) {
                invalid(code, SignInMessages.UNAVAILABLE);
            }
        });
        remove.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        dialog.getFooter().add(ViewSupport.cancel(dialog), remove);
        dialog.open();

    }

    // API keys and sign-in sessions.

    private Component apiKeysTab() {

        final Paragraph about = new Paragraph("API keys let scripts and integrations use Philter as you. Philter UI "
                + "does not create keys, because Philter does not let a sign-in session create one. Create a key with "
                + "Philter's API, using a key you already have; ");
        about.add(PhilterDocs.link("see how to create a key.", "api_and_sdks/api/api_keys_api.html#create-a-key"));

        keys.addColumn(ApiKey::getPrefix).setHeader("Key").setAutoWidth(true);
        keys.addColumn(key -> scopeSummary(key.getScopes())).setHeader("Scopes").setFlexGrow(1);
        keys.addColumn(key -> ViewSupport.utc(key.getCreated())).setHeader("Created").setAutoWidth(true)
                .setSortProperty("created");
        keys.addColumn(key -> key.isBootstrap() ? "Bootstrap key" : "").setHeader("").setAutoWidth(true);
        keys.addComponentColumn(key -> ViewSupport.button("Edit scopes", VaadinIcon.KEY,
                "Edit scopes of key " + key.getPrefix(), () -> openEditScopes(key)))
                .setHeader("Scopes").setAutoWidth(true).setFlexGrow(0);
        keys.addComponentColumn(key -> revokeButton(key, "Revoke", "Revoke key " + key.getPrefix()))
                .setHeader("Revoke").setAutoWidth(true).setFlexGrow(0);
        final ListingProvider<ApiKey> keyListing = ListingProvider.bind(keys,
                options -> apiKeys(client.getApiKeys(null, options.session(false))));
        keys.setAllRowsVisible(true);

        signIns.addColumn(key -> device(key.getUserAgent())).setHeader("Device").setAutoWidth(true).setResizable(true)
                .setTooltipGenerator(ApiKey::getUserAgent);
        signIns.addColumn(key -> key.getClientAddress() == null ? "" : key.getClientAddress()).setHeader("Address")
                .setAutoWidth(true).setResizable(true);
        signIns.addColumn(key -> ViewSupport.utc(key.getCreated())).setHeader("Signed in").setAutoWidth(true)
                .setSortProperty("created");
        signIns.addColumn(key -> ViewSupport.utc(key.getLastUsedAt())).setHeader("Last used").setAutoWidth(true);
        signIns.addColumn(key -> ViewSupport.utc(key.getExpiresAt())).setHeader("Ends by").setAutoWidth(true);
        signIns.addComponentColumn(key -> key.isCurrent()
                        ? new Span(THIS_SESSION)
                        : revokeButton(key, "Sign out", "Sign out session " + key.getPrefix()))
                .setHeader("").setAutoWidth(true).setFlexGrow(0);
        final ListingProvider<ApiKey> sessionListing = ListingProvider.bind(signIns,
                options -> apiKeys(client.getApiKeys(null, options.session(true))));
        signIns.setAllRowsVisible(true);

        final Button signOutOthers = new Button("Sign Out Other Sessions", VaadinIcon.SIGN_OUT.create(),
                e -> openSignOutOthers());
        signOutOthers.addThemeVariants(ButtonVariant.LUMO_ERROR);
        signOutOthers.setTooltipText("Sign out every session but this one. Your API keys keep working.");
        final HorizontalLayout sessionsBar = sessionListing.toolbar(signIns, null, "session", "sessions");
        sessionsBar.add(signOutOthers);

        final VerticalLayout layout = new VerticalLayout(about, keyListing.toolbar(keys, null, "key", "keys"), keys,
                new H3("Sign-in Sessions"),
                new Paragraph("Each sign-in, here or in another program that signs in through Philter, has its own "
                        + "session, shown with the device and address it signed in from. Sign out a session you do "
                        + "not recognize or no longer use; to end this one, sign out of Philter UI."),
                sessionsBar, signIns);
        layout.setSizeFull();
        return layout;

    }

    private Component revokeButton(final ApiKey key, final String text, final String label) {
        // Philter refuses to revoke the key making the request; this session ends by signing out.
        if (key.isCurrent()) {
            return new Span();
        }
        final Button revoke = ViewSupport.button(text, VaadinIcon.TRASH, label, () -> openRevoke(key, text));
        revoke.addThemeVariants(ButtonVariant.LUMO_ERROR);
        return revoke;
    }

    private void openRevoke(final ApiKey key, final String action) {

        final Dialog dialog = new Dialog();
        dialog.setWidth("450px");
        dialog.add(new H3(action), new Paragraph(key.isSession()
                ? "Sign out this session? Whatever uses it must sign in again."
                : "Revoke key " + key.getPrefix() + "? Anything that uses it stops working. This cannot be undone."));

        final Button revoke = new Button(action, e -> {
            try {
                client.revokeApiKey(key.getId());
                dialog.close();
                keys.getDataProvider().refreshAll();
                signIns.getDataProvider().refreshAll();
                Notifications.success(key.isSession() ? "Session signed out." : "Key revoked.");
            } catch (final ClientException | ServiceUnavailableException | IOException ex) {
                Notifications.failure(ex, "The key could not be revoked.");
            }
        });
        revoke.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        dialog.getFooter().add(ViewSupport.cancel(dialog), revoke);
        dialog.open();

    }

    private void openSignOutOthers() {

        final Dialog dialog = new Dialog();
        dialog.setWidth("450px");
        dialog.add(new H3("Sign Out Other Sessions"), new Paragraph("Sign out every session but this one? "
                + "Wherever they are used, they must sign in again. You stay signed in here, and your API keys keep "
                + "working."));

        final Button signOut = new Button("Sign Out Others", e -> {
            try {
                final long revoked = client.signOutOtherSessions();
                dialog.close();
                signIns.getDataProvider().refreshAll();
                Notifications.success(otherSessionsSignedOut(revoked));
            } catch (final ClientException | ServiceUnavailableException | IOException ex) {
                Notifications.failure(ex, "The other sessions could not be signed out.");
            }
        });
        signOut.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        dialog.getFooter().add(ViewSupport.cancel(dialog), signOut);
        dialog.open();

    }

    /** Edits a key's scopes. A sign-in session can only remove scopes, so only the key's current ones are offered. */
    private void openEditScopes(final ApiKey key) {

        final List<ApiKeyScopeDescription> catalog;
        try {
            catalog = ViewSupport.orEmpty(client.listApiKeyScopes().getScopes());
        } catch (final ClientException | ServiceUnavailableException | IOException e) {
            Notifications.failure(e, "The scopes could not be read.");
            return;
        }
        final Map<String, String> descriptions = catalog.stream().collect(Collectors.toMap(
                ApiKeyScopeDescription::getName, ApiKeyScopeDescription::getDescription, (a, b) -> a));
        final Set<String> current = new LinkedHashSet<>(ViewSupport.orEmpty(key.getScopes()));

        final CheckboxGroup<String> scopes = new CheckboxGroup<>("Scopes");
        scopes.setItems(catalog.stream().map(ApiKeyScopeDescription::getName).toList());
        scopes.setItemLabelGenerator(name -> name + (descriptions.get(name) == null ? "" : ": " + descriptions.get(name)));
        scopes.setItemEnabledProvider(current::contains);
        scopes.setValue(current);

        final Dialog dialog = new Dialog();
        dialog.setWidth("650px");
        dialog.add(new H3("Edit Scopes: " + key.getPrefix()), new Paragraph("Clear the scopes this key no longer "
                + "needs. A sign-in session can remove scopes but not add them; to give a key more, create a new key "
                + "with Philter's API."), scopes);

        final Button save = new Button("Save", e -> {
            final String problem = scopeChangeProblem(current, scopes.getValue());
            if (problem != null) {
                scopes.setErrorMessage(problem);
                scopes.setInvalid(true);
                return;
            }
            if (scopes.getValue().equals(current)) {
                dialog.close();
                return;
            }
            try {
                client.setApiKeyScopes(key.getId(), List.copyOf(scopes.getValue()));
                dialog.close();
                keys.getDataProvider().refreshAll();
                Notifications.success("Scopes updated.");
            } catch (final ClientException | ServiceUnavailableException | IOException ex) {
                Notifications.failure(ex, "The scopes could not be changed.");
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(ViewSupport.cancel(dialog), save);
        dialog.open();

    }

    // Webhook.

    private Component webhookTab() {

        final Webhook webhook = ViewSupport.unchecked(client::getWebhook);
        webhookSet = webhook != null && webhook.getUrl() != null && !webhook.getUrl().isBlank();
        webhookSecretSet = webhookSet && webhook.isSecretSet();

        final Paragraph about = new Paragraph("Philter can call a URL you choose when an asynchronous redaction "
                + "completes or fails, signing each call with a shared secret. ");
        about.add(PhilterDocs.link("Learn more about webhooks.", "api_and_sdks/api/webhooks.html"));

        final Span status = new Span(webhookStatus(webhookSet, webhookSecretSet));

        final TextField url = new TextField("Webhook URL");
        url.setWidthFull();
        url.setPlaceholder("https://hooks.example.com/philter");
        if (webhookSet) {
            url.setValue(webhook.getUrl());
        }
        final PasswordField secret = new PasswordField("Secret");
        secret.setWidthFull();
        secret.setRevealButtonVisible(true);
        final Integer minimumSecret = ViewSupport.rules(client).webhookSecretMinimumLength();
        secret.setHelperText(secretHelp(minimumSecret, webhookSecretSet));
        final Button generate = new Button("Generate", e -> secret.setValue(generateSecret(minimumSecret)));

        final HorizontalLayout secretRow = new HorizontalLayout(secret, generate);
        secretRow.setWidthFull();
        secretRow.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);
        secretRow.expand(secret);

        final Span testResult = new Span();
        final Button test = new Button("Send Test Event", VaadinIcon.PAPERPLANE.create(), e -> {
            try {
                testResult.setText(testOutcome(client.testWebhook()));
            } catch (final ClientException ex) {
                testResult.setText(ErrorReasons.WEBHOOK_NOT_SET.equals(ex.getReason())
                        ? "No webhook is set." : messageOr(ex, "Philter could not test the webhook."));
            } catch (final IOException | ServiceUnavailableException ex) {
                testResult.setText("");
                Notifications.failure(ex, "The webhook could not be tested.");
            }
            deliveries.getDataProvider().refreshAll();
        });
        test.setTooltipText("Send one signed test event to the webhook now and show how the receiver answered.");
        test.setEnabled(webhookSet);

        final Button save = new Button("Save Webhook", e -> {
            url.setInvalid(false);
            secret.setInvalid(false);
            final String problem = webhookProblem(url.getValue(), secret.getValue(), minimumSecret, webhookSecretSet);
            if (problem != null) {
                if (problem.startsWith("Enter a URL") || problem.startsWith("The URL")) {
                    invalid(url, problem);
                } else {
                    invalid(secret, problem);
                }
                return;
            }
            try {
                final Webhook saved = client.setWebhook(url.getValue().trim(), secretToSend(secret.getValue()));
                webhookSet = true;
                webhookSecretSet = saved == null || saved.isSecretSet();
                secret.clear();
                secret.setHelperText(secretHelp(minimumSecret, webhookSecretSet));
                status.setText(webhookStatus(webhookSet, webhookSecretSet));
                test.setEnabled(true);
                Notifications.success("Webhook saved.");
            } catch (final ClientException ex) {
                invalid(url, messageOr(ex, "Philter did not accept the webhook."));
            } catch (final IOException | ServiceUnavailableException ex) {
                Notifications.failure(ex, "The webhook could not be saved.");
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        final Button remove = new Button("Remove Webhook", e -> {
            try {
                client.removeWebhook();
                url.clear();
                secret.clear();
                webhookSet = false;
                webhookSecretSet = false;
                secret.setHelperText(secretHelp(minimumSecret, false));
                status.setText(webhookStatus(false, false));
                test.setEnabled(false);
                testResult.setText("");
                Notifications.success("Webhook removed.");
            } catch (final ClientException | ServiceUnavailableException | IOException ex) {
                Notifications.failure(ex, "The webhook could not be removed.");
            }
        });
        remove.addThemeVariants(ButtonVariant.LUMO_ERROR);

        final VerticalLayout settings = new VerticalLayout(about, status, url, secretRow,
                new HorizontalLayout(save, test, remove), testResult);
        settings.setPadding(false);
        settings.setMaxWidth("700px");

        final VerticalLayout layout = new VerticalLayout(settings, deliveriesSection());
        layout.setSizeFull();
        return layout;

    }

    /** The webhook's delivery history, newest first, a page at a time. */
    private Component deliveriesSection() {

        deliveries.addColumn(d -> ViewSupport.utc(d.getCreatedAtText())).setHeader("Queued").setAutoWidth(true)
                .setSortProperty("created");
        deliveries.addColumn(d -> eventLabel(d.getEvent())).setHeader("Event").setAutoWidth(true);
        deliveries.addColumn(WebhookDelivery::getDocumentId).setHeader("Document").setResizable(true);
        deliveries.addColumn(d -> statusLabel(d.getStatus())).setHeader("Status").setAutoWidth(true);
        deliveries.addColumn(WebhookDelivery::getAttempts).setHeader("Attempts").setAutoWidth(true);
        deliveries.addColumn(AccountView::lastError).setHeader("Last Error").setResizable(true);
        deliveries.addColumn(AccountView::nextAttempt).setHeader("Next Attempt").setAutoWidth(true);
        final ListingProvider<WebhookDelivery> listing = ListingProvider.bind(deliveries, options -> {
            final GetWebhookDeliveriesResponse page = client.getWebhookDeliveries(null, options);
            return new Listing<>(page.getDeliveries(), page.getTotal());
        });
        deliveries.setMinHeight("250px");
        deliveries.setSizeFull();

        final Button refresh = new Button("Refresh", VaadinIcon.REFRESH.create(),
                e -> deliveries.getDataProvider().refreshAll());
        refresh.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        final HorizontalLayout heading = new HorizontalLayout(new H3("Deliveries"), refresh);
        heading.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);

        final VerticalLayout section = new VerticalLayout(heading,
                new Span("Each event Philter queued for your webhook, newest first, with how its delivery went. "
                        + "Test events are not listed."), listing.toolbar(deliveries, null, "delivery", "deliveries"),
                deliveries);
        section.setPadding(false);
        section.setSizeFull();
        return section;

    }

    private static Listing<ApiKey> apiKeys(final GetApiKeysResponse page) {
        return new Listing<>(page.getApiKeys(), page.getTotal());
    }

    // Logic the tests cover.

    /** How the session in use is marked in the sessions grid, in place of its sign-out button. */
    static final String THIS_SESSION = "This session";

    /** What signing out the other sessions did. */
    static String otherSessionsSignedOut(final long revoked) {
        if (revoked == 0) {
            return "There were no other sessions to sign out.";
        }
        return (revoked == 1 ? "1 other session" : revoked + " other sessions") + " signed out.";
    }

    /**
     * A session's user agent in words, such as "Firefox on Windows", or the user agent itself when it is not a
     * browser this recognizes. Blank when the client sent none.
     */
    static String device(final String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "";
        }
        if (userAgent.startsWith("Java-http-client")) {
            return "Java HTTP client";
        }
        final String browser = browser(userAgent);
        final String system = operatingSystem(userAgent);
        if (browser == null) {
            return userAgent;
        }
        return system == null ? browser : browser + " on " + system;
    }

    private static String browser(final String ua) {
        if (ua.contains("Edg/")) {
            return "Edge";
        }
        if (ua.contains("OPR/")) {
            return "Opera";
        }
        if (ua.contains("Firefox/") || ua.contains("FxiOS/")) {
            return "Firefox";
        }
        if (ua.contains("Chrome/") || ua.contains("CriOS/")) {
            return "Chrome";
        }
        if (ua.contains("Safari/") && ua.contains("Version/")) {
            return "Safari";
        }
        return null;
    }

    private static String operatingSystem(final String ua) {
        if (ua.contains("iPhone") || ua.contains("iPad")) {
            return "iOS";
        }
        if (ua.contains("Android")) {
            return "Android";
        }
        if (ua.contains("CrOS")) {
            return "ChromeOS";
        }
        if (ua.contains("Windows")) {
            return "Windows";
        }
        if (ua.contains("Mac OS X") || ua.contains("Macintosh")) {
            return "macOS";
        }
        if (ua.contains("Linux")) {
            return "Linux";
        }
        return null;
    }

    /** What is wrong with a scope change, or {@code null}: a session may only remove scopes, and a key needs one. */
    static String scopeChangeProblem(final Set<String> current, final Set<String> selected) {
        if (selected.isEmpty()) {
            return "A key needs at least one scope. To stop using it, revoke it.";
        }
        final Set<String> added = new HashSet<>(selected);
        added.removeAll(current);
        if (!added.isEmpty()) {
            return "A sign-in session cannot add scopes to a key: " + String.join(", ", added) + ".";
        }
        return null;
    }

    /**
     * What is wrong with a webhook before it is sent, or {@code null}. A blank secret keeps the one already set,
     * so it is needed only when none is. Philter also checks its allowlist.
     */
    static String webhookProblem(final String url, final String secret, final Integer minimumSecret,
                                 final boolean secretSet) {
        if (url == null || url.isBlank()) {
            return "Enter a URL.";
        }
        try {
            final URI uri = new URI(url.trim());
            if (uri.getScheme() == null || !(uri.getScheme().equalsIgnoreCase("http") || uri.getScheme().equalsIgnoreCase("https"))
                    || uri.getHost() == null) {
                return "The URL must be an http or https address.";
            }
        } catch (final URISyntaxException e) {
            return "The URL must be an http or https address.";
        }
        if (secret == null || secret.isEmpty()) {
            return secretSet ? null : "Enter a secret; Philter requires one for a new webhook.";
        }
        if (minimumSecret != null && secret.length() < minimumSecret) {
            return "Enter a secret of at least " + minimumSecret + " characters.";
        }
        return null;
    }

    /** The secret to send: {@code null}, which keeps the one already set, when the field is blank. */
    static String secretToSend(final String secret) {
        return secret == null || secret.isEmpty() ? null : secret;
    }

    /** The secret field's helper text. */
    static String secretHelp(final Integer minimumSecret, final boolean secretSet) {
        final String length = minimumSecret == null ? "" : "At least " + minimumSecret + " characters. ";
        final String keep = secretSet ? "Leave blank to keep the current secret. " : "Required for a new webhook. ";
        return length + keep + "Philter never shows it again, so copy a new one to the receiving service before you "
                + "leave this page.";
    }

    /** What the webhook section says is set. */
    static String webhookStatus(final boolean set, final boolean secretSet) {
        if (!set) {
            return "No webhook is set.";
        }
        return secretSet ? "A webhook is set, with a secret." : "A webhook is set.";
    }

    /** How a test event went: whether the receiver accepted it, its status or Philter's error, and how long it took. */
    static String testOutcome(final WebhookTestResult result) {
        final StringBuilder text = new StringBuilder(result.isDelivered() ? "Delivered" : "Not delivered");
        if (result.getStatusCode() != null) {
            text.append(": the receiver answered HTTP ").append(result.getStatusCode());
        }
        text.append(result.getStatusCode() != null ? " in " : " after ").append(result.getDurationMillis()).append(" ms.");
        // With a status, Philter's error only repeats it; without one, it says why the receiver was not reached.
        if (!result.isDelivered() && result.getStatusCode() == null && result.getError() != null
                && !result.getError().isBlank()) {
            text.append(' ').append(result.getError().trim());
            if (!result.getError().trim().endsWith(".")) {
                text.append('.');
            }
        }
        if (result.getDeliveryId() != null) {
            text.append(" Delivery ID: ").append(result.getDeliveryId()).append('.');
        }
        return text.toString();
    }

    /** A delivery's status as a person reads it. */
    static String statusLabel(final String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case WebhookDelivery.PENDING -> "Pending";
            case WebhookDelivery.PROCESSING -> "Sending";
            case WebhookDelivery.DELIVERED -> "Delivered";
            case WebhookDelivery.FAILED -> "Failed";
            default -> status;
        };
    }

    /** An event as a person reads it. */
    static String eventLabel(final String event) {
        if (event == null) {
            return "";
        }
        return switch (event) {
            case WebhookEvents.DOCUMENT_REDACTION_COMPLETE -> "Redaction complete";
            case WebhookEvents.DOCUMENT_REDACTION_FAILED -> "Redaction failed";
            case WebhookEvents.WEBHOOK_TEST -> "Test";
            default -> event;
        };
    }

    /** Why a pending or failed delivery's last attempt failed; blank once it is delivered. */
    static String lastError(final WebhookDelivery delivery) {
        return unsettled(delivery) && delivery.getLastError() != null ? delivery.getLastError() : "";
    }

    /** When a pending or failed delivery is next tried, in UTC; blank when there is no next attempt. */
    static String nextAttempt(final WebhookDelivery delivery) {
        return unsettled(delivery) ? ViewSupport.utc(delivery.getNextAttemptAtText()) : "";
    }

    private static boolean unsettled(final WebhookDelivery delivery) {
        return WebhookDelivery.PENDING.equals(delivery.getStatus()) || WebhookDelivery.FAILED.equals(delivery.getStatus());
    }

    /**
     * A random secret, without characters that are easy to confuse: {@link #GENERATED_SECRET_LENGTH} characters, or
     * Philter's minimum when that is longer.
     */
    static String generateSecret(final Integer minimum) {
        final int length = minimum == null ? GENERATED_SECRET_LENGTH : Math.max(GENERATED_SECRET_LENGTH, minimum);
        final StringBuilder secret = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            secret.append(SECRET_CHARACTERS.charAt(RANDOM.nextInt(SECRET_CHARACTERS.length())));
        }
        return secret.toString();
    }

    /** A key's scopes for the grid: all of them, or how many when there are more than six. */
    static String scopeSummary(final List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return "";
        }
        return scopes.size() > 6 ? scopes.size() + " scopes" : String.join(", ", scopes);
    }

    private static TextField readOnly(final String label, final String value) {
        final TextField field = new TextField(label);
        field.setWidthFull();
        field.setReadOnly(true);
        field.setValue(value == null ? "" : value);
        return field;
    }

    private static TextField codeField() {
        final TextField code = new TextField("Authentication code");
        code.setPlaceholder("123456");
        code.setMaxLength(6);
        code.setAllowedCharPattern("[0-9]");
        return code;
    }

    private static String messageOr(final ClientException e, final String fallback) {
        return e.getErrorMessage() == null ? fallback : e.getErrorMessage();
    }

    private static void invalid(final com.vaadin.flow.component.HasValidation field, final String message) {
        field.setErrorMessage(message);
        field.setInvalid(true);
    }

}
