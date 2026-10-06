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
package ai.philterd.ui.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Policy JSON as Philter UI shows, checks, and compares it. Philter does the full validation on save. */
public final class PolicyJson {

    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** One difference between two revisions: an added, removed, or replaced value at a JSON path. */
    public record Difference(String operation, String path, String before, String after) {
    }

    private PolicyJson() {
    }

    /** The JSON a new policy starts from. Philter's API has no template, so Philter UI keeps its own. */
    public static String template() {
        try (InputStream in = PolicyJson.class.getResourceAsStream("/templates/new-policy.json")) {
            return new String(Objects.requireNonNull(in, "templates/new-policy.json").readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The JSON indented for reading, or the text unchanged if it is not JSON. */
    public static String pretty(final String json) {
        if (json == null) {
            return "";
        }
        try {
            return PRETTY.toJson(JsonParser.parseString(json));
        } catch (final JsonParseException e) {
            return json;
        }
    }

    /** What is wrong with the text as a policy's JSON, or {@code null} if it is a JSON object. */
    public static String problem(final String json) {
        if (json == null || json.isBlank()) {
            return "Enter the policy's JSON.";
        }
        try {
            if (!JsonParser.parseString(json).isJsonObject()) {
                return "A policy must be a JSON object.";
            }
        } catch (final JsonParseException e) {
            return "The policy is not valid JSON.";
        }
        return null;
    }

    /** Whether two texts are the same JSON, ignoring formatting. */
    public static boolean sameJson(final String a, final String b) {
        try {
            return JsonParser.parseString(a).equals(JsonParser.parseString(b));
        } catch (final JsonParseException | NullPointerException e) {
            return Objects.equals(a, b);
        }
    }

    /**
     * The differences between two revisions. Objects are compared key by key; arrays and values that
     * differ are reported as one replacement at their path, like the RFC 6902 patch Philter's diff
     * returns, but with the value before the change as well as after.
     */
    public static List<Difference> differences(final String from, final String to) {
        final List<Difference> differences = new ArrayList<>();
        collect(JsonParser.parseString(from), JsonParser.parseString(to), "", differences);
        return differences;
    }

    private static void collect(final JsonElement from, final JsonElement to, final String path,
                                final List<Difference> differences) {
        if (from.equals(to)) {
            return;
        }
        if (from.isJsonObject() && to.isJsonObject()) {
            final JsonObject fromObject = from.getAsJsonObject();
            final JsonObject toObject = to.getAsJsonObject();
            for (final String key : fromObject.keySet()) {
                final String childPath = path + "/" + key;
                if (!toObject.has(key)) {
                    differences.add(new Difference("remove", childPath, text(fromObject.get(key)), ""));
                } else {
                    collect(fromObject.get(key), toObject.get(key), childPath, differences);
                }
            }
            for (final String key : toObject.keySet()) {
                if (!fromObject.has(key)) {
                    differences.add(new Difference("add", path + "/" + key, "", text(toObject.get(key))));
                }
            }
        } else {
            differences.add(new Difference("replace", path.isEmpty() ? "/" : path, text(from), text(to)));
        }
    }

    private static String text(final JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        return element.isJsonPrimitive() ? element.getAsString() : element.toString();
    }

}
