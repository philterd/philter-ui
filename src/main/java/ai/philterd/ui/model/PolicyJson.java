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
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.Objects;

/** Policy JSON as Philter UI shows and checks it. Philter does the full validation on save. */
public final class PolicyJson {

    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private PolicyJson() {
    }

    /** The smallest policy, which a new policy starts from when Philter's template cannot be read. */
    public static final String EMPTY = "{\n  \"identifiers\": {}\n}";

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

}
