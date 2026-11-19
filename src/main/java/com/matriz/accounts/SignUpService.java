package com.matriz.accounts;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.matriz.shared.ApiError;
import com.matriz.shared.ApiException;

/**
 * Creates a personal account (FR-01, US-22, CMP-01).
 *
 * <p>Rules (decision log 2026-11-18): the name is trimmed and 1 to 80 characters; the email is trimmed, contains
 * "@", is stored in lower case and is unique ignoring case (BR-24, enforced by the database index of migration V5);
 * the password is 12 to 64 characters and at most 72 UTF-8 bytes (the BCrypt limit), is not trimmed, and is stored
 * only as a BCrypt hash (SP-10). A new account has no membership and no role. The id is chosen here, never by the
 * client.
 */
@Service
class SignUpService {

    static final int NAME_MAX = 80;
    /** Email length: the usual upper bound for an address (decided 2026-11-18). */
    static final int EMAIL_MAX = 254;
    static final int PASSWORD_MIN = 12;
    static final int PASSWORD_MAX = 64;
    static final int PASSWORD_MAX_BYTES = 72;

    private final AccountRepository accounts;
    private final PasswordEncoder encoder;

    SignUpService(AccountRepository accounts, PasswordEncoder encoder) {
        this.accounts = accounts;
        this.encoder = encoder;
    }

    /** Trimmed and lower case; the form in which emails are stored and looked up (BR-24). */
    static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    Account signUp(String name, String email, String password) {
        List<ApiError.FieldIssue> issues = new ArrayList<>();
        String cleanName = name == null ? "" : name.trim();
        String cleanEmail = normalizeEmail(email);

        int nameLength = cleanName.codePointCount(0, cleanName.length());
        if (nameLength < 1 || nameLength > NAME_MAX || hasControlCharacter(cleanName)
                || hasFormatCharacter(cleanName)) {
            issues.add(new ApiError.FieldIssue("name",
                    "The name must be 1 to " + NAME_MAX + " characters long, without control or invisible formatting characters."));
        }
        if (!looksLikeEmail(cleanEmail) || hasControlCharacter(cleanEmail)) {
            issues.add(new ApiError.FieldIssue("email", "Enter a valid email address."));
        }
        if (password == null || password.codePointCount(0, password.length()) < PASSWORD_MIN
                || password.codePointCount(0, password.length()) > PASSWORD_MAX) {
            issues.add(new ApiError.FieldIssue("password",
                    "The password must be " + PASSWORD_MIN + " to " + PASSWORD_MAX + " characters long."));
        } else if (password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            issues.add(new ApiError.FieldIssue("password",
                    "The password is too long: at most " + PASSWORD_MAX_BYTES + " bytes."));
        }
        if (!issues.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    new ApiError("validation_failed", "The account could not be created.", issues));
        }

        if (accounts.findByEmailIgnoreCase(cleanEmail).isPresent()) {
            throw emailTaken();
        }
        Account account = new Account(UUID.randomUUID(), cleanName, cleanEmail, encoder.encode(password));
        try {
            return accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            // Two sign-ups with the same email at the same moment: the database index decides (EC-12). Any other
            // data error is not "email taken" (audit S-06).
            if (accounts.findByEmailIgnoreCase(cleanEmail).isPresent()) {
                throw emailTaken();
            }
            throw e;
        }
    }

    /** True if the text has a Unicode format character (bidi override, zero-width, ZWJ), which can spoof a name. */
    static boolean hasFormatCharacter(String text) {
        return text.codePoints().anyMatch(cp -> Character.getType(cp) == Character.FORMAT);
    }

    /** True if the text has a control character (for example U+0000, which PostgreSQL cannot store in text). */
    static boolean hasControlCharacter(String text) {
        return text.codePoints().anyMatch(Character::isISOControl);
    }

    /**
     * Whether a sign-in request can possibly match an account: an email that sign-up would accept, and a password of
     * at most 72 UTF-8 bytes (BCrypt ignores the rest, so a longer text must not match; SP-10).
     */
    static boolean canMatchAnAccount(String email, String password) {
        String clean = normalizeEmail(email);
        return looksLikeEmail(clean) && !hasControlCharacter(clean)
                && password.getBytes(StandardCharsets.UTF_8).length <= PASSWORD_MAX_BYTES;
    }

    /**
     * An address with exactly one "@" and text on both sides, at most {@link #EMAIL_MAX} characters, made only of
     * printable ASCII characters (decided 2026-11-18: ASCII only, so look-alike characters cannot make a second
     * account for one address).
     */
    private static boolean looksLikeEmail(String email) {
        if (email.isEmpty() || email.length() > EMAIL_MAX) {
            return false;
        }
        int at = email.indexOf('@');
        return at > 0 && at == email.lastIndexOf('@') && at < email.length() - 1
                && email.chars().allMatch(c -> c > 0x20 && c < 0x7F);
    }

    private static ApiException emailTaken() {
        String message = "This email address is already registered.";
        return new ApiException(HttpStatus.CONFLICT, new ApiError("email_taken", message,
                List.of(new ApiError.FieldIssue("email", message))));
    }
}
