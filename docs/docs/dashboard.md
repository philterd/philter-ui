# Dashboard

Philter UI is served at `https://your-philter-ui-host:8081`. [Sign in](sign_in.md) with your Philter username and password.

Every page works through Philter's [API]({$ philter_docs }/api_and_sdks/api.html) using your session key, so each action is subject to your role and recorded in Philter's [audit log]({$ philter_docs }/auditing.html) under your user. Use the API, not Philter UI, for redacting documents in production.

## Testing Philter

The **Dashboard** home page submits text or a PDF to Philter with a policy you choose and shows the redacted result. Use it to tune a policy before deploying it.

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

**My Account** lets each user:

* **Change their password.** See [password requirements](sign_in.md#password-requirements).
* **Enroll in or remove MFA**, when an administrator has made it available. Removing it takes a code from your authenticator app. See [Multi-factor authentication](sign_in.md#multi-factor-authentication-mfa).
* **Review API keys**: list your keys, including session keys, narrow a key's [scopes]({$ philter_docs }/account/api_keys.html#scopes) with **Edit scopes**, and revoke keys. Philter UI does not create API keys; see [Creating API keys](#creating-api-keys).
* **Set a [webhook]({$ philter_docs }/api_and_sdks/api/webhooks.html)** URL and secret to receive a signed notification when an asynchronous redaction completes or fails.

## Administration

The **Admin** section appears only for administrators.

### Users

The Users grid lists all users, including deactivated ones.

* **Add a user** with a username, an optional email address, a role (`admin` or `user`), and a temporary password. The user sets their own password at first sign-in. Leave the password empty for an account that will use API keys only and never sign in.
* **Reset a password.** The user must set a new one at their next sign-in, and their current sessions end.
* **Sign a user out everywhere** by revoking their session keys.
* **Set a role** to `admin` or `user`. You cannot change your own role, and the last active administrator cannot be demoted.
* **Unlock MFA** or **Disable MFA** for a user who is locked out or has lost their authenticator. See [Multi-factor authentication](sign_in.md#multi-factor-authentication-mfa).
* **Deactivate a user.** Users are deactivated, never deleted. A deactivated user cannot sign in and their API keys stop working, but their data, including policies and redaction ledger, is kept. You cannot deactivate your own account or the last active administrator.
* **Reactivate a user** to restore sign-in and API access with nothing lost.

### Settings

**Admin Settings** changes deployment-wide [settings]({$ philter_docs }/api_and_sdks/api/settings_api.html), including whether MFA is available or required.

### Audit Log

**Audit Log** exports Philter's [audit log]({$ philter_docs }/auditing.html) as CSV for a range of whole days, up to 31 days per export. Audit events never contain sensitive values.

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
