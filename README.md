# Philter UI

An optional web UI for administering [Philter](https://github.com/philterd/philter). Philter 4.0 runs headless and is administered through its REST API; Philter UI is a separate application that calls that API. People sign in with their Philter username and password; Philter checks them and returns a session key that Philter UI uses for that person's requests. Philter does not need it.

## Status

The Vaadin dashboard was moved here from `philterd/philter` at commit `c558c65` and has since been ported to Philter's REST API through [philter-sdk-java](https://github.com/philterd/philter-sdk-java). Every page works through the API with the signed-in person's session key; none reads Philter's database or services.

The user guide is in `docs/` (MkDocs; run `mkdocs build` from that directory).

## Building

Requires Java 25 and Node.js. The build installs the frontend's npm packages and bundles them into the jar:

```
mvn verify
```

## Running

```
java -jar target/philter-ui-4.0.0-SNAPSHOT.jar
```

| Environment variable | Description | Default |
|----------------------|-------------|---------|
| `PHILTER_URL` | The address of your Philter instance. | `https://localhost:8080` |
| `PORT` | The port Philter UI listens on, so it can run beside Philter on 8080. | `8081` |
| `SESSION_TIMEOUT_MINUTES` | Minutes without interaction before a person is signed out, whether or not the tab is still open. | `30` |
| `DOCUMENT_TIMEOUT_SECONDS` | Seconds to wait for Philter to redact a PDF on the Dashboard. | `300` |
| `MAX_PDF_BYTES` | The largest PDF the Dashboard accepts, in bytes. `0` follows Philter's own limit (its `MAX_FILE_SIZE_BYTES`, read from Philter); a smaller value lowers it, for example to limit the memory an upload uses. When Philter's limits cannot be read, this value or 10 MB is used. | `0` |
| `TRUSTED_PROXIES` | Reverse proxies in front of Philter UI (IP addresses and CIDR ranges), whose `X-Forwarded-For` is believed when passing a person's address to Philter with each request. | None |
| `PHILTER_CLIENT_CERTIFICATE` | A PKCS12 or JKS keystore holding the client certificate and private key Philter UI presents to Philter, for a Philter that requires one (mutual TLS). | None |
| `PHILTER_CLIENT_CERTIFICATE_PASSWORD` | The password of `PHILTER_CLIENT_CERTIFICATE`, which its private key must also use. Required with `PHILTER_CLIENT_CERTIFICATE`. | None |
| `PHILTER_TRUST_STORE` | A PKCS12 or JKS trust store holding the certificates Philter UI trusts for Philter, in place of the JVM's defaults. Build it with `keytool -importcert`. | None |
| `PHILTER_TRUST_STORE_PASSWORD` | The password of `PHILTER_TRUST_STORE`. A PKCS12 trust store needs it. | None |

Philter must run with `PASSWORD_SIGN_IN_ENABLED=true`. Philter UI verifies Philter's TLS certificate and host name, so a Philter with a self-signed certificate or one from a private certificate authority needs that certificate in `PHILTER_TRUST_STORE`. For a Philter that requires a client certificate, either itself ([mutual TLS](https://philterd.github.io/philter/latest/settings.html#client-certificates-mutual-tls)) or through a load balancer or service mesh in front of it, set `PHILTER_CLIENT_CERTIFICATE`. See the [user guide](docs/docs/index.md#client-certificates-and-trust-stores).

### Docker Compose

`docker-compose.yml` runs Philter UI, built from this repository, beside Philter 4.0.0 and the ph-eye service Philter needs. Philter has password sign-in turned on and serves plain HTTP on the private compose network, where Philter UI reaches it at `http://philter:8080`. It is meant for development and evaluation.

Philter keeps its data in a SQLite database (`STORAGE_TYPE=sqlite`) in the `philter-data` volume, mounted at `/opt/philter/data`, so users, policies, and other data survive `docker compose down` and `docker compose up`. `docker compose down -v` removes the volume and its data. To run Philter on MongoDB instead, for example to run several Philter instances, see Philter's [database documentation](https://philterd.github.io/philter/latest/database.html#choosing-sqlite-or-mongodb).

Philter needs an encryption key and a bootstrap API key, which `docker-compose.yml` reads from `.env`. Generate them once and keep them: data encrypted with `PHILTER_ENCRYPTION_KEY` cannot be recovered without it, and Philter does not start if it changes.

```
umask 077
[ -f .env ] || printf 'PHILTER_ENCRYPTION_KEY=%s\nPHILTER_BOOTSTRAP_API_KEY=sk_%s\n' \
  "$(openssl rand -base64 32)" "$(openssl rand -hex 16)" > .env
docker compose up -d --build
```

The `admin` user starts without a password. Set one with the bootstrap API key, then sign in at `http://localhost:8081`:

```
API_KEY=$(sed -n 's/^PHILTER_BOOTSTRAP_API_KEY=//p' .env)
curl -X PUT http://localhost:8080/api/users/admin/password \
  -H "Authorization: Bearer ${API_KEY}" \
  -H "Content-Type: application/json" -d '{"password": "<16 or more characters>"}'
```

Before using it with real data, serve Philter UI over HTTPS.

## License

Apache License, version 2.0. See [LICENSE.txt](LICENSE.txt).
