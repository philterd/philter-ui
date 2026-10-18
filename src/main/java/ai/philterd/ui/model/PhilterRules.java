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
package ai.philterd.ui.model;

import ai.philterd.philter.model.Limits;
import ai.philterd.philter.model.PasswordRules;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Philter's rules and limits, and what the signed-in person may do, as Philter reports them from
 * {@code GET /api/limits}, so Philter UI checks input against the rules Philter enforces rather than copies of
 * them. When the limits could not be read, every check passes and Philter refuses invalid input itself, and
 * every capability is off.
 */
public final class PhilterRules {

    private final Limits limits;

    private PhilterRules(final Limits limits) {
        this.limits = limits;
    }

    /** The rules Philter reported. */
    public static PhilterRules of(final Limits limits) {
        return new PhilterRules(limits);
    }

    /** Rules that could not be read: no check is made, and Philter refuses what it does not accept. */
    public static PhilterRules unknown() {
        return new PhilterRules(null);
    }

    /** Whether Philter's limits were read. */
    public boolean isKnown() {
        return limits != null;
    }

    // Passwords.

    private PasswordRules password() {
        return limits == null ? null : limits.getPassword();
    }

    /** The fewest characters a password can have, or {@code null} when not known. */
    public Integer passwordMinimumCharacters() {
        return password() == null ? null : password().getMinCharacters();
    }

    /** The most UTF-8 bytes a password can have, or {@code null} when not known. */
    public Integer passwordMaximumBytes() {
        return password() == null ? null : password().getMaxBytes();
    }

    /** The password rules, for a form's helper text. */
    public String passwordRules() {
        final String advice = "Use a mix of upper and lowercase letters, numbers, and symbols, or a passphrase of 5 to "
                + "7 unrelated words.";
        if (password() == null) {
            return advice;
        }
        return "At least " + password().getMinCharacters() + " characters, and at most " + password().getMaxBytes()
                + " bytes. " + advice;
    }

    /** What is wrong with a password, or {@code null} when it meets the rules or they are not known. */
    public String passwordProblem(final String value) {
        if (password() == null) {
            return null;
        }
        if (value == null || value.length() < password().getMinCharacters()) {
            return "The password must be at least " + password().getMinCharacters() + " characters.";
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > password().getMaxBytes()) {
            return "The password must be at most " + password().getMaxBytes() + " bytes.";
        }
        return null;
    }

    // Names used in request paths: custom lists, contexts, legal hold references.

    /** Whether a name can be used in a request path; {@code true} when the rule is not known. */
    public boolean isPathSafe(final String name) {
        final Limits.Names names = limits == null ? null : limits.getNames();
        if (names == null || name == null) {
            return true;
        }
        if (names.getReservedNames() != null && names.getReservedNames().contains(name)) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            if (names.isControlCharactersForbidden() && Character.isISOControl(c)) {
                return false;
            }
            if (names.getForbiddenCharacters() != null && names.getForbiddenCharacters().contains(String.valueOf(c))) {
                return false;
            }
        }
        return true;
    }

    /** The path-safe name rule in Philter's words, such as "cannot contain /, ...", or a general statement. */
    public String pathSafeRule() {
        final Limits.Names names = limits == null ? null : limits.getNames();
        return names == null || names.getRule() == null ? "contains characters Philter does not allow in a name"
                : names.getRule();
    }

    // Policies.

    private Limits.Policies policies() {
        return limits == null ? null : limits.getPolicies();
    }

    /** The most characters a policy name can have, or {@code null} when not known. */
    public Integer policyNameMaximumLength() {
        return policies() == null ? null : policies().getNameMaxLength();
    }

    /** The policy name rule, for a form's helper text. */
    public String policyNameRule() {
        if (policies() == null) {
            return "Philter checks the name when you save the policy.";
        }
        return "Up to " + policies().getNameMaxLength() + " " + characters(policies().getNamePattern())
                + ", not starting with " + policies().getReservedNamePrefix() + ".";
    }

    /** What is wrong with a name for a new policy, or {@code null} if Philter will accept it or its rules are not known. */
    public String policyNameProblem(final String name) {
        if (name == null || name.isBlank()) {
            return "Enter a name.";
        }
        final Limits.Policies policies = policies();
        if (policies == null) {
            return null;
        }
        if (name.length() > policies.getNameMaxLength()) {
            return "A policy name can be at most " + policies.getNameMaxLength() + " characters.";
        }
        final Pattern pattern = compile(policies.getNamePattern());
        if (pattern != null && !pattern.matcher(name).matches()) {
            return "A policy name can contain only " + characters(policies.getNamePattern()) + ".";
        }
        if (policies.getReservedNamePrefix() != null && name.startsWith(policies.getReservedNamePrefix())) {
            return "A policy name cannot start with " + policies.getReservedNamePrefix()
                    + ", which is reserved for managed policies.";
        }
        if (policies.getReservedNames() != null && policies.getReservedNames().contains(name)) {
            return "The name " + name + " is reserved by Philter.";
        }
        return null;
    }

    /** The most characters a policy description can have, or {@code null} when not known. */
    public Integer policyDescriptionMaximumLength() {
        return policies() == null ? null : policies().getDescriptionMaxLength();
    }

    /** The most characters a policy's notes can have, or {@code null} when not known. */
    public Integer policyNotesMaximumLength() {
        return policies() == null ? null : policies().getNotesMaxLength();
    }

    /** Whether the policy is the one every user is given, which cannot be deleted; {@code false} when not known. */
    public boolean isDefaultPolicy(final String name) {
        return policies() != null && name != null && name.equals(policies().getDefaultPolicyName());
    }

    /** The name of the policy every user is given, or {@code null} when not known. */
    public String defaultPolicyName() {
        return policies() == null ? null : policies().getDefaultPolicyName();
    }

    // Lists.

    /** The most items a custom list can have, or {@code null} when not known. */
    public Integer customListMaximumItems() {
        return limits == null || limits.getCustomLists() == null ? null : limits.getCustomLists().getMaxItems();
    }

    /** The most characters a custom list item can have, or {@code null} when not known. */
    public Integer customListItemMaximumLength() {
        return limits == null || limits.getCustomLists() == null ? null : limits.getCustomLists().getItemMaxLength();
    }

    /**
     * The most characters a custom list description can have, or {@code null} when not known. A Philter that does not
     * report it reads as zero, which is taken as not known rather than as allowing no description.
     */
    public Integer customListDescriptionMaximumLength() {
        if (limits == null || limits.getCustomLists() == null || limits.getCustomLists().getDescriptionMaxLength() <= 0) {
            return null;
        }
        return limits.getCustomLists().getDescriptionMaxLength();
    }

    /** The most terms a redact list can have, or {@code null} when not known. */
    public Integer redactListMaximumTerms() {
        return limits == null || limits.getRedactLists() == null ? null : limits.getRedactLists().getMaxTerms();
    }

    /** The most characters a redact list term can have, or {@code null} when not known. */
    public Integer redactListTermMaximumLength() {
        return limits == null || limits.getRedactLists() == null ? null : limits.getRedactLists().getTermMaxLength();
    }

    // Webhook, holds, users, audit export, paging.

    /** The fewest characters a webhook secret can have, or {@code null} when not known. */
    public Integer webhookSecretMinimumLength() {
        return limits == null || limits.getWebhook() == null ? null : limits.getWebhook().getSecretMinLength();
    }

    /** The scope types a legal hold can have; empty when not known. */
    public List<String> holdScopeTypes() {
        return limits == null || limits.getLegalHolds() == null || limits.getLegalHolds().getScopeTypes() == null
                ? List.of() : List.copyOf(limits.getLegalHolds().getScopeTypes());
    }

    /** The roles a user can have; empty when not known. */
    public List<String> roles() {
        return limits == null || limits.getUsers() == null || limits.getUsers().getRoles() == null
                ? List.of() : List.copyOf(limits.getUsers().getRoles());
    }

    /** The most days between an audit export's from and to dates, or {@code null} when not known. */
    public Integer auditExportMaximumWindowDays() {
        return limits == null || limits.getAuditExport() == null ? null : limits.getAuditExport().getMaxWindowDays();
    }

    /** The most rows a page of the audit export can have, or {@code null} when not known. */
    public Integer auditExportMaximumPageSize() {
        return limits == null || limits.getAuditExport() == null ? null : limits.getAuditExport().getMaxPageSize();
    }

    /** The largest page a listing returns, or {@code null} when not known. */
    public Integer maximumPageSize() {
        return limits == null || limits.getRequests() == null ? null : limits.getRequests().getMaxPageSize();
    }

    /** The largest document Philter redacts, in bytes, or {@code null} when not known. */
    public Long maximumDocumentBytes() {
        return limits == null || limits.getRequests() == null ? null : limits.getRequests().getMaxDocumentBytes();
    }

    // Contexts.

    /** The most contexts a user can have, or {@code null} when not known. */
    public Integer contextLimit() {
        return limits == null || limits.getContexts() == null ? null : limits.getContexts().getMaxPerUser();
    }

    /** Whether the caller already has the most contexts a user can have; {@code false} when not known. */
    public boolean isContextLimitReached() {
        return contextLimit() != null && limits.getCaller() != null
                && limits.getCaller().getContextCount() >= contextLimit();
    }

    // What the caller may do.

    /** The caller's role as Philter reads it now, or {@code null} when not known. */
    public String callerRole() {
        return limits == null || limits.getCaller() == null ? null : limits.getCaller().getRole();
    }

    /** Whether the caller may reach other users' resources; {@code false} when not known. */
    public boolean mayReachOtherUsers() {
        return limits != null && limits.getCaller() != null && limits.getCaller().isCrossUserAccess();
    }

    /** Whether the caller may delete and purge ledger chains; {@code false} when not known. */
    public boolean mayDeleteLedgerChains() {
        return limits != null && limits.getCaller() != null && limits.getCaller().isLedgerDeletion();
    }

    // Helpers.

    private static Pattern compile(final String pattern) {
        try {
            return pattern == null ? null : Pattern.compile(pattern);
        } catch (final PatternSyntaxException e) {
            return null;
        }
    }

    /**
     * The characters a pattern such as {@code ^[a-zA-Z0-9_-]+$} allows, in words, or the pattern itself when it is
     * not that simple form.
     */
    static String characters(final String pattern) {
        if (pattern == null || !pattern.startsWith("^[") || !pattern.endsWith("]+$")) {
            return "characters matching " + pattern;
        }
        String set = pattern.substring(2, pattern.length() - 3);
        final List<String> kinds = new ArrayList<>();
        final boolean lower = set.contains("a-z");
        final boolean upper = set.contains("A-Z");
        set = set.replace("a-z", "").replace("A-Z", "");
        if (lower && upper) {
            kinds.add("letters");
        } else if (lower) {
            kinds.add("lowercase letters");
        } else if (upper) {
            kinds.add("uppercase letters");
        }
        if (set.contains("0-9")) {
            kinds.add("digits");
            set = set.replace("0-9", "");
        }
        if (set.contains("-")) {
            kinds.add("dashes");
            set = set.replace("-", "");
        }
        if (set.contains("_")) {
            kinds.add("underscores");
            set = set.replace("_", "");
        }
        if (!set.isEmpty() || kinds.isEmpty()) {
            return "characters matching " + pattern;
        }
        return join(kinds);
    }

    private static String join(final List<String> words) {
        if (words.size() == 1) {
            return words.get(0);
        }
        if (words.size() == 2) {
            return words.get(0) + " and " + words.get(1);
        }
        return String.join(", ", words.subList(0, words.size() - 1)) + ", and " + words.get(words.size() - 1);
    }

    @Override
    public String toString() {
        return "PhilterRules[" + (limits == null ? "unknown" : "known") + "]";
    }

}
