package com.matriz.accounts;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public interface of CMP-01 for other modules: people's names, to show owners and authors (FR-08, FR-13). */
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
}
