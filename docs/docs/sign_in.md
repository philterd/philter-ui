# Signing In

Philter UI has no user accounts of its own. Users, passwords, and MFA enrollment are stored in Philter, and Philter checks every sign-in. This page describes what a person sees in Philter UI. The limits and timeouts are Philter settings; see Philter's [Sign-in Security]({$ philter_docs }/sign_in_security.html).

## How sign-in works

1. Enter your Philter username and password on the sign-in page.
2. Philter UI sends them to Philter. It does not store or log them.
3. If they are valid, Philter returns a session key for your user. If you are enrolled in MFA, Philter UI first asks for a code from your authenticator app, and the key is issued only after Philter accepts the code.
4. Philter UI keeps the session key on its server, never in your browser, and uses it for every request it makes to Philter on your behalf.

Philter applies your role to each request, so a person who is not an administrator cannot reach administrator functions.

Users without a password, such as accounts used by automation, cannot sign in to Philter UI. They authenticate with long-lived [API keys]({$ philter_docs }/account/api_keys.html).

## Your first sign-in

An administrator creates your user and gives you a temporary password. When you sign in with it, Philter UI asks you to set a new password before you can do anything else: enter the temporary password as the current password, then the new one twice. Philter then ends that session, and you sign in again with the new password. The same happens after an administrator resets your password.

The first administrator's password is set with Philter's bootstrap API key. See [Bootstrapping an API key]({$ philter_docs }/account/api_keys.html#bootstrapping-an-api-key-for-automation) and [Sign-in Security]({$ philter_docs }/sign_in_security.html#passwords).

## Password requirements

A password must be at least 16 characters and at most 72 bytes in UTF-8. A new password must differ from the current one. Use either a random password (mixed letters, numbers, and symbols) or a passphrase of 5 to 7 unrelated words.

## Multi-factor authentication (MFA)

MFA uses time-based one-time codes from a standard authenticator app. An administrator makes it available, or requires it, with Philter's `mfaAvailable` and `mfaRequired` settings.

* **Enrolling when MFA is required.** If Philter requires MFA and you have not enrolled, Philter UI shows a QR code and a setup key after you sign in. Scan the code (or type the key) into your authenticator app and enter the code it shows. Philter then ends that session, and you sign in again, this time with a code.
* **Signing in.** Enter your password, then a code from your authenticator app.
* **A wrong code** ends that sign-in attempt: sign in again with your password and try a new code.
* **Each code works once.** If a code was just used, wait for the app to show the next one.
* **Too many wrong codes** (five in a row) lock your account until an administrator unlocks it.
* **Lost authenticator:** an administrator removes your enrollment, and you enroll again.

## Failed sign-ins

After repeated failed sign-ins for a username (5 within 15 minutes by default), Philter locks that username for a period, and sign-in is refused even with the correct password. The lock clears on its own. Philter also limits how many sign-in attempts each address can make per minute. Philter UI shows how long to wait in either case.

## Sessions

Your session ends, and Philter UI returns you to the sign-in page, when:

* You sign out. Philter UI revokes the session key in Philter.
* You do not interact with Philter UI for `SESSION_TIMEOUT_MINUTES` (default 30). An open tab you are not using does not keep the session alive. Philter UI revokes the session key.
* Philter stops accepting the session key. This happens when the key goes unused for Philter's idle timeout (default 30 minutes) or reaches its maximum lifetime (default 12 hours), when your password is changed or reset, when you enroll in MFA, when your account is deactivated, or when an administrator signs you out everywhere.

Sign-ins, failures, lockouts, password changes, MFA changes, and session keys are recorded in Philter's [audit log]({$ philter_docs }/sign_in_security.html#audit-events).
