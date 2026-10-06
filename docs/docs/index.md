# Philter UI

> Philter UI is under development. Signing in works as described in [Signing In](sign_in.md), and the [Custom Lists](dashboard.md#custom-lists), [Always/Never Redact Lists](dashboard.md#alwaysnever-redact-lists), [Contexts](dashboard.md#contexts), [Redaction Ledgers](dashboard.md#redaction-ledgers), and [Legal Holds](dashboard.md#legal-holds) pages are available. The other pages described in [Dashboard](dashboard.md) are being ported to Philter's API.

Philter UI is an optional web interface for administering [Philter](https://github.com/philterd/philter). Philter 4 runs headless and is administered through its [REST API]({$ philter_docs }/api_and_sdks/api.html). Philter UI is a separate application that uses that API. Philter does not need it.

## How it works

People sign in to Philter UI with their Philter username and password. Philter checks them and returns a session key, a short-lived [API key]({$ philter_docs }/account/api_keys.html#session-keys) for that user, which Philter UI uses for the rest of the session. Philter UI has no users or passwords of its own, so each person sees what their Philter role allows, and Philter's [audit log]({$ philter_docs }/auditing.html) records their actions under their own name. See [Signing In](sign_in.md).

## Running

Philter UI requires Java 25.

```
java -jar philter-ui-{$ philter_ui_version }.jar
```

| Environment variable | Description | Default |
|----------------------|-------------|---------|
| `PHILTER_URL` | The address of your Philter instance. | `https://localhost:8080` |
| `PORT` | The port Philter UI listens on, so it can run beside Philter on 8080. | `8081` |
| `SESSION_TIMEOUT_MINUTES` | Minutes without interaction in Philter UI before a person is signed out. | `30` |

Philter must have password sign-in enabled with `PASSWORD_SIGN_IN_ENABLED=true`; it is off by default. See Philter's [Sign-in Security]({$ philter_docs }/sign_in_security.html).

Philter UI verifies Philter's TLS certificate. Philter's Docker image serves HTTPS with a self-signed certificate, which Philter UI does not trust. Give Philter a certificate from a certificate authority your Java runtime trusts, or add Philter's certificate to a truststore and start Philter UI with `-Djavax.net.ssl.trustStore=<path>`.

Serve Philter UI to its users over HTTPS, for example behind a reverse proxy that terminates TLS, since people send their passwords to it.
