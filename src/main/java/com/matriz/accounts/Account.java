package com.matriz.accounts;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A person's account (database.md: Account). Owned by CMP-01. */
@Entity
@Table(name = "account")
public class Account {

    @Id
    @Column(name = "account_id")
    private UUID id;

    private String name;

    private String email;

    /** BCrypt hash only (TD-03). Never returned in responses or written to logs. */
    @Column(name = "password_hash")
    private String passwordHash;

    protected Account() {
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    String getPasswordHash() {
        return passwordHash;
    }
}
