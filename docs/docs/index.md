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
| `SESSION_TIMEOUT_MINUTES` | Minutes without interaction in Philter UI before a person is signed out, whether or not the tab is still open. See [Sessions](sign_in.md#sessions). | `30` |
| `DOCUMENT_TIMEOUT_SECONDS` | Seconds Philter UI waits for Philter to redact a PDF on the Dashboard. | `300` |
| `MAX_PDF_BYTES` | The largest PDF the Dashboard accepts, in bytes. `0` follows Philter's own limit, its `MAX_FILE_SIZE_BYTES`, which Philter UI reads from Philter; a smaller value lowers it. When Philter's limits cannot be read, this value or 10 MB is used. See [Dashboard](dashboard.md). | `0` |
| `TRUSTED_PROXIES` | The reverse proxies in front of Philter UI, as comma-separated IP addresses and CIDR ranges. See [Client addresses](#client-addresses). | None |
| `PHILTER_CLIENT_CERTIFICATE` | A PKCS12 or JKS keystore holding the client certificate and private key Philter UI presents to Philter. See [Client certificates and trust stores](#client-certificates-and-trust-stores). | None |
| `PHILTER_CLIENT_CERTIFICATE_PASSWORD` | The password of `PHILTER_CLIENT_CERTIFICATE`, which its private key must also use. Required with `PHILTER_CLIENT_CERTIFICATE`. | None |
| `PHILTER_TRUST_STORE` | A PKCS12 or JKS trust store holding the certificates Philter UI trusts for Philter, in place of the JVM's defaults. See [Client certificates and trust stores](#client-certificates-and-trust-stores). | None |
| `PHILTER_TRUST_STORE_PASSWORD` | The password of `PHILTER_TRUST_STORE`. A PKCS12 trust store needs it. | None |

Philter must have password sign-in enabled with `PASSWORD_SIGN_IN_ENABLED=true`; it is off by default. See Philter's [Sign-in Security]({$ philter_docs }/sign_in_security.html).

Philter UI verifies Philter's TLS certificate, and that it names the host in `PHILTER_URL`. Philter's Docker image serves HTTPS with a self-signed certificate, which Philter UI does not trust. A Philter JAR serves plain HTTP unless `SSL_ENABLED=true`; to use it as is, set `PHILTER_URL` to its `http://` address, and keep the network between Philter UI and Philter private, since passwords and redacted text cross it. Give Philter a certificate from a certificate authority your Java runtime trusts, or put Philter's certificate in a trust store and set `PHILTER_TRUST_STORE`, as described in [Client certificates and trust stores](#client-certificates-and-trust-stores).

Serve Philter UI to its users over HTTPS, for example behind a reverse proxy that terminates TLS, since people send their passwords to it.

### Client certificates and trust stores

Philter UI can present a client certificate to Philter and trust certificates of your choosing for Philter, without changing trust for the rest of the Java process. Each setting works alone or with the other, and Philter UI uses the same TLS settings for every connection to Philter: signing in, each person's requests, and PDF redaction.

* **Client certificate.** When Philter requires clients to present a certificate (mutual TLS), either itself with [`SSL_CLIENT_AUTH=need`](https://philterd.github.io/philter/latest/settings.html#client-certificates-mutual-tls) or through a load balancer, ingress, or service mesh in front of it, set `PHILTER_CLIENT_CERTIFICATE` to a PKCS12 or JKS keystore holding Philter UI's certificate and its private key, and `PHILTER_CLIENT_CERTIFICATE_PASSWORD` to the keystore's password. The private key must use the keystore's password, as it does in a PKCS12 file. To make one from PEM files:

    ```
    openssl pkcs12 -export -in philter-ui.crt -inkey philter-ui.key -out philter-ui-client.p12
    ```

* **Trust store.** When Philter's certificate is self-signed or issued by a private certificate authority, set `PHILTER_TRUST_STORE` to a PKCS12 or JKS trust store holding that certificate or the authority's, and `PHILTER_TRUST_STORE_PASSWORD` to its password. Philter UI then trusts only the certificates in that store for Philter, in place of the JVM's defaults and `-Djavax.net.ssl.trustStore`. Build the trust store with `keytool -importcert`:

    ```
    keytool -importcert -noprompt -alias philter-ca -file philter-ca.pem \
      -keystore philter-trust.p12 -storetype PKCS12 -storepass <password>
    ```

    Do not build it with `openssl pkcs12 -nokeys`: Java reads such a PKCS12 trust store as empty, so it trusts nothing.

With neither setting, Philter UI uses the JVM's defaults, including `-Djavax.net.ssl.trustStore` when it is given. With only `PHILTER_CLIENT_CERTIFICATE`, Philter UI trusts what the JVM trusts by default, including `-Djavax.net.ssl.trustStore`.

Philter UI reads both files when it starts, and does not start if either is missing, cannot be read with its password, or holds nothing usable: no private key in the keystore, or no certificates in the trust store. The message names the setting and the file. Philter UI does not log the passwords. It always checks that Philter's certificate names the host in `PHILTER_URL`, and has no setting to turn that check or certificate verification off.

The Docker image built from the repository's `Dockerfile` (`docker build -t philter-ui .`) runs as user `philter` (UID 10001), which must be able to read the files. Mount them read-only and point the settings at them:

```
docker run -p 8081:8081 \
  -v /srv/philter-ui/tls:/opt/philter-ui/tls:ro \
  -e PHILTER_URL=https://philter.internal.example.com:8080 \
  -e PHILTER_CLIENT_CERTIFICATE=/opt/philter-ui/tls/philter-ui-client.p12 \
  -e PHILTER_CLIENT_CERTIFICATE_PASSWORD \
  -e PHILTER_TRUST_STORE=/opt/philter-ui/tls/philter-trust.p12 \
  -e PHILTER_TRUST_STORE_PASSWORD \
  philter-ui
```

`-e PHILTER_CLIENT_CERTIFICATE_PASSWORD` without a value passes the password from your shell's environment, so it is not written in the command. For a Philter reached over HTTPS from a Compose file, add the same to the `philter-ui` service and point `PHILTER_URL` at Philter's `https://` address. Read the passwords from your environment or from an `.env` file kept out of version control:

```yaml
  philter-ui:
    environment:
      PHILTER_CLIENT_CERTIFICATE: /opt/philter-ui/tls/philter-ui-client.p12
      PHILTER_CLIENT_CERTIFICATE_PASSWORD: ${PHILTER_CLIENT_CERTIFICATE_PASSWORD}
      PHILTER_TRUST_STORE: /opt/philter-ui/tls/philter-trust.p12
      PHILTER_TRUST_STORE_PASSWORD: ${PHILTER_TRUST_STORE_PASSWORD}
    volumes:
      - ./tls:/opt/philter-ui/tls:ro
```

### Docker Compose

The `docker-compose.yml` in the [Philter UI repository](https://github.com/philterd/philter-ui) runs Philter UI, built from source, beside Philter 4.0.0 and the ph-eye service Philter needs. Philter has password sign-in turned on and serves plain HTTP on the private compose network, where Philter UI reaches it at `http://philter:8080`. It is meant for development and evaluation.

Philter keeps its data in a SQLite database (`STORAGE_TYPE=sqlite`) in the `philter-data` volume, mounted at `/opt/philter/data`, so users, policies, and other data survive `docker compose down` and `docker compose up`. `docker compose down -v` removes the volume and its data. To run Philter on MongoDB instead, for example to run several Philter instances, see Philter's [database documentation]({$ philter_docs }/database.html#choosing-sqlite-or-mongodb).

```
docker compose up -d --build
```

The `admin` user starts without a password. Set one with the bootstrap API key in `docker-compose.yml`, then sign in at `http://localhost:8081`:

```
curl -X PUT http://localhost:8080/api/users/admin/password \
  -H "Authorization: Bearer sk_developmentonlydevelopmentonly01" \
  -H "Content-Type: application/json" -d '{"password": "<16 or more characters>"}'
```

Before using it with real data, replace the development `PHILTER_ENCRYPTION_KEY` and `PHILTER_BOOTSTRAP_API_KEY`, and serve Philter UI over HTTPS.

### Client addresses

Philter limits sign-in attempts per client address and records the client address with many of the events in its [audit log]({$ philter_docs }/auditing.html), such as sign-ins, audit log exports, and listings. Philter UI calls Philter on each person's behalf, so it passes that person's address to Philter in `X-Forwarded-For` on every request, from sign-in on. Otherwise every Philter UI user would share one sign-in limit, and those events would show Philter UI's address instead of the person's. A request Philter UI makes outside a person's request, such as signing out a session that timed out, carries no address.

* Philter UI uses the address of the connection to it. Behind a reverse proxy, that is the proxy's address, so set `TRUSTED_PROXIES` to the proxy's address. Philter UI then reads `X-Forwarded-For` from the right and uses the first address that is not a trusted proxy. A browser cannot choose its address unless it connects from a trusted proxy's address. A hop that is not an IP address ends the search, and the last trusted proxy's address is used. An entry in `TRUSTED_PROXIES` that is not an IP address or CIDR range stops Philter UI from starting. Philter UI does not otherwise act on `X-Forwarded-*` headers, even on platforms such as Kubernetes where Spring Boot would by default.
* Philter believes the header only from addresses in its own [`TRUSTED_PROXIES`]({$ philter_docs }/settings.html#api-access), which by default are the loopback, private, link-local, and IPv6 unique-local ranges. If Philter UI reaches Philter from another address, set Philter's `TRUSTED_PROXIES` to a list that includes Philter UI's address; setting it replaces the default.
