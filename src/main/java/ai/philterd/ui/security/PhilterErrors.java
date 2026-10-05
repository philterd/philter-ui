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

import ai.philterd.philter.model.exceptions.ClientException;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;

import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a {@link ClientException}. The SDK carries the HTTP status and Philter's error body only in the
 * exception's message ({@code Unknown error: HTTP 404: {...}}), so they are parsed out of it.
 */
public final class PhilterErrors {

    private static final Pattern STATUS = Pattern.compile("HTTP (\\d{3})(?:: (.*))?", Pattern.DOTALL);

    private PhilterErrors() {
    }

    /** The HTTP status Philter returned, if the message names one. */
    public static OptionalInt status(final ClientException exception) {
        final Matcher matcher = matcher(exception);
        return matcher == null ? OptionalInt.empty() : OptionalInt.of(Integer.parseInt(matcher.group(1)));
    }

    public static boolean hasStatus(final ClientException exception, final int status) {
        final OptionalInt actual = status(exception);
        return actual.isPresent() && actual.getAsInt() == status;
    }

    /** The {@code message} field of Philter's error body, or {@code null}. */
    public static String message(final ClientException exception) {
        final Matcher matcher = matcher(exception);
        if (matcher == null || matcher.group(2) == null) {
            return null;
        }
        try {
            final JsonElement body = JsonParser.parseString(matcher.group(2));
            if (body.isJsonObject() && body.getAsJsonObject().has("message")
                    && body.getAsJsonObject().get("message").isJsonPrimitive()) {
                return body.getAsJsonObject().get("message").getAsString();
            }
        } catch (final JsonParseException | IllegalStateException e) {
            // Not JSON, or cut off by the SDK; there is no message to show.
        }
        return null;
    }

    private static Matcher matcher(final ClientException exception) {
        if (exception.getMessage() == null) {
            return null;
        }
        final Matcher matcher = STATUS.matcher(exception.getMessage());
        return matcher.find() ? matcher : null;
    }

}
