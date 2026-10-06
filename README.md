# Philter UI

An optional web UI for administering [Philter](https://github.com/philterd/philter). Philter 4.0 runs headless and is administered through its REST API; Philter UI is a separate application that calls that API. People sign in with their Philter username and password; Philter checks them and returns a session key that Philter UI uses for that person's requests. Philter does not need it.

## Status

The UI is being moved out of Philter in two steps.

1. **Done:** the Vaadin dashboard was moved here from `philterd/philter` at commit `c558c65`. Its views still call Philter's internal services and entities, so they are kept under `src/main/java/ai/philterd/philter/` (and their tests under `src/test/java/ai/philterd/philter/`) and excluded from compilation in `pom.xml`. The application that builds today, in `ai.philterd.ui`, signs people in through Philter (password, MFA, forced password change, and MFA enrollment) and has ported Redaction Policies, Custom Lists, Always/Never Redact Lists, Contexts, Redaction Ledgers, and Legal Holds pages; the other views are still to be ported.
2. **Next:** each view is ported to Philter's REST API through [philter-sdk-java](https://github.com/philterd/philter-sdk-java), moved into `ai.philterd.ui`, and removed from the exclusion.

The user guide is in `docs/` (MkDocs; run `mkdocs build` from that directory). Its dashboard page describes the views still to be ported.

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

Philter must run with `PASSWORD_SIGN_IN_ENABLED=true`. Philter UI verifies Philter's TLS certificate, so a Philter with a self-signed certificate needs that certificate in a truststore passed with `-Djavax.net.ssl.trustStore=<path>`. See the [user guide](docs/docs/index.md).

## License

Apache License, version 2.0. See [LICENSE.txt](LICENSE.txt).
