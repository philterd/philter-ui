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
package ai.philterd.ui;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PhilterUiApplicationTest {

    @Autowired
    private Environment environment;

    @Test
    void servesTheSignInPage() throws Exception {
        assertEquals(200, get("/login").statusCode());
    }

    @Test
    void sendsSomeoneNotSignedInToTheSignInPage() throws Exception {
        final HttpResponse<String> response = get("/");
        assertEquals(302, response.statusCode());
        assertTrue(response.headers().firstValue("Location").orElse("").endsWith("/login"));
    }

    private HttpResponse<String> get(final String path) throws Exception {
        final String url = "http://localhost:" + environment.getRequiredProperty("local.server.port") + path;
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
    }

}
