# Philter UI

A simple, optional web UI on top of [Philter](https://github.com/philterd/philter) for managing policies, ledgers, and users. For a full redaction workflow application, use [Philter Workspace](https://philterd.ai/philter-workspace).

Philter UI is a separate application that calls Philter's REST API. People sign in with their Philter username and password. Philter does not need it.

## Quickstart

`docker-compose.yml` runs Philter UI, built from this repository, beside Philter 4.0.0. It is meant for development and evaluation.

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

## Documentation

The user guide is in `docs/` (MkDocs; run `mkdocs build` from that directory):

- [Running](docs/docs/index.md#running): all environment variables and how to connect to your own Philter
- [Client certificates and trust stores](docs/docs/index.md#client-certificates-and-trust-stores)
- [Docker Compose](docs/docs/index.md#docker-compose): data storage and other details of the compose file
- [Signing in](docs/docs/sign_in.md)
- [Dashboard](docs/docs/dashboard.md)

## Supported Philter versions

| Philter UI | Philter |
|------------|---------|
| 4.0.0 | 4.x |

## Building

Requires Java 25 and Node.js. The build installs the frontend's npm packages and bundles them into the jar:

```
mvn verify
java -jar target/philter-ui-4.0.0.jar
```

Philter must run with `PASSWORD_SIGN_IN_ENABLED=true`. Set `PHILTER_URL` to its address.

## License

Apache License, version 2.0. See [LICENSE.txt](LICENSE.txt).
