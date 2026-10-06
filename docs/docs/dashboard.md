# Dashboard

Philter UI is served at `https://your-philter-ui-host:8081`. [Sign in](sign_in.md) with your Philter username and password.

Every page works through Philter's [API]({$ philter_docs }/api_and_sdks/api.html) using your session key, so each action is subject to your role and recorded in Philter's [audit log]({$ philter_docs }/auditing.html) under your user. Use the API, not Philter UI, for redacting documents in production.

## Testing Philter

The **Dashboard** is the home page. It redacts text or a PDF with one of your policies so you can see what the policy finds, and tune it before deploying it.

* Pick a **Policy**. The list holds your own policies; `default` is selected when you have one, or your only policy when you have just one.
* **Text** redacts what you type or paste and shows the redacted text below it.
* **PDF** takes one PDF of at most 50 MB and returns the redacted PDF to download, named after the upload with `-redacted` added. Philter UI holds the upload in memory and does not write it to disk. Philter UI waits up to `DOCUMENT_TIMEOUT_SECONDS` (default 300) for Philter to finish; see [Running](index.md#running).

No [context]({$ philter_docs }/redaction/contexts.html) is sent, so token replacements are not stored for later requests and no redaction ledger is written. Each redaction is an ordinary request to Philter's [filter API]({$ philter_docs }/api_and_sdks/api/filtering_api.html), recorded under your user like any other.

If your bootstrap API key from `PHILTER_BOOTSTRAP_API_KEY` is still active, the Dashboard reminds you to create a key of your own with Philter's API (see [Creating API keys](#creating-api-keys)) and then revoke the bootstrap key on [My Account](#my-account).

## Policies

**Redaction Policies** manages the [policies]({$ philter_docs }/policies/filter_policies.html) that decide what Philter detects and how it redacts it. On the **My Policies** tab you can:

* **Edit** a policy's JSON, description, and notes. Build a policy in the [policy editor](https://policies.philterd.ai/) and paste its JSON in, or edit the JSON directly. Philter validates the policy when it is saved and the page shows its reason if it refuses. Saving changed JSON creates a new revision; changing only the description or notes does not. A description can have up to 200 characters and notes up to 1000.
* **Create** a policy with **New Policy**, starting from a template. Policy names can have up to 50 letters, digits, dashes, and underscores, cannot start with `managed_`, and are unique per user.
* **Duplicate** a policy under a new name.
* **Delete** a policy. Philter keeps its version history. The `default` policy cannot be deleted.
* **See a policy's history**: view any retained revision, compare two revisions with each change's value before and after, or roll back to an earlier revision. A rollback saves the earlier content as a new revision; no revision is removed.

Terms that are always or never redacted across all of your policies are on the [Always/Never Redact Lists](#alwaysnever-redact-lists) page.

The **Managed Policies** tab lists Philter's ready-made policies, such as common PII, financial PII, and healthcare PHI, with their descriptions. You can view them and create your own policy from one; managed policies themselves cannot be changed or deleted.

Administrators also see an **All Policies** tab listing every user's policies and their owners, including users who have been deactivated, and can view each policy's JSON, when Philter's `ADMIN_CROSS_USER_ACCESS_ENABLED` is `true`.

## Custom Lists

**Custom Lists** manages reusable [custom lists]({$ philter_docs }/redaction/custom_lists.html) of terms, such as internal project names or employee IDs, that policies can reference to include or exclude many values at once.

On the **My Custom Lists** tab you can:

* **See** each list's name, description, and number of terms.
* **Create** a list with **New Custom List**: a name, an optional description, and the items, one per line. Blank lines are ignored. A list can have up to 100 items of up to 50 characters each. List names are unique per user and cannot contain `/`, `\`, `;`, `%`, or control characters, or be `.` or `..`.
* **Edit** a list's items and description. The description is kept unless you change it, and clearing the field removes it.
* **Delete** a list.

A list created before Philter checked names may have a name it no longer allows, such as one containing `/`. Such a list cannot be opened or edited, but it can be deleted.

Administrators also see an **All Custom Lists** tab listing every user's lists with their descriptions, sizes, and owners, when Philter's `ADMIN_CROSS_USER_ACCESS_ENABLED` is `true`.

## Always/Never Redact Lists

**Always/Never Redact Lists** holds two lists of [terms]({$ philter_docs }/redaction/redact_lists.html) that apply to all of your redactions, whatever the policy: terms that are always redacted, and terms that are never redacted. Each has its own tab.

* **Edit** a list as terms, one per line. Blank lines and surrounding spaces are ignored. Each list can have up to 1000 terms of up to 100 characters each.
* **Save** a list to replace it with what is shown. Saving an empty list clears it. Saving one list keeps the other as Philter has it, including changes made elsewhere, and updates the other tab to match unless you have unsaved edits there.

If Philter refuses a save, the page shows Philter's reason, for example a term that is too long.

## Contexts

**Contexts** manages [redaction contexts]({$ philter_docs }/redaction/contexts.html), which support:

* **Referential integrity:** the same value is replaced with the same stand-in across documents in a context.
* **Disambiguation:** helps resolve which entity type an ambiguous value is.

On the **My Contexts** tab you can:

* **View** a context: its settings, its number of entries, and the entries counted by filter type. Entries stored without a filter type, which only a [context import]({$ philter_docs }/api_and_sdks/api/contexts_api.html#import-a-mapping-table-into-a-context) creates, are counted on a row labeled **No filter type**. The counts sum to the number of entries.
* **Create** a context with **New Context**, optionally with entity type disambiguation and the [redaction ledger]({$ philter_docs }/redaction/ledgers.html) enabled. Context names are unique per user, and Philter limits how many contexts each user can have; see [Capacity]({$ philter_docs }/api_and_sdks/api/contexts_api.html#capacity).
* **Edit** a context's settings. The dialog starts from the context's current settings, and only the settings you change are sent to Philter. Turning the ledger off stops recording redaction evidence for that context.
* **Clear** a context, removing all of its entries but keeping the context.
* **Delete** a context. Philter refuses while a document submitted for redaction with that context is still pending or processing.

Administrators also see an **All Contexts** tab listing every user's contexts and their owners, with **View** for each, when Philter's `ADMIN_CROSS_USER_ACCESS_ENABLED` is `true`.

## Redaction Ledgers

**Redaction Ledgers** shows the tamper-evident, hash-chained record of the redactions made in any context with the [ledger]({$ philter_docs }/redaction/ledgers.html) enabled. On the **My Ledgers** tab you can:

* **Browse and search** your chains by document ID or filename, with the number of chains found.
* **View** a chain: each recorded redaction (type, replacement, position, policy and version, and time, in UTC) and whether the chain verifies. **Chain verified** means the hash chain is intact and every signed entry's signature matches. **Chain invalid** means a hash or signature does not match, and the page says which. **Not verified** means Philter could not check the chain at all, for example because an entry could not be read; that is not evidence of tampering, but the chain is not reported as valid, and Philter returns none of its entries. The original redacted values are never shown.
* **Export** a chain as Philter's JSON export, for evidence or independent verification. The export includes the original redacted values and the signing keys, so store it securely. Each export is recorded in Philter's audit log. If Philter cannot export a chain, for example one it could not check, the page shows Philter's reason.
* **Delete** a document's chain, or **purge** your completed chains older than a number of days. These appear only for administrators, and only when Philter's `LEDGER_DELETION_ENABLED` is `true`; it is `false` by default. An active [legal hold]({$ philter_docs }/redaction/legal_holds.html) blocks the deletion, and the page shows Philter's message naming the holds. Any active hold on your evidence blocks a purge entirely.

Administrators also see an **All Ledgers** tab listing every user's chains and their owners, including users who have been deactivated, with **View** for each, when Philter's `ADMIN_CROSS_USER_ACCESS_ENABLED` is `true`.

## Legal Holds

**Legal Holds** sets and releases [legal holds]({$ philter_docs }/redaction/legal_holds.html): named, audited instructions that block deletion and purge of redaction evidence until released.

On the **My Legal Holds** tab you can:

* **See** your holds with their reference, scope, reason, and when they were set (shown in UTC).
* **Set a hold** with **Set Hold**: a reference, unique among your holds, and what it protects, either one document's ledger chain (enter the document ID) or all of your evidence. A reason is optional. References cannot contain `/`, `\`, `;`, `%`, or control characters, or be `.` or `..`.
* **Release** a hold. Evidence it covered may then become eligible for deletion or purge, if no other hold covers it.

A hold set before Philter checked references may have one it no longer allows, such as one containing `/`. Such a hold can still be released.

If another evidence or hold operation is in progress, Philter refuses to set or release a hold until it finishes, and the page shows Philter's message.

Administrators also see an **All Legal Holds** tab listing every user's holds and their owners, including users who have been deactivated, and can release them, when Philter's `ADMIN_CROSS_USER_ACCESS_ENABLED` is `true`. A deletion blocked by a hold names the holds responsible.

## My Account

**My Account** is where you manage your own account. Its tabs:

* **Account** shows your username, email, and role. **Change Password** asks for your current password and the new one twice; see [password requirements](sign_in.md#password-requirements). Philter then ends all of your sign-in sessions, and you sign in again with the new password. Your API keys keep working.
* **MFA** appears when an administrator has made multi-factor authentication available, or when you are enrolled. **Set Up MFA** shows a QR code and setup key for your authenticator app and asks for a code to confirm; you are then signed out, and sign in again with a code. **Remove MFA** asks for a code from your authenticator app; a wrong code counts toward locking your MFA. See [Multi-factor authentication](sign_in.md#multi-factor-authentication-mfa).
* **API Keys** lists your long-lived [API keys]({$ philter_docs }/account/api_keys.html) with their scopes and when they were created. **Edit scopes** can only remove [scopes]({$ philter_docs }/account/api_keys.html#scopes), since Philter does not let a sign-in session add them, and a key keeps at least one. **Revoke** stops a key working. Philter UI does not create keys; see [Creating API keys](#creating-api-keys). Below the keys, **Sign-in Sessions** lists each of your sessions, here or in another program that signs in through Philter, with when it started, was last used, and ends at the latest. You can sign out any session except the one you are using, which ends when you sign out of Philter UI.
* **Webhook** sets the [webhook]({$ philter_docs }/api_and_sdks/api/webhooks.html) URL Philter calls when an asynchronous redaction completes or fails, and the secret it signs each call with. Philter requires the secret on every save, at least 16 characters, and never shows it again, so copy it to the receiving service when you set it; **Generate** makes a random one. Philter refuses a URL that is not `http` or `https`. When an administrator has set a webhook destination allowlist, only the hosts and address ranges on it are accepted; otherwise any public address is, and a private, loopback, or link-local one is refused. **Remove Webhook** stops the calls.

## Administration

The **Admin** page appears only for administrators. Philter also checks on every request that the caller is an administrator.

### Users

**Users** lists every user, including deactivated ones, with their role, status, password state, and MFA state. A user's **Password** is **Must change** when an administrator set it, and **None (API keys only)** for a user who never signs in.

* **Add User** takes a username, an optional email address, a role (`user` or `admin`), and an optional temporary password; **Generate** fills in a random one to copy. A user given a password must change it at first sign-in. Leave the password empty for a user who will use API keys only. Philter creates a default policy and context for the new user.

Each user's actions menu has:

* **Reset password**: sets a temporary password. The user's sign-in sessions end and they must choose a new password at their next sign-in; their API keys keep working. See [password requirements](sign_in.md#password-requirements).
* **Set role** to `user` or `admin`. Philter refuses to demote the last active administrator.
* **Sign out everywhere**: ends every sign-in session the user has. Their API keys keep working.
* **Unlock MFA** for a user locked out after too many wrong codes. Their enrollment is unchanged.
* **Disable MFA** for a user who has lost their authenticator. They set MFA up again at their next sign-in if it is required, and otherwise sign in with their password alone until they do. See [Multi-factor authentication](sign_in.md#multi-factor-authentication-mfa).
* **Deactivate**: the user cannot sign in and their API keys stop working, but the user and their data, including policies and redaction ledgers, are kept. Users are deactivated, never deleted. Philter refuses to deactivate the last active administrator.
* **Reactivate** restores sign-in and API access with nothing lost.

Your own row offers only **Unlock MFA**: change your password, sign out your other sessions, and remove your MFA on [My Account](#my-account). You cannot change your own role or deactivate yourself here. To reset a deactivated user's password or set their role, reactivate them first.

### Settings

**Settings** changes Philter's deployment-wide [settings]({$ philter_docs }/api_and_sdks/api/settings_api.html). **Save Settings** sends only the settings you changed.

* **Multi-factor authentication**: whether users may set up MFA, and whether every user must. Requiring it needs it to be available.
* **Output signing**: whether Philter signs every text redaction and explain response.
* **Webhook destination allowlist**: the hostnames, IP addresses, and CIDR ranges a user's [webhook](#my-account) may point to. Empty allows any public address and refuses private, loopback, and link-local ones.
* **PII counts**: whether Philter records PII counts for differential-privacy reporting, and whether it publishes them to Phield, with the Phield URL, source ID, organization, and API key. Philter never returns the Phield API key: type a new one to replace it, or tick **Remove the Phield API key**. Philter warns when the key would be sent over `http`.

Cross-user access by administrators (`ADMIN_CROSS_USER_ACCESS_ENABLED`) and ledger deletion (`LEDGER_DELETION_ENABLED`) are set when Philter starts, so the page shows them but cannot change them. Each Philter instance caches the settings for up to `ADMIN_SETTINGS_CACHE_TTL_SECONDS`, so other instances pick up a change when their cache expires.

**Signing Key** shows the active output signing key's ID and SHA-256 fingerprint. **Regenerate Signing Key** makes a new key the active one; earlier keys stay available, so signatures and ledger entries made with them still verify. When the key is managed with `PHILTER_SIGNING_KEY_PATH`, regenerating is disabled: replace that file and restart every Philter instance instead.

### Audit Log

**Audit Log** exports Philter's [audit log]({$ philter_docs }/auditing.html) as one CSV file for a range of whole days, up to 31 days, most recent first. The days are read in Philter's time zone, and times in the file are in UTC. An export holds at most 100,000 events; when a range has more, Philter UI says so, and a shorter range gets the rest. The export is itself recorded in the audit log. Audit events never contain sensitive values.

## Creating API keys

Philter UI does not create API keys. Philter refuses to create a key with the session key Philter UI holds for your sign-in, so a key made from a session cannot outlive it or a password reset. Create long-lived keys for scripts and integrations with Philter's [API Keys API]({$ philter_docs }/api_and_sdks/api/api_keys_api.html#create-a-key), using a long-lived key you already have. On a new deployment, that is the [bootstrap API key]({$ philter_docs }/account/api_keys.html#bootstrapping-an-api-key-for-automation).

Create a key for yourself:

```
curl -k "https://localhost:8080/api/api-keys" \
  -H "Authorization: Bearer <existing-api-key>" \
  -H "Content-Type: application/json" \
  --data '{"scopes":["redact"]}'
```

An administrator creates a key for another user by naming them in the path:

```
curl -k "https://localhost:8080/api/users/<username>/api-keys" \
  -H "Authorization: Bearer <administrator-api-key>" \
  -H "Content-Type: application/json" \
  --data '{"scopes":["redact"]}'
```

The response contains the key in `apiKey`. It is shown once; Philter stores only a hash of it. A key can only be given scopes the key that creates it holds. Once created, the key appears in **My Account**, where you can narrow its scopes or revoke it.
