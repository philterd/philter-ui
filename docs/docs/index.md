# Philter UI

Philter UI is an optional web interface for administering [Philter](https://github.com/philterd/philter). Philter 4 runs headless and is administered through its [REST API]({$ philter_docs }/api_and_sdks/api.html). Philter UI is a separate application that uses that API. Philter does not need it.

!!! note
    Philter UI is under development. These pages describe the intended behavior. Signing in requires Philter's password sign-in, which is not yet in a Philter release.

## How it works

People sign in to Philter UI with their Philter username and password. Philter checks them and returns an [API key]({$ philter_docs }/account/api_keys.html) for that user, which Philter UI uses for the rest of the session. Philter UI has no users or passwords of its own, so each person sees what their Philter role and the key's scopes allow, and Philter's [audit log]({$ philter_docs }/auditing.html) records their actions under their own name. See [Signing In](sign_in.md).

## Running

Philter UI requires Java 25.

```
java -jar philter-ui-{$ philter_ui_version }.jar
```

Philter UI listens on port 8081 (set `PORT` to change it), so it can run beside Philter on 8080. Set `PHILTER_URL` to the address of your Philter instance. Password sign-in must be enabled in Philter; it is off by default.
