/*
 *     Copyright 2026 Philterd, LLC @ https://www.philterd.ai
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.philterd.ui.security;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Answers Philter's sign-in endpoints the way Philter documents them, with the outcome chosen by the
 * username, and records which session keys were revoked.
 */
final class FakePhilter implements AutoCloseable {

    final List<String> revokedKeys = new CopyOnWriteArrayList<>();
    private final HttpServer server;

    FakePhilter() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/sign-in", this::signIn);
        server.createContext("/api/users/me", this::me);
        server.createContext("/api/api-keys/current", this::signOut);
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void signIn(final HttpExchange exchange) throws IOException {
        final String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (exchange.getRequestURI().getPath().endsWith("/mfa")) {
            final String code = JsonParser.parseString(body).getAsJsonObject().get("code").getAsString();
            if ("123456".equals(code)) {
                respond(exchange, 200, session("sk_mfa", "mfa", false, false));
            } else if ("999999".equals(code)) {
                respond(exchange, 403, "{\"message\":\"MFA is locked.\"}");
            } else {
                respond(exchange, 401, "{\"message\":\"Unauthorized\"}");
            }
            return;
        }
        final String username = JsonParser.parseString(body).getAsJsonObject().get("username").getAsString();
        switch (username) {
            case "jordan" -> respond(exchange, 200, session("sk_jordan", "jordan", false, false));
            case "admin" -> respond(exchange, 200, session("sk_admin", "admin", false, false));
            case "broken" -> respond(exchange, 200, session("sk_broken", "broken", false, false));
            case "newpassword" -> respond(exchange, 200, session("sk_newpassword", "newpassword", true, false));
            case "enroll" -> respond(exchange, 200, session("sk_enroll", "enroll", false, true));
            case "mfa" -> respond(exchange, 200,
                    "{\"mfaRequired\":true,\"challenge\":\"challenge-1\",\"challengeExpiresAt\":\"2026-10-05T14:08:11.000Z\"}");
            case "locked" -> {
                exchange.getResponseHeaders().add("Retry-After", "900");
                respond(exchange, 429, "{\"message\":\"Too many failed sign-ins.\",\"reason\":\"locked\"}");
            }
            case "busy" -> {
                exchange.getResponseHeaders().add("Retry-After", "60");
                respond(exchange, 429, "{\"message\":\"Too many requests.\",\"reason\":\"rate_limited\"}");
            }
            case "mfalocked" -> respond(exchange, 403, "{\"message\":\"MFA is locked.\"}");
            case "disabled" -> respond(exchange, 404, "{\"message\":\"Not found.\"}");
            default -> respond(exchange, 401, "{\"message\":\"Unauthorized\"}");
        }
    }

    private void me(final HttpExchange exchange) throws IOException {
        final String key = key(exchange);
        switch (key) {
            case "sk_admin" -> respond(exchange, 200, user("admin", "admin"));
            case "sk_broken" -> respond(exchange, 500, "{\"message\":\"Internal error.\"}");
            default -> respond(exchange, 200, user(key.substring(3), "user"));
        }
    }

    private void signOut(final HttpExchange exchange) throws IOException {
        final String key = key(exchange);
        if ("sk_expired".equals(key)) {
            respond(exchange, 401, "{\"message\":\"Unauthorized\"}");
            return;
        }
        revokedKeys.add(key);
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    private static String key(final HttpExchange exchange) {
        final String header = exchange.getRequestHeaders().getFirst("Authorization");
        return header == null ? "" : header.replaceFirst("^Bearer ", "");
    }

    private static String session(final String key, final String username, final boolean passwordChangeRequired,
                                  final boolean mfaEnrollmentRequired) {
        return "{\"id\":\"id-" + username + "\",\"apiKey\":\"" + key + "\",\"username\":\"" + username + "\",\"scopes\":[\"redact\"],"
                + "\"expiresAt\":\"2026-10-06T02:03:11.000Z\",\"idleExpiresAt\":\"2026-10-05T14:33:11.000Z\","
                + "\"passwordChangeRequired\":" + passwordChangeRequired
                + ",\"mfaEnrollmentRequired\":" + mfaEnrollmentRequired + "}";
    }

    private static String user(final String username, final String role) {
        return "{\"username\":\"" + username + "\",\"role\":\"" + role + "\",\"active\":true}";
    }

    private static void respond(final HttpExchange exchange, final int status, final String body) throws IOException {
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }

}
