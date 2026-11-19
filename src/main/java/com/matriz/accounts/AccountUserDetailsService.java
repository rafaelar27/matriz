package com.matriz.accounts;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads an account by email for password checking during sign-in (CMP-01). */
@Service
class AccountUserDetailsService implements UserDetailsService {

    private final AccountRepository accounts;

    AccountUserDetailsService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return accounts.findByEmailIgnoreCase(SignUpService.normalizeEmail(email))
                .map(SignedInAccount::new)
                .orElseThrow(() -> new UsernameNotFoundException("No account"));
    }
}
