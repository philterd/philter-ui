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

import ai.philterd.philter.PhilterClient;
import ai.philterd.philter.model.ApiKey;
import ai.philterd.philter.model.Limits;
import ai.philterd.philter.model.exceptions.ErrorReasons;
import ai.philterd.philter.model.exceptions.ClientException;
import ai.philterd.philter.model.exceptions.ServiceUnavailableException;
import ai.philterd.ui.model.Pages;
import ai.philterd.ui.security.PhilterClients;
import ai.philterd.ui.security.PhilterUser;
import ai.philterd.ui.security.Roles;
import ai.philterd.ui.security.Sessions;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AttachmentType;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.UploadFormat;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import com.vaadin.flow.server.streams.UploadHandler;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.beans.factory.annotation.Value;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Tests a policy: redacts text or a PDF with one of the person's policies, through Philter's filter API.
 * No context is sent, so nothing is stored for later requests and no ledger is written.
 */
@Route(value = "", layout = MainLayout.class)
@PageTitle("Dashboard | Philter UI")
@RolesAllowed(Roles.USER)
public class DashboardView extends VerticalLayout {

    /** The largest PDF Philter accepts by default ({@code MAX_FILE_SIZE_BYTES}), used when its limits cannot be read. */
    static final long DEFAULT_PDF_BYTES = 10L * 1024 * 1024;

    static final String DEFAULT_POLICY = "default";

    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final transient PhilterClient client;
    private final transient PhilterClient documentClient;
    private final ComboBox<String> policy = new ComboBox<>("Policy");

    private byte[] pdf;
    private String pdfName;

    /** The largest PDF this page accepts, which is held in memory while Philter redacts it. */
    private final long maxPdfBytes;

    public DashboardView(final PhilterClients clients, @Value("${philter.max-pdf-bytes}") final long configuredMaxPdfBytes) {

        final PhilterUser user = Sessions.currentUser().orElseThrow();
        this.client = clients.forUser(user);
        this.documentClient = clients.forDocuments(user);
        this.maxPdfBytes = pdfLimit(philterDocumentLimit(), configuredMaxPdfBytes);

        setSizeFull();
        add(new H2("Dashboard"));

        if (hasBootstrapKey()) {
            final Paragraph notice = new Paragraph("Your bootstrap API key, from PHILTER_BOOTSTRAP_API_KEY, is still "
                    + "active. Create a key of your own with Philter's API, then revoke the bootstrap key on the My "
                    + "Account page. ");
            notice.add(PhilterDocs.link("See how to create a key.", "api_and_sdks/api/api_keys_api.html#create-a-key"));
            notice.getStyle().set("color", "var(--lumo-warning-text-color, var(--lumo-error-text-color))");
            add(notice);
        }

        final List<String> policies = ViewSupport.unchecked(() -> Pages.read(0, Integer.MAX_VALUE,
                (offset, limit) -> ViewSupport.orEmpty(client.getPolicies(null, offset, limit))));
        policy.setItems(policies);
        policy.setPlaceholder("Select a policy");
        policy.setWidth("400px");
        if (policies.contains(DEFAULT_POLICY)) {
            policy.setValue(DEFAULT_POLICY);
        } else if (policies.size() == 1) {
            policy.setValue(policies.get(0));
        }
        policy.setHelperText(policies.isEmpty() ? "You have no policies. Create one on the Redaction Policies page."
                : null);

        final TabSheet tabs = new TabSheet();
        tabs.add("Text", textTab());
        tabs.add("PDF", pdfTab());
        tabs.setSizeFull();

        add(new Paragraph("Redact text or a PDF with one of your policies to see what it finds. Philter records each "
                + "redaction under your user, as it does any request to its API."), policy, tabs);

    }

    private boolean hasBootstrapKey() {
        return ViewSupport.unchecked(() -> Pages.read(0, Integer.MAX_VALUE,
                        (offset, limit) -> ViewSupport.orEmpty(client.getApiKeys(null, offset, limit, false).getApiKeys())))
                .stream().anyMatch(ApiKey::isBootstrap);
    }

    private Component textTab() {

        final TextArea text = new TextArea("Text");
        text.setWidthFull();
        text.setHeight("250px");
        text.setValue("George Washington was president.");

        final TextArea redacted = new TextArea("Redacted text");
        redacted.setWidthFull();
        redacted.setHeight("250px");
        redacted.setReadOnly(true);
        redacted.setVisible(false);

        final Button submit = new Button("Redact Text", VaadinIcon.ERASER.create());
        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submit.setDisableOnClick(true);
        submit.addClickListener(e -> {
            try {
                final String problem = textProblem(policy.getValue(), text.getValue());
                if (problem != null) {
                    Notifications.failure(problem);
                    return;
                }
                final String result = client.filter(null, policy.getValue(), text.getValue()).getFilteredText();
                redacted.setValue(result == null ? "" : result);
                redacted.setVisible(true);
            } catch (final ClientException | ServiceUnavailableException | IOException ex) {
                Notifications.failure(ex, "Philter could not redact the text.");
            } finally {
                submit.setEnabled(true);
            }
        });

        final VerticalLayout layout = new VerticalLayout(text, submit, redacted);
        layout.setMaxWidth("900px");
        return layout;

    }

    private Component pdfTab() {

        final Anchor download = new Anchor();
        download.setVisible(false);

        final Upload upload = new Upload(UploadHandler.inMemory((metadata, data) -> {
            pdf = data;
            pdfName = metadata.fileName();
            download.setVisible(false);
        }));
        // Raw, not multipart: the servlet container would spool a multipart upload to a temporary file.
        upload.setUploadFormat(UploadFormat.RAW);
        upload.setAcceptedFileTypes("application/pdf", ".pdf");
        upload.setMaxFiles(1);
        upload.setMaxFileSize((int) Math.min(maxPdfBytes, Integer.MAX_VALUE));
        upload.addFileRejectedListener(e -> Notifications.failure(e.getErrorMessage()));
        upload.addFileRemovedListener(e -> {
            pdf = null;
            pdfName = null;
            download.setVisible(false);
        });

        final Button submit = new Button("Redact PDF", VaadinIcon.ERASER.create());
        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submit.setDisableOnClick(true);
        submit.addClickListener(e -> {
            try {
                final String problem = pdfProblem(policy.getValue(), pdf, maxPdfBytes);
                if (problem != null) {
                    Notifications.failure(problem);
                    return;
                }
                // The byte[] overload, so the upload is never written to disk.
                final byte[] result = documentClient.filterToPdf(null, policy.getValue(), pdfName, pdf).getContent();
                final String name = redactedFilename(pdfName);
                download.setHref(DownloadHandler.fromInputStream(event -> new DownloadResponse(
                        new ByteArrayInputStream(result), name, "application/pdf", result.length)),
                        AttachmentType.DOWNLOAD);
                download.setText("Download " + name);
                download.setVisible(true);
                Notifications.success("PDF redacted.");
            } catch (final ClientException ex) {
                if (isTooLarge(ex)) {
                    Notifications.failure(tooLarge(maxPdfBytes));
                } else {
                    Notifications.failure(ex, "Philter could not redact the PDF.");
                }
            } catch (final ServiceUnavailableException | IOException ex) {
                Notifications.failure(ex, "Philter could not redact the PDF.");
            } finally {
                submit.setEnabled(true);
            }
        });

        final VerticalLayout layout = new VerticalLayout(new Paragraph("Upload a PDF of at most "
                + megabytes(maxPdfBytes) + ". Philter returns the redacted PDF to download."),
                upload, submit, download);
        layout.setMaxWidth("900px");
        return layout;

    }

    // Logic the tests cover.

    /** What stops the text being sent, or {@code null}. */
    static String textProblem(final String policyName, final String text) {
        if (policyName == null || policyName.isBlank()) {
            return "Select a policy.";
        }
        if (text == null || text.isBlank()) {
            return "Enter text to redact.";
        }
        return null;
    }

    /** What stops the PDF being sent, or {@code null}, checking its size against {@code limit} too. */
    static String pdfProblem(final String policyName, final byte[] content, final long limit) {
        final String problem = pdfProblem(policyName, content);
        if (problem == null && content.length > limit) {
            return tooLarge(limit);
        }
        return problem;
    }

    /**
     * The largest PDF to accept: Philter's limit when it could be read, lowered to {@code configured} when that
     * is set ({@code MAX_PDF_BYTES}) and smaller. Without Philter's limit, {@code configured} if set, else
     * Philter's default.
     */
    static long pdfLimit(final Long philterLimit, final long configured) {
        if (philterLimit != null && philterLimit > 0) {
            return configured > 0 ? Math.min(philterLimit, configured) : philterLimit;
        }
        return configured > 0 ? configured : DEFAULT_PDF_BYTES;
    }

    /** Whether Philter refused the document as too large. */
    static boolean isTooLarge(final ClientException e) {
        return e.getStatusCode() == 413 || ErrorReasons.PAYLOAD_TOO_LARGE.equals(e.getReason());
    }

    /** What to tell a person whose PDF is over the limit. */
    static String tooLarge(final long limit) {
        return "The PDF is too large. Philter accepts a PDF of at most " + megabytes(limit) + ".";
    }

    /** A size in megabytes, as a person reads it: 10 MB, or 2.5 MB. */
    static String megabytes(final long bytes) {
        final double megabytes = bytes / (1024.0 * 1024.0);
        return (megabytes == Math.rint(megabytes) ? String.valueOf((long) megabytes)
                : String.format(java.util.Locale.ROOT, "%.1f", megabytes)) + " MB";
    }

    /** Philter's largest document, or {@code null} when its limits cannot be read. */
    private Long philterDocumentLimit() {
        try {
            final Limits limits = client.getLimits();
            return limits == null || limits.getRequests() == null ? null : limits.getRequests().getMaxDocumentBytes();
        } catch (final ClientException | ServiceUnavailableException | IOException e) {
            // The page still works, with the configured or default limit.
            return null;
        }
    }

    /** What stops the PDF being sent, or {@code null}. */
    static String pdfProblem(final String policyName, final byte[] content) {
        if (policyName == null || policyName.isBlank()) {
            return "Select a policy.";
        }
        if (content == null || content.length == 0) {
            return "Upload a PDF.";
        }
        if (content.length < PDF_SIGNATURE.length
                || !Arrays.equals(content, 0, PDF_SIGNATURE.length, PDF_SIGNATURE, 0, PDF_SIGNATURE.length)) {
            return "The file is not a PDF.";
        }
        return null;
    }

    /** The download's name: the upload's, with anything unsafe in a filename replaced, and marked redacted. */
    static String redactedFilename(final String uploaded) {
        String base = uploaded == null ? "" : uploaded.replaceAll("^.*[/\\\\]", "");
        if (base.toLowerCase().endsWith(".pdf")) {
            base = base.substring(0, base.length() - 4);
        }
        base = base.replaceAll("[^A-Za-z0-9._ -]", "_").strip();
        return (base.isEmpty() || base.chars().allMatch(c -> c == '.') ? "document" : base) + "-redacted.pdf";
    }

}
