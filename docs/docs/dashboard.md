# Dashboard

Philter UI is served at `https://your-philter-ui-host:8081`. [Sign in](sign_in.md) with your Philter username and password.

Every page works through Philter's [API]({$ philter_docs }/api_and_sdks/api.html) using your session key, so each action is subject to your role. Philter records changes, and some reads, in its [audit log]({$ philter_docs }/auditing.html); that page lists which events it records and under which principal. Use the API, not Philter UI, for redacting documents in production.

When an action on a page fails, the page shows Philter's explanation, or says that Philter could not be reached. If that happens while a page is opening, the page says so in place of its content, with **Try again** to reload it. When Philter answered the request, the message also shows its **Request ID**, with **Copy** to copy it. Quote the request ID to your administrator: Philter records it on every [audit event]({$ philter_docs }/auditing.html) the request caused, so they can find exactly what happened, for example on the Admin page's **Audit Log**. No request ID is shown when Philter could not be reached. Philter UI also writes each request Philter refused or failed, with its request ID, to its own log; the log has the method and path but never the query or body.

Lists, such as your policies, contexts, or legal holds, are read from Philter a page at a time as you scroll, and each shows how many items there are. Most have a search field above them that Philter matches against the name (or, for legal holds, the reference, and for ledgers, the document ID or filename) as you type, ignoring case. A column whose header can be clicked to sort, such as a name or a date, is sorted by Philter across every page; other columns cannot be sorted.

## Testing Philter

The **Dashboard** is the home page. It redacts text or a PDF with one of your policies so you can see what the policy finds, and tune it before deploying it.

* Pick a **Policy**. The list holds your own policies, read from Philter as you scroll; type to search them by name. `default` is selected when you have one, or your only policy when you have just one.
* **Text** redacts what you type or paste and shows the redacted text below it.
* **PDF** takes one PDF and returns the redacted PDF to download, named after the upload with `-redacted.pdf` in place of `.pdf` and unusual characters replaced by underscores. The file must be a PDF, not only named like one. The page shows the largest PDF it accepts and refuses a larger one before uploading it: Philter's own limit, its `MAX_FILE_SIZE_BYTES` (10 MB by default), which Philter UI reads from Philter, or a smaller limit set with Philter UI's `MAX_PDF_BYTES`. If Philter refuses a document as too large, the page says so and names the limit. Philter UI holds the upload in memory and does not write it to disk. It waits up to `DOCUMENT_TIMEOUT_SECONDS` (default 300) for Philter to finish, and reports that Philter could not be reached if it takes longer; see [Running](index.md#running).

No [context]({$ philter_docs }/redaction/contexts.html) is sent, so token replacements are not stored for later requests and no redaction ledger is written. Each redaction is an ordinary request to Philter's [filter API]({$ philter_docs }/api_and_sdks/api/filtering_api.html), recorded under your user like any other unless Philter's `AUDIT_REDACTION_EVENTS_ENABLED` is `false`.

If your bootstrap API key from `PHILTER_BOOTSTRAP_API_KEY` is still active, the Dashboard reminds you to create a key of your own with Philter's API (see [Creating API keys](#creating-api-keys)) and then revoke the bootstrap key on [My Account](#my-account).

## Policies

**Redaction Policies** manages the [policies]({$ philter_docs }/policies/filter_policies.html) that decide what Philter detects and how it redacts it. On the **My Policies** tab you can:

* **See** each policy's name, description, and when it was created and last updated (shown in UTC). The list can be sorted by name, created, or updated.
* **Edit** a policy's JSON, description, and notes. Build a policy in the [policy editor](https://policies.philterd.ai/) and paste its JSON in, or edit the JSON directly. Philter validates the policy when it is saved and the page shows its reason if it refuses. Saving changed JSON creates a new revision; changing only the description or notes does not. The JSON, description, and notes are saved together in one request, so a refused save changes none of them. If someone else changed the policy, its description, or its notes after you opened the editor, Philter refuses the save rather than overwrite their change, and the page asks you to cancel and edit the policy again to see the latest. The description and notes can be as long as Philter allows (by default 200 and 1000 characters).
* **Create** a policy with **New Policy**, starting from Philter's `default` [policy template]({$ philter_docs }/api_and_sdks/api/policies_api.html#get-a-policy-template), read from Philter when the editor opens, so it follows the policy schema Philter runs. If Philter cannot return the template, the editor opens with an empty policy and says so. The policy, description, and notes are created in one request, so if Philter refuses, nothing is created. Policy names are unique per user and follow Philter's naming rule, shown under the name field: by default up to 50 letters, digits, dashes, and underscores, not starting with `managed_`.
* **Duplicate** a policy under a new name.
* **Delete** a policy. Philter keeps its version history, and the policy appears on the **Deleted Policies** tab. The `default` policy cannot be deleted.
* **See a policy's history**: view its most recent revisions, as many as Philter returns in one page (100 by default), with when each was captured and its **Author**, the user who made it (blank when Philter does not know), compare two revisions using Philter's [diff]({$ philter_docs }/api_and_sdks/api/policies_api.html#diff-two-revisions), which lists each change with its path (a JSON Pointer, as Philter returns it) and its value before and after, or roll back to an earlier revision. A rollback saves the earlier content as a new revision; no revision is removed. If the policy changed after you opened its history, Philter refuses the rollback, so open the history again and retry.

Terms that are always or never redacted across all of your policies are on the [Always/Never Redact Lists](#alwaysnever-redact-lists) page.

The **Deleted Policies** tab lists your deleted policies whose history Philter keeps, with each one's latest revision, when it was deleted, and who deleted it. **Deleted** and **Deleted By** are blank for a deletion Philter did not record. For each you can:

* **See its history**: view and compare its revisions, as for a live policy. A deleted policy cannot be rolled back.
* **Restore** it, as it was when it was deleted, or from any revision in its history. The revision is saved as your policy under the same name at a new revision, its history continues, and it leaves the **Deleted Policies** tab. Philter refuses while you have another policy with that name.

The **Managed Policies** tab lists Philter's ready-made policies, such as common PII, financial PII, and healthcare PHI, with their descriptions. You can view them and create your own policy from one; managed policies themselves cannot be changed or deleted.

Administrators also see an **All Policies** tab listing every user's policies and their owners, including users who have been deactivated, and can view each policy's JSON, when Philter's `ADMIN_CROSS_USER_ACCESS_ENABLED` is `true`.

## Custom Lists

**Custom Lists** manages reusable [custom lists]({$ philter_docs }/redaction/custom_lists.html) of terms, such as internal project names or employee IDs, that policies can reference to include or exclude many values at once.

On the **My Custom Lists** tab you can:

* **See** each list's name, description, and number of terms.
* **Create** a list with **New Custom List**: a name, an optional description of up to 200 characters, and the items, one per line. Blank lines are ignored. A list needs at least one item, and Philter limits how many items it can have and how long each can be (by default 100 items of up to 50 characters). List names are unique per user and follow Philter's rule for names used in requests: by default they cannot contain `/`, `\`, `;`, `%`, or control characters, or be `.` or `..`.
* **Edit** a list's items and description. The description is kept unless you change it, and clearing the field removes it.
* **Delete** a list.

A list created before Philter checked names may have a name it no longer allows, such as one containing `/`. Such a list cannot be opened or edited, but it can be deleted.

Administrators also see an **All Custom Lists** tab listing every user's lists with their descriptions, sizes, and owners, when Philter's `ADMIN_CROSS_USER_ACCESS_ENABLED` is `true`.

## Always/Never Redact Lists

**Always/Never Redact Lists** holds two lists of [terms]({$ philter_docs }/redaction/redact_lists.html) that apply to all of your redactions, whatever the policy: terms that are always redacted, and terms that are never redacted. Each has its own tab.

* **Edit** a list as terms, one per line. Blank lines and surrounding spaces are ignored. Philter limits how many terms each list can have and how long each can be (by default 1000 terms of up to 100 characters).
* **Save** a list to replace it with what is shown. Saving an empty list clears it. Saving one list sends only that list; the other is not changed.
* If someone changed a list after you opened the page, for example in another tab or through the API, Philter refuses the save so neither change is lost. Philter UI then shows the list as Philter now has it and opens a dialog holding your unsaved edits to copy. Apply what you need to the current list and save again.

Philter UI checks the term limits before saving. If Philter refuses a save, the page shows Philter's reason.

## Contexts

**Contexts** manages [redaction contexts]({$ philter_docs }/redaction/contexts.html), which support:

* **Referential integrity:** the same value is replaced with the same stand-in across documents in a context.
* **Disambiguation:** helps resolve which entity type an ambiguous value is.

On the **My Contexts** tab you can:

* **See** each context's name and when it was created (shown in UTC). The list can be sorted by name or created.
* **View** a context: its settings, including its disambiguation scope, its number of entries, and the entries counted by filter type. Entries stored without a filter type, which only a [context import]({$ philter_docs }/api_and_sdks/api/contexts_api.html#import-a-mapping-table-into-a-context) creates, are counted on a row labeled **No filter type**. The counts sum to the number of entries.
* **Create** a context with **New Context**, optionally with entity type disambiguation and its scope, and the [redaction ledger]({$ philter_docs }/redaction/ledgers.html) enabled. Context names are unique per user and follow Philter's rule for names used in requests. Philter limits how many contexts each user can have, counting the `default` context; see [Capacity]({$ philter_docs }/api_and_sdks/api/contexts_api.html#capacity). Once you have that many, **New Context** is disabled and says why until you delete one.
* **Edit** a context's settings. The dialog starts from the context's current settings, and only the settings you change are sent to Philter, so editing another setting leaves the scope as it is. Turning the ledger off stops recording redaction evidence for that context.
* **Clear** a context, removing all of its entries but keeping the context.
* **Delete** a context and its entries. Philter refuses while a document submitted for redaction with that context is still pending or processing.

With entity type disambiguation on, the **Disambiguation scope** says what Philter learns from when it resolves an ambiguous value: **Each document on its own** (the default) learns within each document only, and **Across the context** remembers what it learns and uses it for every later document redacted in the context. The scope is shown only while disambiguation is on, since it has no effect otherwise. See [contexts]({$ philter_docs }/redaction/contexts.html).

Administrators also see an **All Contexts** tab listing every user's contexts and their owners, with **View** for each, when Philter's `ADMIN_CROSS_USER_ACCESS_ENABLED` is `true`.

## Models

**Models** lists the PhEye models the Philter deployment has in its models directory (`PHEYE_MODELS_DIRECTORY`), through Philter's [models API]({$ philter_docs }/api_and_sdks/api/models_api.html), ordered by name. A policy's ph-eye filter uses a model by giving its name as `phEyeConfiguration.modelPath`, and the model then runs inside Philter. The models are the deployment's, the same for every user. The page needs Philter 4.0.0 or later.

Each model shows its name, the encoder it was trained from, the most words it reads in one call (**Maximum Words**), the most words one detected entity can span (**Maximum Entity Width**), and its size. A value the model's configuration does not give is blank. The list is read each time the page opens, so a model an administrator adds appears without restarting Philter or Philter UI. When the deployment has no models, the page says so instead.

## Redaction Ledgers

**Redaction Ledgers** shows the tamper-evident, hash-chained record of the redactions made in any context with the [ledger]({$ philter_docs }/redaction/ledgers.html) enabled. On the **My Ledgers** tab you can:

* **Browse and search** your chains by document ID or filename, with the number of chains found. A chain whose first entry Philter can no longer read, for example after a key change, is marked **Cannot be read**; hover over the mark for Philter's explanation. Such a chain can be opened but not verified or exported.
* **View** a chain: each recorded redaction (type, replacement, position, policy and version, and time, in UTC) and whether the chain verifies. The entries are read from Philter a page at a time as you scroll, with the number of entries in the whole chain, so every entry of a long chain can be reached. The verification always covers the whole chain. **Chain verified** means the hash chain is intact and every signed entry's signature matches. **Chain invalid** means a hash or signature does not match, and the page says which. **Not verified** means Philter could not check the chain at all, for example because an entry could not be read; that is not evidence of tampering, but the chain is not reported as valid, and Philter returns none of its entries. The original redacted values are never shown.
* **Export** a chain as Philter's JSON export, for evidence or independent verification. The export includes the original redacted values, so store it securely, and the public keys the entries were signed with, so it can be verified on its own. Each export is recorded in Philter's audit log. If Philter cannot export a chain, for example one it could not check, the page shows Philter's reason.
* **Delete** a completed document's chain, or **purge** your completed chains older than a number of days (90 unless you change it). These appear only for administrators, and only when Philter's `LEDGER_DELETION_ENABLED` is `true`; it is `false` by default. An active [legal hold]({$ philter_docs }/redaction/legal_holds.html) blocks the deletion, and the page shows Philter's message naming the holds. Any active hold on your evidence blocks a purge entirely.

Administrators also see an **All Ledgers** tab listing every user's chains and their owners, including users who have been deactivated, searchable by document ID or filename, with **View** for each, when Philter's `ADMIN_CROSS_USER_ACCESS_ENABLED` is `true`.

## Legal Holds

**Legal Holds** sets and releases [legal holds]({$ philter_docs }/redaction/legal_holds.html): named, audited instructions that block deletion and purge of redaction evidence until released.

On the **My Legal Holds** tab you can:

* **See** your holds with their reference, scope, the document ID (or your username, for a hold on all of your evidence), reason, and when they were set (shown in UTC).
* **Set a hold** with **Set Hold**: a reference, unique among your holds, and what it protects, either one document's ledger chain (enter the document ID; Philter does not check that the document exists, so a hold can be set before a document is redacted) or all of your evidence. A reason is optional. References follow Philter's rule for names used in requests: by default they cannot contain `/`, `\`, `;`, `%`, or control characters, or be `.` or `..`.
* **Release** a hold. Evidence it covered may then become eligible for deletion or purge, if no other hold covers it.

A hold set before Philter checked references may have one it no longer allows, such as one containing `/`. Such a hold can still be released.

If another evidence or hold operation is in progress, Philter refuses to set or release a hold until it finishes, and the page shows Philter's message.

Administrators also see an **All Legal Holds** tab listing every user's holds and their owners, including users who have been deactivated, and can release them, when Philter's `ADMIN_CROSS_USER_ACCESS_ENABLED` is `true`. A deletion blocked by a hold names the holds responsible.

## My Account

**My Account** is where you manage your own account. Its tabs:

* **Account** shows your username, email, and role. **Change Password** asks for your current password and the new one twice; see [password requirements](sign_in.md#password-requirements). Philter then ends all of your sign-in sessions, and you sign in again with the new password. Your API keys keep working.
* **MFA** appears when an administrator has made multi-factor authentication available, or when you are enrolled. **Set Up MFA** asks for your current password, then shows a QR code and setup key for your authenticator app and asks for a code to confirm; you are then signed out, and sign in again with a code. A wrong password counts toward the sign-in lockout. **Remove MFA** asks for a code from your authenticator app; a wrong code counts toward locking your MFA. See [Multi-factor authentication](sign_in.md#multi-factor-authentication-mfa).
* **API Keys** lists your long-lived [API keys]({$ philter_docs }/account/api_keys.html) by prefix, with their scopes and when they were created; the bootstrap key is marked. **Edit scopes** can only remove [scopes]({$ philter_docs }/account/api_keys.html#scopes), since Philter does not let a sign-in session add them, and a key keeps at least one. **Revoke** stops a key working. Philter UI does not create keys; see [Creating API keys](#creating-api-keys). Below the keys, **Sign-in Sessions** lists each of your sessions, here or in another program that signs in through Philter, so you can review where you are signed in. Each shows its **Device** (the browser and operating system, such as Firefox on Windows, read from the user agent it signed in with; hover for the full user agent), its client **Address**, and when it started, was last used, and ends at the latest; it also ends sooner if it goes unused for Philter's idle timeout. Philter UI passes your browser's user agent when it signs you in, so its sessions show your browser. A program that signs in without passing one shows its own client, such as Java HTTP client. The session you are using is marked **This session**. You can sign out any other session one at a time, or all of them at once with **Sign Out Other Sessions**, which asks first and then says how many were signed out. You stay signed in, and your API keys keep working. The session you are using ends when you sign out of Philter UI.
* **Webhook** sets the [webhook]({$ philter_docs }/api_and_sdks/api/webhooks.html) URL Philter calls when an asynchronous redaction completes or fails, and the secret it signs each call with. A new webhook needs a secret at least as long as Philter's minimum (16 characters by default); **Generate** makes a random one. Philter never shows the secret again, so copy it to the receiving service when you set it. Once a secret is set, leave the field blank to change the URL and keep the current secret, or enter a new one to replace it. Philter refuses a URL that is not `http` or `https`. When an administrator has set a webhook destination allowlist, the URL's host must be on it, and the address it resolves to must be public unless that address is in a listed IP address or range. Without an allowlist, any public address is accepted, and a private, loopback, or link-local one is refused. **Remove Webhook** stops the calls.
    * **Send Test Event** sends one signed test event to the webhook now and shows whether the receiver accepted it, the HTTP status it answered with (or why it could not be reached), how long it took, and the delivery ID to look for in the receiver's logs. It is available once a webhook is set. Test events are sent once, not retried, and are not added to the delivery history.
    * **Deliveries** lists each event Philter queued for your webhook, newest first, a page at a time: when it was queued, the event, the document, its status (Pending, Sending, Delivered, or Failed), and how many attempts were made. For a pending or failed delivery it also shows the last attempt's error and when the next attempt is due, to help find out why a receiver is not getting events. **Refresh** reads the list again. Neither the secret nor the content of a delivery is shown.

## Administration

The **Admin** page appears only for administrators. Philter also checks on every request that the caller is an administrator.

Philter UI checks your role with Philter each time you open a page, so a role an administrator changes takes effect on the next page you open. The tabs listing every user's resources, and ledger deletion, appear when Philter says you may use them. If Philter's limits cannot be read, they stay hidden, and Philter UI leaves its other checks to Philter.

### Users

**Users** lists every user, including deactivated ones, a page at a time with the number that match. Search by username, and filter by **Role** and by **Status** (Active or Deactivated). Each row shows the username, email, role, **Status** (Active or Deactivated), **Password** (Set, Must change, or None (API keys only)), and **MFA** (On, Off, or Locked). **Password** is **Must change** after an administrator sets or resets it, and **None (API keys only)** for a user who never signs in.

* **Add User** takes a username, an optional email address (one `@` with something on each side, a dot in the domain, no spaces, and at most 254 characters), a role (one of those Philter offers, by default `user` or `admin`), and an optional temporary password; **Generate** fills in a random one to copy. A user given a password must change it at first sign-in. Leave the password empty for a user who will use API keys only. Philter creates a default policy and context for the new user.

Each user's actions menu has:

* **Reset password**: sets a temporary password. The user's sign-in sessions end and they must choose a new password at their next sign-in; their API keys keep working. See [password requirements](sign_in.md#password-requirements).
* **Set role** to one of the roles Philter offers.
* **Edit email** sets, changes, or removes a user's email address; leave the field empty to remove it. The address is not used to sign in, so changing it changes nothing else. Philter refuses an address that does not follow the rule above, and the user is left unchanged. Philter refuses to demote the last active administrator.
* **Sign out everywhere**: ends every sign-in session the user has. Their API keys keep working.
* **Unlock MFA** for a user locked out after too many wrong codes. Their enrollment is unchanged.
* **Clear sign-in lock** for a user locked after repeated [failed sign-ins](sign_in.md#failed-sign-ins), so they can sign in again before the lock lapses. The lock returns if failed sign-ins continue. Philter does not report whether a user's sign-in is locked, so the item is always offered; for a user who is not locked, Philter UI says there is nothing to clear.
* **Disable MFA** for a user who has lost their authenticator. They set MFA up again at their next sign-in if it is required, and otherwise sign in with their password alone until they do. See [Multi-factor authentication](sign_in.md#multi-factor-authentication-mfa).
* **Deactivate**: the user cannot sign in and their API keys stop working, but the user and their data, including policies and redaction ledgers, are kept. Users are deactivated, never deleted. Philter refuses to deactivate the last active administrator.
* **Reactivate** restores sign-in and API access with nothing lost.

On your own row only **Clear sign-in lock**, and **Unlock MFA** when your MFA is locked, are available: change your password, sign out your other sessions, and remove your MFA on [My Account](#my-account). You cannot change your own role or deactivate yourself here. To reset a deactivated user's password or set their role, reactivate them first.

### Settings

**Settings** changes Philter's deployment-wide [settings]({$ philter_docs }/api_and_sdks/api/settings_api.html). **Save Settings** sends only the settings you changed.

* **Multi-factor authentication**: whether users may set up MFA, and whether every user must. Requiring it needs it to be available.
* **Output signing**: whether Philter signs every text redaction and explain response.
* **Webhook destination allowlist**: the hostnames, IP addresses, and CIDR ranges a user's [webhook](#my-account) may point to. A listed hostname must still resolve to a public address unless that address is also listed. Empty allows any public address and refuses private, loopback, and link-local ones.
* **PII counts**: whether Philter records PII counts for differential-privacy reporting, and whether it publishes them to Phield, with the Phield URL (required while publishing is on), source ID, organization, and API key. A blank source ID or organization is saved as `philter`. If any value is invalid, nothing is saved. Philter never returns the Phield API key: type a new one to replace it, or tick **Remove the Phield API key**. Philter warns when the key would be sent over `http`.

Cross-user access by administrators (`ADMIN_CROSS_USER_ACCESS_ENABLED`) and ledger deletion (`LEDGER_DELETION_ENABLED`) are set when Philter starts, so the page shows them but cannot change them. Each Philter instance caches the settings for up to `ADMIN_SETTINGS_CACHE_TTL_SECONDS`, so other instances pick up a change when their cache expires.

**Signing Key** shows the active output signing key's ID and SHA-256 fingerprint. **Regenerate Signing Key** makes a new key the active one; earlier keys stay available, so signatures and ledger entries made with them still verify. When the key is managed with `PHILTER_SIGNING_KEY_PATH`, regenerating is disabled: replace that file and restart every Philter instance instead.

### Audit Log

**Audit Log** lets you browse and export Philter's [audit log]({$ philter_docs }/auditing.html), which covers the whole deployment.

**Browse** lists events newest first, a page at a time, with the number of events that match. Each row shows the time (UTC), the event type, the **User** who performed it, the client address, the source, and the details. **User** is the acting user's username, including for a user since deactivated; for an event whose actor is not a user, it shows the ID Philter stored, or a dash when there is none. Filter by one event type (such as `policy_deleted`), by whole days in UTC (**From** and **To**, either of which can be left open), or by the user who acted, then select **Apply**. Some events record what they concern rather than who acted, so a user filter leaves them out. Filtering by another user needs Philter's `ADMIN_CROSS_USER_ACCESS_ENABLED`. When Philter refuses a filter, such as an event type it does not record, the page shows its reason. Each page read is itself recorded in the audit log.

**Export** saves the audit log as one CSV file for a range of whole days, up to the longest range Philter allows (31 days by default), most recent first. The days are read in Philter's time zone, and times in the file are in UTC. An export holds at most 100,000 events; when a range has more, Philter UI says so, and a shorter range gets the rest. Philter UI reads the export a page at a time (up to 1,000 events a page by default), each page continuing exactly where the last ended, so each event in the range appears once even while new events are recorded; events recorded after the export starts are left out, so export again to include them. Events that look the same are separate events and all appear. The export is itself recorded in the audit log, once for each page. Philter does not record redacted values, passwords, or secrets in audit events, but events do include usernames, client addresses, document IDs, and text that callers supply, such as legal hold references.

## Creating API keys

Philter UI does not create API keys. Philter refuses to create a key with the session key Philter UI holds for your sign-in, so a key made from a session cannot outlive it or a password reset. Create long-lived keys for scripts and integrations with Philter's [API Keys API]({$ philter_docs }/api_and_sdks/api/api_keys_api.html#create-a-key), using a long-lived key you already have that holds the `api-keys:write` scope. On a new deployment, that is the [bootstrap API key]({$ philter_docs }/account/api_keys.html#bootstrapping-an-api-key-for-automation).

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

The response contains the key in `apiKey`. It is shown once; Philter stores only a hash of it. A key can only be given scopes the key that creates it holds. Once created, the key appears in its owner's **My Account**, where they can narrow its scopes or revoke it.
