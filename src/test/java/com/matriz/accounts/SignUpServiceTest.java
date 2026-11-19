package com.matriz.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.matriz.shared.ApiException;

/**
 * How sign-up reports a data error from the database (INC-12; EC-12, BR-24; US-22; audit S-06) as pure logic, with a
 * mocked repository. No Spring context and no database.
 */
class SignUpServiceTest {

    private AccountRepository accounts;
    private SignUpService service;

    @BeforeEach
    void setUp() {
        accounts = mock(AccountRepository.class);
        service = new SignUpService(accounts, new BCryptPasswordEncoder(4));
    }

    @Test
    @DisplayName("EC-12 · BR-24 · a unique violation while the email now exists is reported as 409 email_taken")
    void uniqueViolationWithExistingEmailIsEmailTaken() {
        when(accounts.findByEmailIgnoreCase("nia@example.com"))
                .thenReturn(Optional.empty(), Optional.of(mock(Account.class)));
        when(accounts.saveAndFlush(any(Account.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.signUp("Nia", "nia@example.com", "fictitious-nia-pw-1"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.error().code()).isEqualTo("email_taken");
                });
    }

    @Test
    @DisplayName("EC-12 · audit S-06 · a data error when the email is not taken is not reported as email_taken")
    void otherDataErrorIsNotEmailTaken() {
        when(accounts.findByEmailIgnoreCase("nia@example.com")).thenReturn(Optional.empty());
        DataIntegrityViolationException failure = new DataIntegrityViolationException("not null violation");
        when(accounts.saveAndFlush(any(Account.class))).thenThrow(failure);

        assertThatThrownBy(() -> service.signUp("Nia", "nia@example.com", "fictitious-nia-pw-1"))
                .isSameAs(failure);
    }
}
