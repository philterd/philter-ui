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
package ai.philterd.ui.views;

import ai.philterd.philter.model.Model;
import ai.philterd.ui.security.PhilterClients;
import ai.philterd.ui.security.Roles;
import ai.philterd.ui.security.Sessions;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The PhEye models the Philter deployment has in its models directory, through Philter's models API, so a person
 * writing a policy can see the names they may use. The models are the deployment's, the same for every user. They
 * are read each time the page opens, since Philter reads its directory on each request.
 */
@Route(value = "models", layout = MainLayout.class)
@PageTitle("Models | Philter UI")
@RolesAllowed(Roles.USER)
public class ModelsView extends VerticalLayout {

    /** Shown in place of the grid when the deployment has no models. */
    static final String NO_MODELS = "This Philter deployment has no models. An administrator adds a model by "
            + "placing it in Philter's models directory, set by PHEYE_MODELS_DIRECTORY; it can be used without "
            + "restarting Philter.";

    /** The model named in the example when the deployment has none. */
    static final String EXAMPLE_MODEL = "pii-en-xsmall";

    public ModelsView(final PhilterClients clients) {

        // A refusal, or Philter being unreachable, is thrown so the page shows it in place of its content.
        final List<Model> models = read(clients.forUser(Sessions.currentUser().orElseThrow())::getModels);

        setSizeFull();

        final Span description = new Span("PhEye models run inside Philter to find sensitive information. A "
                + "policy's ph-eye filter uses one by giving its name as phEyeConfiguration.modelPath. ");
        description.add(PhilterDocs.link("Learn more about models.", "api_and_sdks/api/models_api.html"));

        add(new H2("Models"), description,
                new Pre(example(models.isEmpty() ? EXAMPLE_MODEL : models.get(0).getName())), content(models));

    }

    /** The deployment's models, ordered by name. */
    static List<Model> read(final ViewSupport.PhilterQuery<List<Model>> query) {
        return ViewSupport.orEmpty(ViewSupport.unchecked(query)).stream()
                .sorted(Comparator.comparing(Model::getName, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** A policy whose ph-eye filter uses the named model, as in Philter's models API documentation. */
    static String example(final String modelName) {
        return """
                {
                  "identifiers": {
                    "pheyes": [
                      {
                        "phEyeConfiguration": {
                          "modelPath": "%s",
                          "labels": ["name"]
                        },
                        "phEyeFilterStrategies": [
                          { "strategy": "REDACT", "redactionFormat": "{{{REDACTED-%%t}}}" }
                        ]
                      }
                    ]
                  }
                }""".formatted(modelName);
    }

    /** A grid of the models with their count, or {@link #NO_MODELS} when there are none. */
    static Component content(final List<Model> models) {

        if (models.isEmpty()) {
            return new Paragraph(NO_MODELS);
        }

        final Grid<Model> grid = new Grid<>();
        grid.addColumn(Model::getName).setHeader("Name").setAutoWidth(true).setResizable(true);
        grid.addColumn(model -> blankIfNull(model.getEncoder())).setHeader("Encoder").setAutoWidth(true)
                .setResizable(true);
        grid.addColumn(model -> blankIfNull(model.getMaxLength())).setHeader("Maximum Words").setAutoWidth(true)
                .setTooltipGenerator(model -> "The most words the model reads in one call.");
        grid.addColumn(model -> blankIfNull(model.getMaxWidth())).setHeader("Maximum Entity Width").setAutoWidth(true)
                .setTooltipGenerator(model -> "The most words one detected entity can span.");
        grid.addColumn(model -> size(model.getSizeBytes())).setHeader("Size").setAutoWidth(true);
        grid.setItems(models);
        grid.setSizeFull();

        final VerticalLayout layout = new VerticalLayout(
                new Span(ListingProvider.count(models.size(), "model", "models")), grid);
        layout.setPadding(false);
        layout.setSizeFull();
        return layout;

    }

    /** A value Philter may leave out, blank when it did. */
    static String blankIfNull(final Object value) {
        return value == null ? "" : value.toString();
    }

    private static final String[] UNITS = {"KB", "MB", "GB", "TB"};

    /** A size as a person reads it: 94.2 MB, 1.5 GB, or 16 KB. */
    static String size(final long bytes) {
        if (bytes < 1024) {
            return bytes + (bytes == 1 ? " byte" : " bytes");
        }
        double value = bytes / 1024.0;
        int unit = 0;
        // Moves up a unit when the value would print as 1024 or more, so 1023.96 MB reads as 1 GB.
        while (unit < UNITS.length - 1 && Math.round(value * 10) >= 1024 * 10) {
            value /= 1024;
            unit++;
        }
        final String number = String.format(Locale.ROOT, "%.1f", value);
        return (number.endsWith(".0") ? number.substring(0, number.length() - 2) : number) + " " + UNITS[unit];
    }

}
