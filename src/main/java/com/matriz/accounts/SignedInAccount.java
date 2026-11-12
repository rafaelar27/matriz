package com.matriz.accounts;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The signed-in person, stored in the server-side session. It holds only the account id, name and email; the
 * password hash is erased right after sign-in. Roles are not stored here: they are read from the database on every
 * request, so role changes and deactivation take effect immediately (SP-03, SP-04).
 */
public final class SignedInAccount implements UserDetails, CredentialsContainer, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID accountId;
    private final String name;
    private final String email;
    private String passwordHash;

    SignedInAccount(Account account) {
        this.accountId = account.getId();
        this.name = account.getName();
        this.email = account.getEmail();
        this.passwordHash = account.getPasswordHash();
    }

    public UUID accountId() {
        return accountId;
    }

    public String name() {
        return name;
    }

    public String email() {
        return email;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String toString() {
        return "SignedInAccount[" + accountId + "]";
    }
}
