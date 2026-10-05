# Philter UI

An optional web UI for administering [Philter](https://github.com/philterd/philter). Philter 4.0 runs headless and is administered through its REST API; Philter UI is a separate application that calls that API with an administrator's API key. Philter does not need it.

## Status

The UI is being moved out of Philter in two steps.

1. **Done:** the Vaadin dashboard was moved here from `philterd/philter` at commit `c558c65`. Its views still call Philter's internal services and entities, so they are kept under `src/main/java/ai/philterd/philter/` (and their tests under `src/test/java/ai/philterd/philter/`) and excluded from compilation in `pom.xml`. The application that builds today is a shell in `ai.philterd.ui` that serves a placeholder page.
2. **Next:** each view is ported to Philter's REST API through [philter-sdk-java](https://github.com/philterd/philter-sdk-java), moved into `ai.philterd.ui`, and removed from the exclusion.

The user guide in `docs/` (MkDocs; `mkdocs build` from that directory) describes the intended behavior, including sign-in through Philter, which Philter does not yet support.

## Building

Requires Java 25 and Node.js. The build installs the frontend's npm packages and bundles them into the jar:

```
mvn verify
```

## Running

```
java -jar target/philter-ui-4.0.0-SNAPSHOT.jar
```

Philter UI listens on port 8081 (set `PORT` to change it), so it can run beside Philter on 8080.

## License

Apache License, version 2.0. See [LICENSE.txt](LICENSE.txt).
