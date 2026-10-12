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
import ai.philterd.ui.model.Listing;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardViewTest {

    private static final byte[] PDF = "%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII);

    @Test
    void textNeedsAPolicyAndText() {
        assertNull(DashboardView.textProblem("default", "George Washington was president."));
        assertEquals("Select a policy.", DashboardView.textProblem(null, "text"));
        assertEquals("Select a policy.", DashboardView.textProblem(" ", "text"));
        assertEquals("Enter text to redact.", DashboardView.textProblem("default", ""));
        assertEquals("Enter text to redact.", DashboardView.textProblem("default", " \n "));
        assertEquals("Enter text to redact.", DashboardView.textProblem("default", null));
    }

    @Test
    void aPdfNeedsAPolicyAndAPdf() {
        assertNull(DashboardView.pdfProblem("default", PDF));
        assertEquals("Select a policy.", DashboardView.pdfProblem(null, PDF));
        assertEquals("Upload a PDF.", DashboardView.pdfProblem("default", null));
        assertEquals("Upload a PDF.", DashboardView.pdfProblem("default", new byte[0]));
        assertEquals("The file is not a PDF.", DashboardView.pdfProblem("default", "%PD".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("The file is not a PDF.", DashboardView.pdfProblem("default",
                "<html>%PDF-1.7</html>".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void theDownloadIsNamedAfterTheUpload() {
        assertEquals("statement-redacted.pdf", DashboardView.redactedFilename("statement.pdf"));
        assertEquals("Statement-redacted.pdf", DashboardView.redactedFilename("Statement.PDF"));
        assertEquals("Q3 report-redacted.pdf", DashboardView.redactedFilename("Q3 report.pdf"));
        assertEquals("scan-redacted.pdf", DashboardView.redactedFilename("C:\\Users\\someone\\scan.pdf"));
        assertEquals("scan-redacted.pdf", DashboardView.redactedFilename("../../scan.pdf"));
        assertEquals("a_b_c-redacted.pdf", DashboardView.redactedFilename("a\"b;c.pdf"));
        assertEquals("notes.txt-redacted.pdf", DashboardView.redactedFilename("notes.txt"));
        assertEquals("document-redacted.pdf", DashboardView.redactedFilename(null));
        assertEquals("document-redacted.pdf", DashboardView.redactedFilename(".pdf"));
        assertEquals("document-redacted.pdf", DashboardView.redactedFilename("...pdf"));
    }


    private static final long MB = 1024 * 1024;

    @Test
    void theLimitIsPhiltersLoweredByTheSetting() {
        assertEquals(10 * MB, DashboardView.pdfLimit(10 * MB, 0), "Philter's limit when no setting is given");
        assertEquals(25 * MB, DashboardView.pdfLimit(25 * MB, 0), "a raised Philter limit is followed");
        assertEquals(5 * MB, DashboardView.pdfLimit(25 * MB, 5 * MB), "the setting can lower it");
        assertEquals(25 * MB, DashboardView.pdfLimit(25 * MB, 50 * MB), "but not raise it past Philter's");
        assertEquals(10 * MB, DashboardView.pdfLimit(null, 0), "Philter's default when its limits cannot be read");
        assertEquals(20 * MB, DashboardView.pdfLimit(null, 20 * MB), "or the setting");
    }

    @Test
    void aPdfOverTheLimitIsRefusedBeforeItIsSent() {
        final byte[] large = new byte[(int) (2 * MB)];
        System.arraycopy(PDF, 0, large, 0, PDF.length);
        assertNull(DashboardView.pdfProblem("default", large, 2 * MB));
        assertEquals("The PDF is too large. Philter accepts a PDF of at most 1.5 MB.",
                DashboardView.pdfProblem("default", large, (long) (1.5 * MB)));
        assertEquals("Select a policy.", DashboardView.pdfProblem(null, large, MB), "other problems come first");
    }

    @Test
    void philtersSizeRefusalIsNamed() {
        assertTrue(DashboardView.isTooLarge(new ClientException("HTTP 413", 413, "Too large.", "payload_too_large")));
        assertFalse(DashboardView.isTooLarge(new ClientException("HTTP 400", 400, "Bad.", "invalid_request")));
        assertEquals("The PDF is too large. Philter accepts a PDF of at most 10 MB.", DashboardView.tooLarge(10 * MB));
    }

    @Test
    void thePickerStartsOnTheDefaultPolicyOrTheOnlyOne() {
        assertEquals("default", DashboardView.initialPolicy(new Listing<>(List.of("alpha"), 3), "default",
                List.of("default", "default-copy")));
        assertNull(DashboardView.initialPolicy(new Listing<>(List.of("alpha"), 3), "default", List.of("default-copy")));
        assertEquals("claims", DashboardView.initialPolicy(new Listing<>(List.of("claims"), 1), "default", List.of()));
        assertNull(DashboardView.initialPolicy(new Listing<>(List.of(), 0), "default", List.of()));
        assertNull(DashboardView.initialPolicy(new Listing<>(List.of("alpha"), 2), null, List.of()));
    }

}
