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
import ai.philterd.philter.model.exceptions.ClientException;
import ai.philterd.ui.model.PolicyJson;
import ai.philterd.ui.security.PhilterUnreachableException;
import ai.philterd.ui.security.SignInMessages;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.provider.Query;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelsViewTest {

    private static Model model(final String name, final String encoder, final Integer maxLength,
                               final Integer maxWidth, final long sizeBytes) {
        final Model model = new Model();
        model.setName(name);
        model.setEncoder(encoder);
        model.setMaxLength(maxLength);
        model.setMaxWidth(maxWidth);
        model.setSizeBytes(sizeBytes);
        return model;
    }

    @Test
    void modelsAreListedByName() {
        final Model small = model("pii-en-small", "microsoft/deberta-v3-small", 512, 12, 580_000_000L);
        final Model xsmall = model("pii-en-xsmall", null, null, null, 98_724_945L);
        final List<Model> models = ModelsView.read(() -> List.of(xsmall, small));
        assertEquals(List.of(small, xsmall), models);

        final Component content = ModelsView.content(models);
        assertEquals("2 models", content.getChildren().filter(Span.class::isInstance).map(Span.class::cast)
                .findFirst().orElseThrow().getText());
        @SuppressWarnings("unchecked")
        final Grid<Model> grid = (Grid<Model>) content.getChildren().filter(Grid.class::isInstance).findFirst()
                .orElseThrow();
        assertEquals(List.of(small, xsmall), grid.getDataProvider().fetch(new Query<>()).toList());
        assertEquals(List.of("Name", "Encoder", "Maximum Words", "Maximum Entity Width", "Size"),
                grid.getColumns().stream().map(Grid.Column::getHeaderText).toList());
    }

    @Test
    void absentValuesAreBlank() {
        assertEquals("", ModelsView.blankIfNull(null));
        assertEquals("512", ModelsView.blankIfNull(512));
        assertEquals("microsoft/deberta-v3-xsmall", ModelsView.blankIfNull("microsoft/deberta-v3-xsmall"));
    }

    @Test
    void sizesAreReadable() {
        assertEquals("94.2 MB", ModelsView.size(98_724_945L));
        assertEquals("553.1 MB", ModelsView.size(580_000_000L));
        assertEquals("1.5 GB", ModelsView.size(1_610_612_736L));
        assertEquals("16 KB", ModelsView.size(16_384L));
        assertEquals("200 bytes", ModelsView.size(200L));
        assertEquals("0 bytes", ModelsView.size(0L));
        assertEquals("1 MB", ModelsView.size(1024L * 1024 - 1), "rounds up into the next unit, not to 1024 KB");
        assertEquals("1 GB", ModelsView.size(1024L * 1024 * 1024 - 1));
    }

    @Test
    void theExampleIsAPolicyNamingTheModel() {
        final String example = ModelsView.example("pii-en-small");
        assertNull(PolicyJson.problem(example));
        assertTrue(example.contains("\"modelPath\": \"pii-en-small\""));
        assertTrue(example.contains("{{{REDACTED-%t}}}"));
    }

    @Test
    void noModelsSaysHowToAddThem() {
        assertEquals(List.of(), ModelsView.read(List::of));
        assertEquals(List.of(), ModelsView.read(() -> null));
        final Component content = ModelsView.content(List.of());
        assertEquals(ModelsView.NO_MODELS, assertInstanceOf(Paragraph.class, content).getText());
    }

    @Test
    void aRefusalOrUnreachablePhilterOpensTheFailurePage() {
        final ClientException refused = new ClientException("HTTP 403", 403, "The key lacks the policies:read scope.",
                "missing_scope");
        assertSame(refused, assertThrows(ClientException.class, () -> ModelsView.read(() -> {
            throw refused;
        })));
        assertEquals("The key lacks the policies:read scope.", PhilterFailureView.message(refused));

        final PhilterUnreachableException unreachable = assertThrows(PhilterUnreachableException.class,
                () -> ModelsView.read(() -> {
                    throw new IOException("Connection refused");
                }));
        assertEquals(SignInMessages.UNAVAILABLE, PhilterFailureView.message(unreachable));
    }

}
