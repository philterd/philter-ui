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

import ai.philterd.philter.model.exceptions.ClientException;
import ai.philterd.philter.model.exceptions.ServiceUnavailableException;
import ai.philterd.ui.security.PhilterFailures;
import ai.philterd.ui.security.PhilterUnreachableException;
import ai.philterd.ui.security.SignInMessages;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailureNoticeTest {

    private static Stream<Component> all(final Component component) {
        return Stream.concat(Stream.of(component), component.getChildren().flatMap(FailureNoticeTest::all));
    }

    private static List<String> texts(final Component component) {
        return all(component).filter(Span.class::isInstance).map(c -> ((Span) c).getText()).toList();
    }

    @Test
    void aRefusalIsShownWithItsRequestIdToCopy() {
        final ClientException refused = new ClientException("HTTP 409", 409, "The policy changed.", "policy_changed",
                "req-7f3a");
        final String requestId = PhilterFailures.requestIdFor(refused);
        assertEquals("req-7f3a", requestId);

        final Component notice = FailureNotice.content(Notifications.failureMessage(refused, "Not saved."), requestId);
        assertEquals(List.of("The policy changed.", "Request ID: req-7f3a"), texts(notice));
        assertTrue(all(notice).anyMatch(c -> c instanceof Button button && "Copy".equals(button.getText())));
    }

    @Test
    void aFailureWithoutAPhilterResponseHasNoRequestId() {
        assertNull(PhilterFailures.requestIdFor(new PhilterUnreachableException(new ConnectException("refused"))));
        assertNull(PhilterFailures.requestIdFor(new IOException("refused")));
        assertNull(PhilterFailures.requestIdFor(new ClientException("HTTP 400", 400, "Bad.")));

        final Component notice = FailureNotice.content(SignInMessages.UNAVAILABLE, null);
        assertEquals(List.of(SignInMessages.UNAVAILABLE), texts(notice));
        assertTrue(all(notice).noneMatch(Button.class::isInstance));
    }

    @Test
    void theRequestIdIsFoundWhereverItIsWrapped() {
        assertEquals("req-1", PhilterFailures.requestIdFor(new IllegalStateException(
                new ServiceUnavailableException("HTTP 503", "Down.", "service_unavailable", "req-1"))));
    }

    @Test
    void theMessageFallsBackWhenPhilterGaveNone() {
        assertEquals("Not saved.", Notifications.failureMessage(new ClientException("HTTP 500", 500, null), "Not saved."));
        assertEquals(SignInMessages.UNAVAILABLE, Notifications.failureMessage(new IOException("down"), "Not saved."));
    }

}
