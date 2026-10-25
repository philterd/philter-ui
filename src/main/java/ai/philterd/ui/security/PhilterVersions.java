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

import ai.philterd.philter.model.StatusResponse;
import ai.philterd.philter.model.exceptions.ClientException;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides whether Philter UI supports the Philter it signs people in to, from the version
 * {@code GET /api/health} reports. Checked on the sign-in page and at each sign-in rather than at
 * startup, so Philter UI still starts while Philter is down, and an upgraded Philter is noticed
 * without a restart.
 */
public final class PhilterVersions {

    /** The Philter major version this release of Philter UI supports. Keep the README's list in step. */
    public static final int SUPPORTED_MAJOR = 4;

    public static final String OLDER_THAN_4 = "This Philter is older than 4.0, which Philter UI does not support. "
            + "Philter UI supports Philter " + SUPPORTED_MAJOR + ".x.";

    private static final Pattern MAJOR = Pattern.compile("^(\\d+)\\.");

    private PhilterVersions() {
    }

    /**
     * Why Philter UI cannot sign people in to this Philter, or null when it can, or when Philter's
     * version cannot be read; then sign-in goes ahead and reports any failure itself.
     */
    public static String unsupported(final PhilterClients clients) {
        final StatusResponse health;
        try {
            health = clients.anonymous().health();
        } catch (final ClientException e) {
            // Philter 3.x has no /api/health (it had /api/status).
            return e.getStatusCode() == 404 ? OLDER_THAN_4 : null;
        } catch (final IOException | RuntimeException e) {
            return null;
        }
        return unsupported(health.getApplicationVersion());
    }

    public static String unsupported(final String version) {
        if (version == null) {
            return null;
        }
        final Matcher matcher = MAJOR.matcher(version.trim());
        if (!matcher.find() || Integer.parseInt(matcher.group(1)) == SUPPORTED_MAJOR) {
            return null;
        }
        return "This Philter is version " + version.trim() + ", which Philter UI does not support. "
                + "Philter UI supports Philter " + SUPPORTED_MAJOR + ".x.";
    }

}
