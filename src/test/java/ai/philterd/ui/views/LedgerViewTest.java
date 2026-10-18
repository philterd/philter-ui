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
import ai.philterd.philter.model.LedgerEntry;
import com.vaadin.flow.data.provider.Query;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

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

    @Test
    void anUnreadableChainIsExplainedWithPhiltersWords() {
        assertEquals("Philter could not read this chain's first entry, so the chain cannot be verified or exported. "
                        + "Philter says: This entry could not be read, so its replacement is not shown.",
                LedgerView.unreadableDetail("This entry could not be read, so its replacement is not shown. "));
    }

    /**
     * Philter's chain of {@code total} entries, as {@code GET /api/ledger/{documentId}} pages it: at most 100 per
     * request, with the whole chain's total and checks on every page. The chain fails its hash check.
     */
    private static final class Chain implements LedgerView.ChainReader {

        private final int total;
        final List<int[]> requests = new ArrayList<>();

        Chain(final int total) {
            this.total = total;
        }

        @Override
        public LedgerChain read(final int offset, final int limit) {
            requests.add(new int[]{offset, limit});
            final int from = Math.min(Math.max(0, offset), total);
            final int to = Math.min(total, from + Math.min(limit, 100));
            final LedgerChain page = chain(false, false, true, null);
            page.setEntries(IntStream.range(from, to).mapToObj(i -> {
                final LedgerEntry entry = new LedgerEntry();
                entry.setStartPosition(i);
                return entry;
            }).toList());
            page.setTotal((long) total);
            return page;
        }
    }

    private static Query<LedgerEntry, Void> rows(final int offset, final int limit) {
        return new Query<>(offset, limit, List.of(), null, null);
    }

    @Test
    void everyEntryOfAChainLongerThanOnePageCanBeReached() {
        final Chain philter = new Chain(250);
        final List<Long> totals = new ArrayList<>();
        final ListingProvider<LedgerEntry> provider = new ListingProvider<>(LedgerView.chainEntries(philter), 50)
                .onTotal(totals::add);

        assertEquals(250, provider.count(rows(0, Integer.MAX_VALUE)));
        assertEquals(50, provider.fetch(rows(0, 50)).count());
        assertEquals(1, philter.requests.size(), "the count and the first rows share one request");

        final List<LedgerEntry> last = provider.fetch(rows(200, 50)).toList();
        assertEquals(50, last.size());
        assertEquals(249, last.get(last.size() - 1).getStartPosition(), "the last entry is reached");
        assertEquals(200, philter.requests.get(1)[0]);

        // A range wider than Philter's cap is read in requests of at most 100.
        assertEquals(150, provider.fetch(rows(50, 150)).count());
        assertTrue(philter.requests.stream().allMatch(request -> request[1] <= 100));
        assertTrue(totals.stream().allMatch(total -> total == 250));
    }

    @Test
    void thePagesTotalIsTheWholeChains() {
        final LedgerChain page = new Chain(250).read(0, 1);
        assertEquals(1, LedgerView.entries(page).items().size());
        assertEquals(250, LedgerView.entries(page).total());
        // The verification is the whole chain's, whichever page it came with.
        assertEquals(LedgerView.Verification.INVALID, LedgerView.verification(page));

        // A Philter that gives no total counts only the entries it returned.
        page.setTotal(null);
        assertEquals(1, LedgerView.entries(page).total());
    }

}
