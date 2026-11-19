package com.matriz.accounts;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public interface of CMP-01 for other modules: people's names, to show owners and authors (FR-08, FR-13), and the
 * email rule and lookups that invitations need (FR-03, BR-24, DA-01).
 */
@Service
public class AccountDirectory {

    private final AccountRepository accounts;

    AccountDirectory(AccountRepository accounts) {
        this.accounts = accounts;
    }

    /** Names by account id. Unknown ids are left out. */
    @Transactional(readOnly = true)
    public Map<UUID, String> namesOf(Collection<UUID> accountIds) {
        return accounts.findAllById(accountIds).stream()
                .collect(Collectors.toMap(Account::getId, Account::getName));
    }

    /**
     * The email in its stored form (trimmed, lower case) if it follows the sign-up rule BR-24; empty otherwise.
     * Invitation emails use the same rule (decided 2026-11-19, Q7), so they match accounts exactly (DA-01).
     */
    public static Optional<String> validEmail(String raw) {
        String clean = SignUpService.normalizeEmail(raw);
        return SignUpService.looksLikeEmail(clean) ? Optional.of(clean) : Optional.empty();
    }

    /** The account registered with this email, ignoring letter case (BR-24); empty if there is none. */
    @Transactional(readOnly = true)
    public Optional<UUID> accountIdByEmail(String email) {
        return accounts.findByEmailIgnoreCase(email).map(Account::getId);
    }
}
