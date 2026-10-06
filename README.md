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
| `SESSION_TIMEOUT_MINUTES` | Minutes without interaction before a person is signed out. | `30` |
| `DOCUMENT_TIMEOUT_SECONDS` | Seconds to wait for Philter to redact a PDF on the Dashboard. | `300` |
| `TRUSTED_PROXIES` | Reverse proxies in front of Philter UI (IP addresses and CIDR ranges), whose `X-Forwarded-For` is believed when passing a person's address to Philter with each request. | None |

Philter must run with `PASSWORD_SIGN_IN_ENABLED=true`. Philter UI verifies Philter's TLS certificate, so a Philter with a self-signed certificate needs that certificate in a truststore passed with `-Djavax.net.ssl.trustStore=<path>`. See the [user guide](docs/docs/index.md).

## License

Apache License, version 2.0. See [LICENSE.txt](LICENSE.txt).
