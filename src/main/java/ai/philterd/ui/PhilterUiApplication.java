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

import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@Theme("philter")
// Vaadin 25 loads all Lumo modules automatically except the utility classes, which the dashboard's
// views use, so load the utility stylesheet explicitly.
@StyleSheet(Lumo.UTILITY_STYLESHEET)
// Override the react-router version that Vaadin's React integration pulls in transitively. The
// platform-bundled version has open security advisories; pin a patched release.
@NpmPackage(value = "react-router", version = "7.18.2")
// No login yet, so no default in-memory user with a generated password.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class PhilterUiApplication implements AppShellConfigurator {

    public static void main(final String[] args) {
        SpringApplication.run(PhilterUiApplication.class, args);
    }

}
