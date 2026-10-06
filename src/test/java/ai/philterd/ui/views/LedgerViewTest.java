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

import ai.philterd.philter.model.LedgerChain;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerViewTest {

    private static LedgerChain chain(final boolean valid, final Boolean hashes, final Boolean signatures,
                                     final String validationError) {
        final LedgerChain chain = new LedgerChain();
        chain.setValid(valid);
        chain.setHashChainValid(hashes);
        chain.setSignaturesValid(signatures);
        chain.setValidationError(validationError);
        return chain;
    }

    @Test
    void aChainThatVerifiesIsVerified() {
        final LedgerChain chain = chain(true, true, true, null);
        assertEquals(LedgerView.Verification.VERIFIED, LedgerView.verification(chain));
        assertTrue(LedgerView.verificationDetail(chain).startsWith("The hash chain is intact"));
    }

    @Test
    void aBrokenHashChainOrSignatureIsInvalidAndSaysWhich() {
        assertEquals(LedgerView.Verification.INVALID, LedgerView.verification(chain(false, false, true, null)));
        assertTrue(LedgerView.verificationDetail(chain(false, false, true, null)).startsWith("The hash chain is broken"));
        assertEquals("A signature does not match its entry.", LedgerView.verificationDetail(chain(false, true, false, null)));
        final String both = LedgerView.verificationDetail(chain(false, false, false, null));
        assertTrue(both.contains("hash chain is broken") && both.contains("signature does not match"), both);
    }

    @Test
    void aChainPhilterCouldNotCheckIsNotVerifiedRatherThanInvalid() {
        // Philter leaves the check results out, and valid is false, but this is not evidence of tampering.
        final LedgerChain chain = chain(false, null, null, "An entry could not be read or checked.");
        assertEquals(LedgerView.Verification.NOT_VERIFIED, LedgerView.verification(chain));
        assertEquals("Philter could not check this chain: An entry could not be read or checked.",
                LedgerView.verificationDetail(chain));
    }

    @Test
    void theCountIsWordedForASearchOrTheWholeLedger() {
        assertEquals("5 chains in your ledger.", LedgerView.countLabel(5, false));
        assertEquals("1 chain found.", LedgerView.countLabel(1, true));
        assertEquals("0 chains found.", LedgerView.countLabel(0, true));
    }

    @Test
    void exportFilenamesAreSafe() {
        assertEquals("ledger-7a906866-4fc9-44d6-9bc3-22728b93a602-export.json",
                LedgerView.exportFilename("7a906866-4fc9-44d6-9bc3-22728b93a602"));
        assertEquals("ledger-a_b__c-export.json", LedgerView.exportFilename("a/b\\\"c"));
    }

}
