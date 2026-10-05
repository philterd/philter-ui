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

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/** Draws a QR code as an SVG data URI, so an authenticator app can scan the MFA setup URI. */
final class QrCodes {

    private QrCodes() {
    }

    static String svgDataUri(final String content) {

        final BitMatrix matrix;
        try {
            matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, Map.of(EncodeHintType.MARGIN, 2));
        } catch (final WriterException e) {
            throw new IllegalArgumentException("Could not draw a QR code.", e);
        }

        final StringBuilder path = new StringBuilder();
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                if (matrix.get(x, y)) {
                    path.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
                }
            }
        }

        final String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + matrix.getWidth() + " "
                + matrix.getHeight() + "\" shape-rendering=\"crispEdges\"><rect width=\"100%\" height=\"100%\" "
                + "fill=\"#fff\"/><path d=\"" + path + "\" fill=\"#000\"/></svg>";

        return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));

    }

}
