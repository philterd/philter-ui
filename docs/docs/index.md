# Philter UI

> Philter UI is under development.

Philter UI is an optional web interface for administering [Philter](https://github.com/philterd/philter). Philter 4 runs headless and is administered through its [REST API]({$ philter_docs }/api_and_sdks/api.html). Philter UI is a separate application that uses that API. Philter does not need it.

## How it works

People sign in to Philter UI with their Philter username and password. Philter checks them and returns a session key, a short-lived [API key]({$ philter_docs }/account/api_keys.html#session-keys) for that user, which Philter UI uses for the rest of the session. Philter UI has no users or passwords of its own, so each person sees what their Philter role allows, and Philter's [audit log]({$ philter_docs }/auditing.html) attributes the events it records for their requests to their own user rather than to Philter UI. See [Signing In](sign_in.md).

## Running

Philter UI requires Java 25.

```
java -jar philter-ui-{$ philter_ui_version }.jar
```

| Environment variable | Description | Default |
|----------------------|-------------|---------|
| `PHILTER_URL` | The address of your Philter instance. | `https://localhost:8080` |
| `PORT` | The port Philter UI listens on, so it can run beside Philter on 8080. | `8081` |
| `SESSION_TIMEOUT_MINUTES` | Minutes after a person's browser stops contacting Philter UI, for example after the tab is closed, before their session ends. See [Sessions](sign_in.md#sessions). | `30` |
| `DOCUMENT_TIMEOUT_SECONDS` | Seconds Philter UI waits for Philter to redact a PDF on the Dashboard. | `300` |
| `TRUSTED_PROXIES` | The reverse proxies in front of Philter UI, as comma-separated IP addresses and CIDR ranges. See [Client addresses](#client-addresses). | None |

Philter must have password sign-in enabled with `PASSWORD_SIGN_IN_ENABLED=true`; it is off by default. See Philter's [Sign-in Security]({$ philter_docs }/sign_in_security.html).

Philter UI verifies Philter's TLS certificate. Philter's Docker image serves HTTPS with a self-signed certificate, which Philter UI does not trust. A Philter JAR serves plain HTTP unless `SSL_ENABLED=true`; to use it as is, set `PHILTER_URL` to its `http://` address, and keep the network between Philter UI and Philter private, since passwords and redacted text cross it. Give Philter a certificate from a certificate authority your Java runtime trusts, or add Philter's certificate to a truststore and start Philter UI with `-Djavax.net.ssl.trustStore=<path>`.

Serve Philter UI to its users over HTTPS, for example behind a reverse proxy that terminates TLS, since people send their passwords to it.

### Client addresses

Philter limits sign-in attempts per client address and records the client address with many of the events in its [audit log]({$ philter_docs }/auditing.html), such as sign-ins, audit log exports, and listings. Philter UI calls Philter on each person's behalf, so it passes that person's address to Philter in `X-Forwarded-For` on every request, from sign-in on. Otherwise every Philter UI user would share one sign-in limit, and those events would show Philter UI's address instead of the person's. A request Philter UI makes outside a person's request, such as signing out a session that timed out, carries no address.

* Philter UI uses the address of the connection to it. Behind a reverse proxy, that is the proxy's address, so set `TRUSTED_PROXIES` to the proxy's address. Philter UI then reads `X-Forwarded-For` from the right and uses the first address that is not a trusted proxy. A browser cannot choose its address unless it connects from a trusted proxy's address. A hop that is not an IP address ends the search, and the last trusted proxy's address is used. An entry in `TRUSTED_PROXIES` that is not an IP address or CIDR range stops Philter UI from starting. Philter UI does not otherwise act on `X-Forwarded-*` headers, even on platforms such as Kubernetes where Spring Boot would by default.
* Philter believes the header only from addresses in its own [`TRUSTED_PROXIES`]({$ philter_docs }/settings.html#api-access), which by default are the loopback, private, link-local, and IPv6 unique-local ranges. If Philter UI reaches Philter from another address, set Philter's `TRUSTED_PROXIES` to a list that includes Philter UI's address; setting it replaces the default.
