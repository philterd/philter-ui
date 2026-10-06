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

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

}
