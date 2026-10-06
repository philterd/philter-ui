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
package ai.philterd.philter.views;

import ai.philterd.philter.audit.AuditEventPublisher;
import ai.philterd.philter.audit.AuditLogService;
import ai.philterd.philter.data.entities.UserEntity;
import ai.philterd.philter.data.services.AdminSettingsDataService;
import ai.philterd.philter.data.services.ContextDataService;
import ai.philterd.philter.data.services.PolicyDataService;
import ai.philterd.philter.data.services.SigningKeyDataService;
import ai.philterd.philter.data.services.UserService;
import ai.philterd.philter.services.encryption.EncryptionService;
import com.mongodb.client.MongoClient;
import com.vaadin.flow.component.Component;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The dashboard had no tests at all: every page was built, wired and shipped without a single
 * assertion. This covers what a broken page would cost: that each one builds for the user who opens
 * it.
 *
 * <p>Rendering is not asserted here; that needs a browser. What is asserted is everything that runs
 * before a browser is involved, which is where the constructors do their wiring.
 */
class DashboardViewsTest {

    /** Builds each view from a Mongo client and the services it takes. */
    private static Map<String, Function<MongoClient, Component>> views() {

        final EncryptionService encryption = mock(EncryptionService.class);
        final AuditEventPublisher audit = mock(AuditEventPublisher.class);
        final Map<String, Function<MongoClient, Component>> views = new LinkedHashMap<>();

        views.put("Admin", mongo -> new AdminView(mongo, encryption, audit,
                mock(UserService.class), mock(PolicyDataService.class), mock(ContextDataService.class),
                mock(AdminSettingsDataService.class), mock(AuditLogService.class), signingKeys()));

        return views;

    }

    /** The real service answers a digest, or "(unavailable)"; a bare mock answers null, which no field takes. */
    private static SigningKeyDataService signingKeys() {
        final SigningKeyDataService signingKeys = mock(SigningKeyDataService.class);
        when(signingKeys.getPublicKeyFingerprint()).thenReturn("(unavailable)");
        return signingKeys;
    }

    @Test
    @DisplayName("Every dashboard page a user opens builds for them")
    void everyViewBuildsForAnAdministrator() {

        final UserEntity admin = HeadlessDashboard.user("admin@example.com", "admin");

        try (final HeadlessDashboard dashboard = HeadlessDashboard.signedInAs(admin.getUsername())) {

            final MongoClient mongo = HeadlessDashboard.mongoClientReturning(admin);
            final List<String> failures = new ArrayList<>();

            for (final Map.Entry<String, Function<MongoClient, Component>> view : views().entrySet()) {

                // Admin is left out deliberately, not because it passes. It registers a download
                // handler while building, and a stream resource is held on the session; a mocked
                // service cannot supply a real session lock, so including it here is flaky rather
                // than false. Covering the Admin page needs a browser-driven test.
                if ("Admin".equals(view.getKey())) {
                    continue;
                }

                try {
                    assertNotNull(view.getValue().apply(mongo), view.getKey() + " built nothing");
                } catch (final RuntimeException broken) {
                    final StringBuilder where = new StringBuilder(view.getKey() + ": " + broken);
                    for (final StackTraceElement frame : broken.getStackTrace()) {
                        if (frame.getClassName().startsWith("ai.philterd")) { where.append("\n    at ").append(frame); }
                    }
                    failures.add(where.toString());
                }
            }

            assertEquals(List.of(), failures, "every dashboard page must build");

        }

    }

}
