package com.matriz.accounts;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByEmail(String email);

    /** Case-insensitive lookup, backed by the unique index on lower(email) (BR-24, migration V5). */
    @Query("select a from Account a where lower(a.email) = lower(:email)")
    Optional<Account> findByEmailIgnoreCase(@Param("email") String email);
}
