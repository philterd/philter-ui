# Signing In

Philter UI has no user accounts of its own. Users, passwords, and MFA enrollment are stored in Philter, and Philter checks every sign-in. This page describes what a person sees in Philter UI. The thresholds and timeouts are Philter settings; see Philter's [Settings]({$ philter_docs }/settings.html).

## How sign-in works

1. Enter your Philter username and password on the sign-in page.
2. Philter UI sends them to Philter. Philter UI does not store them.
3. If they are valid, Philter returns a session API key for your user. If you are enrolled in MFA, Philter UI first asks for a code from your authenticator app, and the key is issued only after the code is accepted.
4. Philter UI keeps the session key on its server, never in your browser, and uses it for every request it makes to Philter on your behalf.

Philter applies your role and the key's scopes to each request, so Philter UI shows only what you are allowed to see and change. Administrator pages appear only for administrators.

Users created without a password, such as accounts used by automation, cannot sign in to Philter UI. They authenticate with long-lived [API keys]({$ philter_docs }/account/api_keys.html) as before.

## Your first sign-in

An administrator creates your user and gives you a temporary password. On your first sign-in, Philter UI asks you to set a new password before you can use anything else. The same happens after an administrator resets your password.

The first administrator's password is set with Philter's bootstrap API key. See [Bootstrapping an API key]({$ philter_docs }/account/api_keys.html#bootstrapping-an-api-key-for-automation).

## Password requirements

Use a password of at least 16 characters, either random (mixed letters, numbers, and symbols) or a passphrase of 5 to 7 unrelated words. Philter enforces the minimum length; randomness is recommended rather than enforced, so a long passphrase is accepted. When you change your own password, the new one must differ from the current one.

When an administrator creates a user, the **Generate** button fills in a random password that meets the requirements.

## Multi-factor authentication (MFA)

MFA uses time-based one-time passwords (TOTP) from a standard authenticator app. An administrator can make MFA available, or require it for everyone.

* **Enroll:** open **My Account**, **MFA**, scan the QR code (or type the setup key) into your authenticator app, and enter a code to confirm.
* **At sign-in:** enter your password, then a code from your authenticator app.
* **Each code works once.** Reusing a code is rejected; wait for the next one.
* **Too many wrong codes** lock your account until an administrator unlocks it on the **Admin**, **Users** tab.
* **Lost authenticator:** an administrator clears your enrollment with **Disable MFA** on the **Admin**, **Users** tab, and you enroll again.

## Failed sign-ins

After too many consecutive failed sign-ins, Philter locks the username for a period. While it is locked, sign-in is refused even with the correct password. The lock clears on its own when the period ends.

## Sessions

Your session ends when you sign out, when you are idle longer than Philter's session-key idle timeout, or when the key reaches its maximum lifetime. Philter UI then returns you to the sign-in page. Signing out revokes the session key in Philter.

A session also ends when Philter stops accepting its key: for example, after your password is changed or reset, your account is deactivated, or an administrator revokes the key.

All sign-ins, failures, lockouts, password changes, and MFA changes are recorded in Philter's [audit log]({$ philter_docs }/auditing.html).
