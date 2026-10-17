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
import ai.philterd.philter.model.AuditEvent;
import ai.philterd.philter.model.AuditLogExport;
import ai.philterd.philter.model.GetAuditLogResponse;
import ai.philterd.philter.model.GetUsersResponse;
import ai.philterd.philter.model.ListOptions;
import ai.philterd.philter.model.SigningKey;
import ai.philterd.philter.model.UpdateAdminSettingsRequest;
import ai.philterd.philter.model.User;
import ai.philterd.philter.model.exceptions.ClientException;
import ai.philterd.philter.model.exceptions.ErrorReasons;
import ai.philterd.philter.model.exceptions.ServiceUnavailableException;
import ai.philterd.ui.model.Listing;
import ai.philterd.ui.security.PhilterClients;
import ai.philterd.ui.security.PhilterUnreachableException;
import ai.philterd.ui.security.PhilterUser;
import ai.philterd.ui.security.Roles;
import ai.philterd.ui.security.Sessions;
import ai.philterd.ui.model.PhilterRules;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AttachmentType;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import jakarta.annotation.security.RolesAllowed;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Administration through Philter's API: users, deployment settings and the signing key, and the audit log.
 * Philter checks that the caller is an administrator on every request.
 */
@Route(value = "admin", layout = MainLayout.class)
@PageTitle("Admin | Philter UI")
@RolesAllowed(Roles.ADMIN)
public class AdminView extends VerticalLayout {

    /** The most events one export holds, since Philter UI builds the file in memory. */
    static final int MAX_AUDIT_EXPORT_EVENTS = 100_000;

    private final transient PhilterClient client;
    private final PhilterUser user;
    private final Grid<User> users = new Grid<>();

    public AdminView(final PhilterClients clients) {

        this.user = Sessions.currentUser().orElseThrow();
        this.client = clients.forUser(user);

        setSizeFull();

        final TabSheet tabs = new TabSheet();
        tabs.add("Users", usersTab());
        tabs.add("Settings", settingsTab());
        tabs.add("Audit Log", auditTab());
        tabs.setSizeFull();

        add(new H2("Admin"), tabs);

    }

    // Users.

    private Component usersTab() {

        final Button add = new Button("Add User", VaadinIcon.PLUS.create(), e -> openAddUser());
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        users.addColumn(User::getUsername).setHeader("Username").setAutoWidth(true).setSortProperty("username");
        users.addColumn(u -> u.getEmail() == null ? "" : u.getEmail()).setHeader("Email").setAutoWidth(true);
        users.addColumn(User::getRole).setHeader("Role").setAutoWidth(true);
        users.addColumn(AdminView::status).setHeader("Status").setAutoWidth(true);
        users.addColumn(AdminView::passwordState).setHeader("Password").setAutoWidth(true);
        users.addColumn(AdminView::mfaState).setHeader("MFA").setAutoWidth(true);
        users.addComponentColumn(this::actions).setHeader("").setAutoWidth(true).setFlexGrow(0);
        final Select<String> role = new Select<>();
        role.setLabel("Role");
        role.setItems(roleFilters(ViewSupport.rules(client)));
        role.setItemLabelGenerator(r -> r.isEmpty() ? "All roles" : r);
        role.setValue("");
        final Select<String> status = new Select<>();
        status.setLabel("Status");
        status.setItems(STATUS_FILTERS);
        status.setValue(STATUS_FILTERS.get(0));

        final ListingProvider<User> listing = ListingProvider.bind(users, options -> {
            final GetUsersResponse page = client.getUsers(userFilters(options, role.getValue(), status.getValue()));
            return new Listing<>(page.getUsers(), page.getTotal());
        });
        role.addValueChangeListener(e -> users.getDataProvider().refreshAll());
        status.addValueChangeListener(e -> users.getDataProvider().refreshAll());
        users.setSizeFull();

        final HorizontalLayout toolbar = listing.toolbar(users, "Search by username", "user", "users");
        toolbar.addComponentAtIndex(1, role);
        toolbar.addComponentAtIndex(2, status);
        final HorizontalLayout top = new HorizontalLayout(add, toolbar);
        top.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);

        final VerticalLayout layout = new VerticalLayout(top, users);
        layout.setSizeFull();
        return layout;

    }

    private Component actions(final User row) {

        final boolean self = row.getUsername().equals(user.getUsername());
        final Button button = ViewSupport.button("Actions", VaadinIcon.ELLIPSIS_DOTS_V,
                "Actions for " + row.getUsername(), () -> { });
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        final ContextMenu items = new ContextMenu(button);
        items.setOpenOnClick(true);

        final MenuItem reset = items.addItem("Reset password", e -> openResetPassword(row));
        final MenuItem role = items.addItem("Set role", e -> openSetRole(row));
        items.addItem("Edit email", e -> openEditEmail(row));
        final MenuItem signOut = items.addItem("Sign out everywhere", e -> confirm("Sign Out Everywhere",
                "Sign " + row.getUsername() + " out of every session? Their API keys keep working.", "Sign out", false,
                () -> {
                    final int revoked = client.revokeSessionKeys(row.getUsername());
                    Notifications.success(revoked == 1 ? "1 session ended." : revoked + " sessions ended.");
                }));
        final MenuItem unlock = items.addItem("Unlock MFA", e -> confirm("Unlock MFA", "Unlock multi-factor "
                + "authentication for " + row.getUsername() + "? They can enter a code again; their enrollment is "
                + "unchanged.", "Unlock", false, () -> {
                    client.unlockUserMfa(row.getUsername());
                    Notifications.success("MFA unlocked for " + row.getUsername() + ".");
                }));
        // Philter does not report whether sign-in is locked, so this is always offered, and a user who is not
        // locked is reported as information.
        items.addItem("Clear sign-in lock", e -> confirm("Clear Sign-in Lock", "Clear the sign-in lock on "
                + row.getUsername() + "? They can sign in again now. The lock returns if failed sign-ins continue.",
                "Clear lock", false, () -> {
                    try {
                        client.unlockUserSignIn(row.getUsername());
                        Notifications.success("Sign-in lock cleared for " + row.getUsername() + ".");
                    } catch (final ClientException ex) {
                        if (!signInNotLocked(ex)) {
                            throw ex;
                        }
                        Notifications.info(row.getUsername() + "'s sign-in is not locked. There is nothing to clear.");
                    }
                }));
        final MenuItem removeMfa = items.addItem("Disable MFA", e -> confirm("Disable MFA", "Remove "
                + row.getUsername() + "'s authenticator? Use this for someone who has lost it. They set MFA up "
                + "again at their next sign-in if it is required, and otherwise sign in with their password alone "
                + "until they do.", "Disable MFA", true, () -> {
                    client.removeUserMfa(row.getUsername());
                    Notifications.success("MFA disabled for " + row.getUsername() + ".");
                }));
        final MenuItem activation = row.isActive()
                ? items.addItem("Deactivate", e -> confirm("Deactivate User", "Deactivate " + row.getUsername()
                        + "? They can no longer sign in and their API keys stop working. Their data, including "
                        + "policies, contexts, lists, and redaction ledgers, is kept, and you can reactivate them "
                        + "at any time.", "Deactivate", true, () -> {
                            client.deactivateUser(row.getUsername());
                            Notifications.success(row.getUsername() + " deactivated.");
                        }))
                : items.addItem("Reactivate", e -> confirm("Reactivate User", "Reactivate " + row.getUsername()
                        + "? They can sign in again and their API keys work again.", "Reactivate", false, () -> {
                            client.reactivateUser(row.getUsername());
                            Notifications.success(row.getUsername() + " reactivated.");
                        }));

        // Philter refuses these on the administrator's own user, or Philter UI keeps them for My Account.
        disable(reset, self, "Change your own password on My Account.");
        disable(role, self, "You cannot change your own role here.");
        disable(signOut, self, "Sign out your other sessions on My Account.");
        disable(removeMfa, self, "Remove your own MFA on My Account.");
        disable(activation, self, "You cannot deactivate yourself.");
        disable(reset, !row.isActive(), "Reactivate the user first.");
        disable(role, !row.isActive(), "Reactivate the user first.");
        disable(unlock, !row.isMfaLocked(), "MFA is not locked.");
        disable(removeMfa, !row.isMfaEnabled(), "The user has not set up MFA.");

        return button;

    }

    /** Whether Philter refused to clear a sign-in lock because the user's sign-in is not locked. */
    static boolean signInNotLocked(final ClientException e) {
        return e.getStatusCode() == 409;
    }

    private static void disable(final MenuItem item, final boolean condition, final String reason) {
        if (condition && item.isEnabled()) {
            item.setEnabled(false);
            item.getElement().setAttribute("title", reason);
        }
    }

    private void openAddUser() {

        final TextField username = new TextField("Username");
        username.setWidthFull();
        username.setRequired(true);
        final TextField email = new TextField("Email (optional)");
        email.setWidthFull();
        final PhilterRules rules = ViewSupport.rules(client);
        final ComboBox<String> role = roleField(rules.roles().contains("user") ? "user" : null, rules);
        final PasswordField password = new PasswordField("Temporary password (optional)");
        password.setHelperText(rules.passwordRules() + " Leave empty for a user who will use API keys only and never "
                + "sign in.");

        final Dialog dialog = new Dialog();
        dialog.setWidth("600px");
        dialog.add(new H3("Add User"), new Paragraph("A user given a password must change it at first sign-in. "
                + "Philter creates a default policy and context for the new user."),
                new VerticalLayout(username, email, role, passwordRow(password, null, rules)));

        final Button create = new Button("Add User", e -> {
            username.setInvalid(false);
            email.setInvalid(false);
            password.setInvalid(false);
            if (username.getValue().isBlank()) {
                invalid(username, "Enter a username.");
                return;
            }
            final String emailProblem = emailProblem(email.getValue());
            if (emailProblem != null) {
                invalid(email, emailProblem);
                return;
            }
            final String chosen = password.getValue().isEmpty() ? null : password.getValue();
            if (chosen != null && rules.passwordProblem(chosen) != null) {
                invalid(password, rules.passwordProblem(chosen));
                return;
            }
            try {
                client.createUser(username.getValue().trim(), blankToNull(email.getValue()), role.getValue(), chosen);
                dialog.close();
                users.getDataProvider().refreshAll();
                Notifications.success(username.getValue().trim() + " added.");
            } catch (final ClientException ex) {
                final String message = messageOr(ex, "Philter did not add the user.");
                switch (refusedField(ex)) {
                    case PASSWORD -> invalid(password, message);
                    case EMAIL -> invalid(email, message);
                    case ROLE -> invalid(role, message);
                    default -> invalid(username, message);
                }
            } catch (final IOException | ServiceUnavailableException ex) {
                Notifications.failure(ex, "The user could not be added.");
            }
        });
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(ViewSupport.cancel(dialog), create);
        dialog.open();

    }

    private void openResetPassword(final User row) {

        final PhilterRules rules = ViewSupport.rules(client);
        final PasswordField password = new PasswordField("New password");
        password.setHelperText(rules.passwordRules());
        final PasswordField confirm = new PasswordField("Confirm new password");
        confirm.setWidthFull();

        final Dialog dialog = new Dialog();
        dialog.setWidth("600px");
        dialog.add(new H3("Reset Password"), new Paragraph("Set a temporary password for " + row.getUsername()
                + ". Their sessions end, and they must choose a new password at their next sign-in. Their API keys "
                + "keep working."), new VerticalLayout(passwordRow(password, confirm, rules), confirm));

        final Button save = new Button("Reset Password", e -> {
            password.setInvalid(false);
            confirm.setInvalid(false);
            final String problem = rules.passwordProblem(password.getValue());
            if (problem != null) {
                invalid(password, problem);
                return;
            }
            if (!password.getValue().equals(confirm.getValue())) {
                invalid(confirm, "The passwords do not match.");
                return;
            }
            try {
                client.setPassword(row.getUsername(), password.getValue());
                dialog.close();
                users.getDataProvider().refreshAll();
                Notifications.success("Password reset for " + row.getUsername() + ".");
            } catch (final ClientException ex) {
                invalid(password, messageOr(ex, "Philter did not reset the password."));
            } catch (final IOException | ServiceUnavailableException ex) {
                Notifications.failure(ex, "The password could not be reset.");
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(ViewSupport.cancel(dialog), save);
        dialog.open();

    }

    private void openSetRole(final User row) {

        final ComboBox<String> role = roleField(row.getRole(), ViewSupport.rules(client));
        final Dialog dialog = new Dialog();
        dialog.setWidth("450px");
        dialog.add(new H3("Set Role"), new Paragraph("Choose the role for " + row.getUsername() + "."), role);

        final Button save = new Button("Save", e -> {
            if (Objects.equals(role.getValue(), row.getRole())) {
                dialog.close();
                return;
            }
            try {
                client.setUserRole(row.getUsername(), role.getValue());
                dialog.close();
                users.getDataProvider().refreshAll();
                Notifications.success(row.getUsername() + " is now " + role.getValue() + ".");
            } catch (final ClientException ex) {
                invalid(role, messageOr(ex, "Philter did not change the role."));
            } catch (final IOException | ServiceUnavailableException ex) {
                Notifications.failure(ex, "The role could not be changed.");
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(ViewSupport.cancel(dialog), save);
        dialog.open();

    }

    private void openEditEmail(final User row) {

        final TextField email = new TextField("Email");
        email.setWidthFull();
        email.setValue(row.getEmail() == null ? "" : row.getEmail());
        email.setClearButtonVisible(true);
        email.setHelperText("Leave empty to remove the address. It is not used to sign in.");

        final Dialog dialog = new Dialog();
        dialog.setWidth("450px");
        dialog.add(new H3("Edit Email"), new Paragraph("Set, change, or remove the email address for "
                + row.getUsername() + "."), email);

        final Button save = new Button("Save", e -> {
            email.setInvalid(false);
            if (Objects.equals(blankToNull(email.getValue()), blankToNull(row.getEmail()))) {
                dialog.close();
                return;
            }
            try {
                final String problem = saveEmail(client::setUserEmail, row.getUsername(), email.getValue());
                if (problem != null) {
                    invalid(email, problem);
                    return;
                }
                dialog.close();
                users.getDataProvider().refreshAll();
                Notifications.success(blankToNull(email.getValue()) == null
                        ? "Email removed for " + row.getUsername() + "."
                        : "Email changed for " + row.getUsername() + ".");
            } catch (final ClientException | ServiceUnavailableException | IOException ex) {
                Notifications.failure(ex, "The email address could not be changed.");
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(ViewSupport.cancel(dialog), save);
        dialog.open();

    }

    /** A call to Philter that returns nothing the dialog needs. */
    private interface Action {
        void run() throws IOException;
    }

    private void confirm(final String title, final String text, final String action, final boolean destructive,
                         final Action call) {

        final Dialog dialog = new Dialog();
        dialog.setWidth("500px");
        dialog.add(new H3(title), new Paragraph(text));

        final Button go = new Button(action, e -> {
            try {
                call.run();
                dialog.close();
                users.getDataProvider().refreshAll();
            } catch (final ClientException | ServiceUnavailableException | IOException ex) {
                Notifications.failure(ex, "Philter could not do that.");
            }
        });
        go.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        if (destructive) {
            go.addThemeVariants(ButtonVariant.LUMO_ERROR);
        }
        dialog.getFooter().add(ViewSupport.cancel(dialog), go);
        dialog.open();

    }

    // Settings and the signing key.

    private Component settingsTab() {

        final AdminSettings settings;
        try {
            settings = ViewSupport.unchecked(client::getAdminSettings);
        } catch (final ClientException | ServiceUnavailableException | PhilterUnreachableException e) {
            // The rest of the page still works; this tab says why it is empty.
            return new Paragraph("Philter's settings could not be read. " + ViewSupport.why(e));
        }

        final Checkbox diffuse = new Checkbox("Record PII counts for differential-privacy reporting",
                settings.isDiffuseCountsEnabled());

        final Checkbox phield = new Checkbox("Publish PII counts to Phield for drift monitoring", settings.isPhieldEnabled());
        final TextField phieldUrl = textField("Phield URL", settings.getPhieldUrl());
        phieldUrl.setPlaceholder("https://phield.example.com");
        final TextField phieldSource = textField("Phield source ID", settings.getPhieldSourceId());
        phieldSource.setHelperText("Blank sets philter.");
        final TextField phieldOrganization = textField("Phield organization", settings.getPhieldOrganization());
        phieldOrganization.setHelperText("Blank sets philter.");
        final PasswordField phieldKey = new PasswordField("Phield API key");
        phieldKey.setWidth("480px");
        phieldKey.setPlaceholder(settings.isPhieldApiKeySet() ? "A key is set; type a new one to replace it" : "No key is set");
        phieldKey.setHelperText("Needed only when Phield requires one. Philter never returns it.");
        final Checkbox removePhieldKey = new Checkbox("Remove the Phield API key");
        removePhieldKey.setVisible(settings.isPhieldApiKeySet());
        removePhieldKey.addValueChangeListener(e -> phieldKey.setEnabled(!e.getValue()));

        final Checkbox signing = new Checkbox("Sign every text redaction and explain response (X-Philter-Signature)",
                settings.isSigningEnabled());

        final Checkbox mfaAvailable = new Checkbox("Users may set up multi-factor authentication", settings.isMfaAvailable());
        final Checkbox mfaRequired = new Checkbox("Every user must set up multi-factor authentication at sign-in",
                settings.isMfaRequired());
        mfaRequired.setEnabled(settings.isMfaAvailable());
        mfaAvailable.addValueChangeListener(e -> {
            mfaRequired.setEnabled(e.getValue());
            if (!e.getValue()) {
                mfaRequired.setValue(false);
            }
        });

        final TextField allowlist = textField("Webhook destination allowlist", settings.getWebhookAllowlist());
        allowlist.setWidth("640px");
        allowlist.setPlaceholder("hooks.example.com, 203.0.113.0/24");
        allowlist.setHelperText("Comma-separated hostnames, IP addresses, and CIDR ranges a user's webhook may "
                + "point to. Empty allows any public address and refuses private, loopback, and link-local ones.");

        final Paragraph fixed = new Paragraph("Set when Philter starts, and shown here only: cross-user access by "
                + "administrators is " + onOff(settings.isCrossUserAccessEnabled())
                + " (ADMIN_CROSS_USER_ACCESS_ENABLED), and ledger deletion is "
                + onOff(settings.isLedgerDeletionEnabled()) + " (LEDGER_DELETION_ENABLED).");

        final Button save = new Button("Save Settings", e -> {
            allowlist.setInvalid(false);
            final UpdateAdminSettingsRequest request = settingsChanges(settings, new SettingsForm(diffuse.getValue(),
                    signing.getValue(), allowlist.getValue(), phield.getValue(), phieldUrl.getValue(),
                    phieldSource.getValue(), phieldOrganization.getValue(),
                    phieldKeyChange(removePhieldKey.getValue(), phieldKey.getValue()), mfaAvailable.getValue(),
                    mfaRequired.getValue()));
            if (request == null) {
                Notifications.success("Nothing changed.");
                return;
            }
            try {
                final AdminSettings saved = client.updateAdminSettings(request);
                phieldKey.clear();
                removePhieldKey.setValue(false);
                removePhieldKey.setVisible(saved.isPhieldApiKeySet());
                phieldKey.setPlaceholder(saved.isPhieldApiKeySet() ? "A key is set; type a new one to replace it" : "No key is set");
                copy(saved, settings);
                Notifications.success("Settings saved.");
                for (final String warning : ViewSupport.orEmpty(saved.getWarnings())) {
                    Notifications.warning(warning);
                }
            } catch (final ClientException | ServiceUnavailableException | IOException ex) {
                Notifications.failure(ex, "The settings could not be saved.");
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        final VerticalLayout layout = new VerticalLayout(
                new H3("Multi-Factor Authentication"), mfaAvailable, mfaRequired,
                new H3("Output Signing"), signing,
                new H3("Webhook Destinations"), allowlist,
                new H3("PII Counts"), diffuse, phield, phieldUrl, phieldSource, phieldOrganization, phieldKey,
                removePhieldKey,
                save, fixed, signingKeySection(settings));
        layout.setMaxWidth("900px");
        return layout;

    }

    private Component signingKeySection(final AdminSettings settings) {

        final TextField keyId = textField("Active key ID", null);
        keyId.setReadOnly(true);
        final TextField fingerprint = textField("Public key fingerprint (SHA-256)", null);
        fingerprint.setReadOnly(true);
        fingerprint.setWidth("640px");
        final Runnable load = () -> {
            try {
                final SigningKey key = client.getSigningKeyDetails();
                keyId.setValue(key.getKeyId() == null ? "" : key.getKeyId());
                fingerprint.setValue(key.getFingerprint() == null ? "" : key.getFingerprint());
            } catch (final ClientException | ServiceUnavailableException | IOException ex) {
                Notifications.failure(ex, "The signing key could not be read.");
            }
        };
        load.run();

        final Button regenerate = new Button("Regenerate Signing Key", VaadinIcon.REFRESH.create(), e -> {
            final Dialog dialog = new Dialog();
            dialog.setWidth("500px");
            dialog.add(new H3("Regenerate Signing Key"), new Paragraph("Philter makes a new signing key the active "
                    + "one. Earlier keys stay available, so signatures and ledger entries made with them still verify. "
                    + "Verifiers should look up each signature's key ID rather than keep one key."));
            final Button go = new Button("Regenerate", ev -> {
                try {
                    client.regenerateSigningKey();
                    dialog.close();
                    load.run();
                    Notifications.success("Signing key regenerated.");
                } catch (final ClientException | ServiceUnavailableException | IOException ex) {
                    Notifications.failure(ex, "The signing key could not be regenerated.");
                }
            });
            go.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
            dialog.getFooter().add(ViewSupport.cancel(dialog), go);
            dialog.open();
        });
        regenerate.addThemeVariants(ButtonVariant.LUMO_ERROR);
        final VerticalLayout layout = new VerticalLayout(new H3("Signing Key"), keyId, fingerprint, regenerate);
        layout.setPadding(false);
        if (settings.isSigningKeyExternallyManaged()) {
            regenerate.setEnabled(false);
            layout.add(new Paragraph("The signing key is managed with PHILTER_SIGNING_KEY_PATH. To change it, replace "
                    + "that file and restart every Philter instance."));
        }
        return layout;

    }

    // Audit log.

    /** The filters the audit log is browsed with, in the form Philter takes them. */
    record AuditFilter(String event, Instant from, Instant to, String owner) {
        static final AuditFilter NONE = new AuditFilter(null, null, null, null);
    }

    /** Reads a page of the audit log, so browsing it can be tested without Philter. */
    interface AuditSource {
        GetAuditLogResponse read(AuditFilter filter, int offset, int limit) throws IOException;
    }

    private Component auditTab() {

        final VerticalLayout layout = new VerticalLayout(auditBrowser(), auditExport());
        layout.setSizeFull();
        return layout;

    }

    /** The audit log, newest first, a page at a time, filtered by event type, days, and user. */
    private Component auditBrowser() {

        final TextField event = new TextField("Event type");
        event.setPlaceholder("policy_deleted");
        event.setClearButtonVisible(true);
        final DatePicker from = new DatePicker("From (UTC)");
        from.setClearButtonVisible(true);
        final DatePicker to = new DatePicker("To (UTC)");
        to.setClearButtonVisible(true);
        final TextField user = new TextField("User");
        user.setPlaceholder("username");
        user.setClearButtonVisible(true);

        final Span total = new Span();
        final Span problem = new Span();
        problem.getStyle().set("color", "var(--lumo-error-text-color)");
        final AuditFilter[] filter = {AuditFilter.NONE};
        final AuditSource source = (f, offset, limit) -> client.getAuditLog(f.event(), f.from(), f.to(), f.owner(),
                offset, limit);

        final Grid<AuditEvent> events = new Grid<>();
        events.addColumn(e -> ViewSupport.utc(e.getTimestamp())).setHeader("Time").setAutoWidth(true).setFlexGrow(0);
        events.addColumn(AuditEvent::getEvent).setHeader("Event").setAutoWidth(true).setResizable(true);
        events.addColumn(AdminView::actor).setHeader("User").setAutoWidth(true).setResizable(true);
        events.addColumn(AuditEvent::getClientIpAddress).setHeader("Client Address").setAutoWidth(true).setResizable(true);
        events.addColumn(AuditEvent::getSource).setHeader("Source").setAutoWidth(true).setResizable(true);
        events.addColumn(AuditEvent::getDetails).setHeader("Details").setResizable(true);
        // One request per page; Philter's total is shown from it rather than read with another request, since
        // Philter records every read of the log.
        events.setItems(query -> {
            try {
                final AuditPage page = readAudit(source, filter[0], query.getOffset(), query.getLimit());
                total.setText(auditTotal(page.total()));
                problem.setText("");
                return page.events().stream();
            } catch (final ClientException ex) {
                // An event type Philter does not record, or a user it does not know, is shown beside the filters.
                if (!ErrorReasons.INVALID_REQUEST.equals(ex.getReason()) && !ErrorReasons.NOT_FOUND.equals(ex.getReason())) {
                    throw ex;
                }
                total.setText("");
                problem.setText(ex.getErrorMessage() == null ? "Philter refused the filter." : ex.getErrorMessage());
                return Stream.empty();
            } catch (final IOException ex) {
                throw new PhilterUnreachableException(ex);
            }
        });
        events.setPageSize(Listing.MAX_LIMIT);
        events.setMinHeight("300px");
        events.setSizeFull();

        final Button apply = new Button("Apply", VaadinIcon.FILTER.create(), e -> {
            final String rangeProblem = auditFilterProblem(from.getValue(), to.getValue());
            if (rangeProblem != null) {
                problem.setText(rangeProblem);
                return;
            }
            filter[0] = auditFilter(event.getValue(), from.getValue(), to.getValue(), user.getValue());
            events.getDataProvider().refreshAll();
        });
        apply.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        final Button refresh = new Button("Refresh", VaadinIcon.REFRESH.create(), e -> events.getDataProvider().refreshAll());
        refresh.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        final HorizontalLayout filters = new HorizontalLayout(event, from, to, user, apply, refresh);
        filters.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);
        filters.getStyle().set("flex-wrap", "wrap");

        final Paragraph about = new Paragraph("Philter's audit log, newest first, across the whole deployment. Filter by "
                + "one event type, by whole days in UTC, or by the user who acted; some events record what they "
                + "concern rather than who acted, so a user filter leaves them out. Each page read is itself recorded "
                + "in the audit log. ");
        about.add(PhilterDocs.link("Learn more about the audit log.", "auditing.html"));

        final VerticalLayout layout = new VerticalLayout(new H3("Browse"), about, filters,
                new HorizontalLayout(total, problem), events);
        layout.setPadding(false);
        layout.setSizeFull();
        return layout;

    }

    private Component auditExport() {

        final LocalDate today = LocalDate.now();
        final PhilterRules rules = ViewSupport.rules(client);
        final Integer window = rules.auditExportMaximumWindowDays();
        final DatePicker from = new DatePicker("From", today.minusDays(window == null ? 6 : window));
        final DatePicker to = new DatePicker("To", today);
        final Anchor downloader = new Anchor();
        downloader.getElement().getStyle().set("display", "none");

        final Button export = new Button("Export Audit Log (CSV)", VaadinIcon.DOWNLOAD.create());
        export.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        export.setDisableOnClick(true);
        export.addClickListener(e -> {
            try {
                final String problem = auditRangeProblem(from.getValue(), to.getValue(), window);
                if (problem != null) {
                    Notifications.failure(problem);
                    return;
                }
                final AuditExport read = readExport(cursor -> client.exportAuditLogAfter(from.getValue(),
                        to.getValue(), null, cursor, rules.auditExportMaximumPageSize()), MAX_AUDIT_EXPORT_EVENTS);
                final String zone = read.zone();
                final boolean more = read.capped();
                final AuditCsv joined = joinCsvPages(read.pages());
                final int rows = joined.events();
                final byte[] csv = joined.csv().getBytes(StandardCharsets.UTF_8);
                final String name = "philter-audit-log-" + from.getValue() + "-to-" + to.getValue() + ".csv";
                downloader.setHref(DownloadHandler.fromInputStream(event -> new DownloadResponse(
                        new ByteArrayInputStream(csv), name, "text/csv", csv.length)), AttachmentType.DOWNLOAD);
                downloader.getElement().executeJs("this.click()");
                Notifications.success(rows + (rows == 1 ? " event" : " events") + " exported"
                        + (zone == null ? "." : ", with the days read in " + zone + "."));
                if (more) {
                    Notifications.warning("The export stopped at " + MAX_AUDIT_EXPORT_EVENTS + " events, the most "
                            + "Philter UI exports at once. Export a shorter range to get the rest.");
                }
            } catch (final ClientException | ServiceUnavailableException | IOException ex) {
                Notifications.failure(ex, "The audit log could not be exported.");
            } finally {
                export.setEnabled(true);
            }
        });

        final HorizontalLayout range = new HorizontalLayout(from, to, export);
        range.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);

        final Paragraph about = new Paragraph("Export Philter's audit log as one CSV file for a range of whole days"
                + (window == null ? "" : ", up to " + (window + 1) + " days") + ", most recent first. The days are read in Philter's "
                + "time zone, and times in the file are UTC. An export holds at most " + MAX_AUDIT_EXPORT_EVENTS + " events. "
                + "The export is itself recorded in the audit log. ");
        final VerticalLayout layout = new VerticalLayout(new H3("Export"), about, range, downloader);
        layout.setPadding(false);
        layout.setMaxWidth("900px");
        return layout;

    }

    // Logic the tests cover.

    /** The status filter's choices, the first showing everyone. */
    static final List<String> STATUS_FILTERS = List.of("All statuses", "Active", "Deactivated");

    /** The role filter's choices: every role (empty) and then each role Philter has. */
    static List<String> roleFilters(final PhilterRules rules) {
        final List<String> choices = new ArrayList<>();
        choices.add("");
        choices.addAll(rules.roles());
        return choices;
    }

    /** The users listing request with the role and status filters, either of which may show everyone. */
    static ListOptions userFilters(final ListOptions options, final String role, final String status) {
        return options.role(role == null || role.isEmpty() ? null : role)
                .active("Active".equals(status) ? Boolean.TRUE : "Deactivated".equals(status) ? Boolean.FALSE : null);
    }

    /** The longest email address Philter accepts. */
    static final int MAX_EMAIL_LENGTH = 254;

    /** Philter's email rule: one {@code @}, something on each side, a dot in the domain, and no whitespace. */
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /**
     * What is wrong with an email address under Philter's rule, or {@code null} if it is blank (no address) or
     * meets it: one {@code @} with something on each side, a dot in the domain, no whitespace, and at most
     * {@link #MAX_EMAIL_LENGTH} characters after trimming. Philter checks it again.
     */
    static String emailProblem(final String email) {
        final String value = blankToNull(email);
        if (value == null) {
            return null;
        }
        if (value.length() > MAX_EMAIL_LENGTH) {
            return "The email address must be at most " + MAX_EMAIL_LENGTH + " characters.";
        }
        return EMAIL.matcher(value).matches() ? null : "The email address is not valid.";
    }

    /** Sets a user's email address in Philter, so a change can be tested without Philter. */
    interface EmailSetter {
        User set(String username, String email) throws IOException;
    }

    /**
     * Sets, changes, or (when blank) removes a user's email address. Returns why it was refused, by this check or
     * Philter's, or {@code null} once it is saved.
     */
    static String saveEmail(final EmailSetter setter, final String username, final String email) throws IOException {
        final String problem = emailProblem(email);
        if (problem != null) {
            return problem;
        }
        try {
            setter.set(username, blankToNull(email));
            return null;
        } catch (final ClientException e) {
            if (!ErrorReasons.INVALID_REQUEST.equals(e.getReason())) {
                throw e;
            }
            return messageOr(e, "Philter did not accept the email address.");
        }
    }

    /** The New User fields a refusal from Philter can be about. */
    enum UserField { USERNAME, EMAIL, ROLE, PASSWORD }

    /**
     * Which field Philter's refusal to create a user is about: the one Philter names in {@code field}, and the
     * username otherwise, such as when it is already in use.
     */
    static UserField refusedField(final ClientException e) {
        if (e.getField() == null) {
            return UserField.USERNAME;
        }
        return switch (e.getField()) {
            case "password" -> UserField.PASSWORD;
            case "email" -> UserField.EMAIL;
            case "role" -> UserField.ROLE;
            default -> UserField.USERNAME;
        };
    }

    /** A range of the audit log and how many events match the filters across every page. */
    record AuditPage(List<AuditEvent> events, long total) {
    }

    /** Reads the events from {@code offset}, in Philter-sized pages, with the total from the last page read. */
    static AuditPage readAudit(final AuditSource source, final AuditFilter filter, final int offset, final int limit)
            throws IOException {
        final Listing<AuditEvent> events = Listing.read(offset, limit, (start, count) -> {
            final GetAuditLogResponse page = source.read(filter, start, count);
            return new Listing<>(page.getEvents(), page.getTotal());
        });
        return new AuditPage(events.items(), events.total());
    }

    /**
     * The filters as Philter takes them: blank fields are left out, and the days are whole days in UTC, so
     * {@code to} is the start of the day after the last one.
     */
    static AuditFilter auditFilter(final String event, final LocalDate from, final LocalDate to, final String user) {
        return new AuditFilter(blankToNull(event),
                from == null ? null : from.atStartOfDay(ZoneOffset.UTC).toInstant(),
                to == null ? null : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
                blankToNull(user));
    }

    /** What is wrong with the days to browse, or {@code null}. Either may be left open. */
    static String auditFilterProblem(final LocalDate from, final LocalDate to) {
        return from != null && to != null && from.isAfter(to) ? "From must be on or before To." : null;
    }

    /** Who performed an event: their username, else the id Philter stored, else a dash. */
    static String actor(final AuditEvent event) {
        if (event.getUsername() != null && !event.getUsername().isBlank()) {
            return event.getUsername();
        }
        return event.getApiKeyId() == null || event.getApiKeyId().isBlank() ? "-" : event.getApiKeyId();
    }

    static String auditTotal(final long total) {
        return total == 1 ? "1 event" : total + " events";
    }

    static String status(final User u) {
        return u.isActive() ? "Active" : "Deactivated";
    }

    static String passwordState(final User u) {
        if (!u.isPasswordSet()) {
            return "None (API keys only)";
        }
        return u.isPasswordChangeRequired() ? "Must change" : "Set";
    }

    static String mfaState(final User u) {
        if (u.isMfaLocked()) {
            return "Locked";
        }
        return u.isMfaEnabled() ? "On" : "Off";
    }

    /** What the settings form holds when Save is pressed. The Phield key is blank unless one was typed. */
    record SettingsForm(boolean diffuseCountsEnabled, boolean signingEnabled, String webhookAllowlist,
                        boolean phieldEnabled, String phieldUrl, String phieldSourceId, String phieldOrganization,
                        String phieldApiKey, boolean mfaAvailable, boolean mfaRequired) {
    }

    /**
     * The settings that differ from what Philter returned, or {@code null} when nothing changed. The Phield
     * API key is sent only when one was typed, or as an empty string to remove it.
     */
    static UpdateAdminSettingsRequest settingsChanges(final AdminSettings current, final SettingsForm form) {

        final UpdateAdminSettingsRequest request = new UpdateAdminSettingsRequest();
        boolean changed = false;

        if (form.diffuseCountsEnabled() != current.isDiffuseCountsEnabled()) {
            request.setDiffuseCountsEnabled(form.diffuseCountsEnabled());
            changed = true;
        }
        if (form.signingEnabled() != current.isSigningEnabled()) {
            request.setSigningEnabled(form.signingEnabled());
            changed = true;
        }
        if (!text(form.webhookAllowlist()).equals(text(current.getWebhookAllowlist()))) {
            request.setWebhookAllowlist(text(form.webhookAllowlist()));
            changed = true;
        }
        if (form.phieldEnabled() != current.isPhieldEnabled()) {
            request.setPhieldEnabled(form.phieldEnabled());
            changed = true;
        }
        if (!text(form.phieldUrl()).equals(text(current.getPhieldUrl()))) {
            request.setPhieldUrl(text(form.phieldUrl()));
            changed = true;
        }
        if (!text(form.phieldSourceId()).equals(text(current.getPhieldSourceId()))) {
            request.setPhieldSourceId(text(form.phieldSourceId()));
            changed = true;
        }
        if (!text(form.phieldOrganization()).equals(text(current.getPhieldOrganization()))) {
            request.setPhieldOrganization(text(form.phieldOrganization()));
            changed = true;
        }
        if (form.phieldApiKey() != null && (!form.phieldApiKey().isEmpty() || current.isPhieldApiKeySet())) {
            request.setPhieldApiKey(form.phieldApiKey());
            changed = true;
        }
        if (form.mfaAvailable() != current.isMfaAvailable()) {
            request.setMfaAvailable(form.mfaAvailable());
            changed = true;
        }
        if (form.mfaRequired() != current.isMfaRequired()) {
            request.setMfaRequired(form.mfaRequired());
            changed = true;
        }

        return changed ? request : null;

    }

    /**
     * The Phield API key to send: empty to remove it, the typed key to replace it, or {@code null} to leave it
     * alone. An untouched password field is empty, not {@code null}, so it must not be sent as a removal.
     */
    static String phieldKeyChange(final boolean remove, final String typed) {
        if (remove) {
            return "";
        }
        return typed == null || typed.isEmpty() ? null : typed;
    }

    /** What is wrong with an audit log date range, or {@code null}. */
    static String auditRangeProblem(final LocalDate from, final LocalDate to, final Integer maximumWindowDays) {
        if (from == null || to == null) {
            return "Choose both a From and a To date.";
        }
        if (from.isAfter(to)) {
            return "From must be on or before To.";
        }
        if (maximumWindowDays != null && ChronoUnit.DAYS.between(from, to) > maximumWindowDays) {
            return "An export can cover at most " + (maximumWindowDays + 1) + " days.";
        }
        return null;
    }

    /** Reads one page of an export, after the given cursor, or the first page for {@code null}. */
    interface ExportSource {
        AuditLogExport read(String cursor) throws IOException;
    }

    /** An export's pages, the time zone Philter read the days in, and whether it stopped at the cap with more to read. */
    record AuditExport(List<String> pages, String zone, boolean capped) {
    }

    /**
     * Reads an export by cursor: the first page without one, then each page after the previous page's next cursor,
     * so each event in the range is read once even while events are recorded. Stops when a page is not truncated,
     * has no next cursor, or {@code maxEvents} have been read.
     */
    static AuditExport readExport(final ExportSource source, final int maxEvents) throws IOException {
        final List<String> pages = new ArrayList<>();
        int fetched = 0;
        String zone = null;
        String cursor = null;
        boolean capped = false;
        do {
            final AuditLogExport page = source.read(cursor);
            pages.add(page.getCsv());
            fetched += page.getRows();
            zone = page.getTimeZone();
            final String next = page.isTruncated() ? page.getNextCursor() : null;
            capped = next != null && fetched >= maxEvents;
            cursor = next != null && !capped ? next : null;
        } while (cursor != null);
        return new AuditExport(pages, zone, capped);
    }

    /** An export joined into one CSV, and how many events it holds. */
    record AuditCsv(String csv, int events) {
    }

    /**
     * Joins the pages of an export into one CSV: the first page's header, then every record of every page. Paging
     * by cursor returns each event once, so records that look the same are different events and are all kept.
     */
    static AuditCsv joinCsvPages(final List<String> pages) {
        final StringBuilder csv = new StringBuilder();
        boolean header = false;
        int events = 0;
        for (final String page : pages) {
            final List<String> records = csvRecords(page == null ? "" : page);
            if (records.isEmpty()) {
                continue;
            }
            if (!header) {
                csv.append(records.get(0)).append('\n');
                header = true;
            }
            for (final String record : records.subList(1, records.size())) {
                csv.append(record).append('\n');
                events++;
            }
        }
        return new AuditCsv(csv.toString(), events);
    }

    /** Splits CSV into records without their line endings. A newline inside a quoted field stays in its record. */
    static List<String> csvRecords(final String csv) {
        final List<String> records = new ArrayList<>();
        final StringBuilder record = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < csv.length(); i++) {
            final char c = csv.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            }
            if (c == '\n' && !quoted) {
                if (!record.isEmpty() && record.charAt(record.length() - 1) == '\r') {
                    record.setLength(record.length() - 1);
                }
                records.add(record.toString());
                record.setLength(0);
            } else {
                record.append(c);
            }
        }
        if (!record.isEmpty()) {
            records.add(record.toString());
        }
        return records;
    }

    // Helpers.

    private static void copy(final AdminSettings from, final AdminSettings to) {
        to.setDiffuseCountsEnabled(from.isDiffuseCountsEnabled());
        to.setSigningEnabled(from.isSigningEnabled());
        to.setWebhookAllowlist(from.getWebhookAllowlist());
        to.setPhieldEnabled(from.isPhieldEnabled());
        to.setPhieldUrl(from.getPhieldUrl());
        to.setPhieldSourceId(from.getPhieldSourceId());
        to.setPhieldOrganization(from.getPhieldOrganization());
        to.setPhieldApiKeySet(from.isPhieldApiKeySet());
        to.setMfaAvailable(from.isMfaAvailable());
        to.setMfaRequired(from.isMfaRequired());
    }

    private static String text(final String value) {
        return value == null ? "" : value.trim();
    }

    private static String onOff(final boolean value) {
        return value ? "on" : "off";
    }

    private static TextField textField(final String label, final String value) {
        final TextField field = new TextField(label);
        field.setWidth("480px");
        field.setValue(value == null ? "" : value);
        return field;
    }

    /** A role chooser offering the roles Philter reports, or only the current one when they cannot be read. */
    private static ComboBox<String> roleField(final String value, final PhilterRules rules) {
        final ComboBox<String> role = new ComboBox<>("Role");
        role.setItems(roleChoices(value, rules));
        role.setValue(value);
        role.setAllowCustomValue(false);
        role.setRequired(true);
        role.setWidthFull();
        return role;
    }

    /**
     * A password field with a Generate button, which fills it, and the confirmation field if there is one, with
     * a random password and shows it to copy.
     */
    private static HorizontalLayout passwordRow(final PasswordField password, final PasswordField confirm,
                                                final PhilterRules rules) {
        password.setWidthFull();
        final Button generate = new Button("Generate", VaadinIcon.MAGIC.create(), e -> {
            final String generated = Passwords.generate(rules);
            password.setValue(generated);
            password.setRevealButtonVisible(true);
            if (confirm != null) {
                confirm.setValue(generated);
            }
        });
        final HorizontalLayout row = new HorizontalLayout(password, generate);
        row.setWidthFull();
        row.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);
        row.expand(password);
        return row;
    }

    private static String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String messageOr(final ClientException e, final String fallback) {
        return e.getErrorMessage() == null ? fallback : e.getErrorMessage();
    }

    private static void invalid(final com.vaadin.flow.component.HasValidation field, final String message) {
        field.setErrorMessage(message);
        field.setInvalid(true);
    }


    /** The roles to offer: Philter's, or the current one alone when Philter's cannot be read. */
    static List<String> roleChoices(final String current, final PhilterRules rules) {
        if (!rules.roles().isEmpty()) {
            return rules.roles();
        }
        return current == null ? List.of() : List.of(current);
    }

}
