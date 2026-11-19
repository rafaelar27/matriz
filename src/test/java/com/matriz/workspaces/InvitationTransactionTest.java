package com.matriz.workspaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.matriz.email.InvitationMailer;
import com.matriz.email.InvitationNotSentException;
import com.matriz.shared.ApiException;

/**
 * Invitation rules that need real commits and rollbacks (INC-17; US-03; FR-03, EC-02, BR-05; AC-INC17.8, AC-INC17.13,
 * AC-INC17.14; decisions QJ, QK). Not {@code @Transactional}: the service's own transaction must commit or roll back
 * by itself, and each simultaneous call needs its own transaction, which a rolled-back test transaction would hide.
 * The test creates its own fictitious accounts and workspace with random ids and removes them after each test. The
 * mail sender is a mock, so nothing is sent.
 */
@SpringBootTest
@ActiveProfiles("test")
class InvitationTransactionTest {

    @Autowired
    InvitationService service;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    InvitationMailer mailer;

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final UUID adminAccount = UUID.randomUUID();
    private final UUID inviteeAccount = UUID.randomUUID();
    private final UUID workspace = UUID.randomUUID();
    private final UUID adminMember = UUID.randomUUID();
    private final String inviteeEmail = "tx.invitee." + suffix + "@example.com";

    @BeforeEach
    void createFictitiousData() {
        jdbc.update("INSERT INTO account (account_id, name, email, password_hash) VALUES (?, 'Tx Admin', ?, 'not-a-real-hash')",
                adminAccount, "tx.admin." + suffix + "@example.com");
        jdbc.update("INSERT INTO account (account_id, name, email, password_hash) VALUES (?, 'Tx Invitee', ?, 'not-a-real-hash')",
                inviteeAccount, inviteeEmail);
        jdbc.update("INSERT INTO workspace (workspace_id, name) VALUES (?, ?)", workspace, "Tx Space " + suffix);
        jdbc.update("INSERT INTO member (member_id, workspace_id, account_id, role, status) "
                + "VALUES (?, ?, ?, 'WORKSPACE_ADMINISTRATOR', 'ACTIVE')", adminMember, workspace, adminAccount);
    }

    @AfterEach
    void removeFictitiousData() {
        jdbc.update("DELETE FROM invitation WHERE workspace_id = ?", workspace);
        jdbc.update("DELETE FROM member WHERE workspace_id = ?", workspace);
        jdbc.update("DELETE FROM workspace WHERE workspace_id = ?", workspace);
        jdbc.update("DELETE FROM account WHERE account_id IN (?, ?)", adminAccount, inviteeAccount);
    }

    private InvitationFields fields(String email, String role) {
        InvitationFields body = new InvitationFields();
        body.setEmail(email);
        body.setRole(role);
        return body;
    }

    private long invitations() {
        return jdbc.queryForObject("SELECT count(*) FROM invitation WHERE workspace_id = ?", Long.class, workspace);
    }

    private long membersOfInvitee() {
        return jdbc.queryForObject("SELECT count(*) FROM member WHERE workspace_id = ? AND account_id = ?",
                Long.class, workspace, inviteeAccount);
    }

    private UUID invite(String role) {
        return service.create(adminAccount, workspace, fields(inviteeEmail, role)).invitationId();
    }

    @Test
    @DisplayName("AC-INC17.8, QJ, FR-03 · if the email cannot be sent, createInvitation answers 503 email_not_sent and no invitation row is kept; a later successful send is kept")
    void emailFailureKeepsNothing() {
        doThrow(InvitationNotSentException.class).when(mailer).send(any());

        assertThatThrownBy(() -> invite("Viewer")).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(e.error().code()).isEqualTo("email_not_sent");
        });
        assertThat(invitations()).isZero();

        org.mockito.Mockito.reset(mailer); // the next send works
        invite("Viewer");
        assertThat(invitations()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-INC17.13, EC-02, BR-05, QK · the same invitation accepted by two simultaneous requests creates one member: one 200, the other 404")
    void simultaneousAcceptsOfOneInvitationCreateOneMember() throws Exception {
        UUID invitation = invite("Team Member");

        List<Object> results = runTogether(() -> service.accept(inviteeAccount, inviteeEmail, invitation),
                () -> service.accept(inviteeAccount, inviteeEmail, invitation));

        assertThat(membersOfInvitee()).isEqualTo(1);
        assertThat(results.stream().filter(r -> !(r instanceof Throwable))).hasSize(1);
        assertThat(results.stream().filter(r -> r instanceof Throwable)).allSatisfy(t ->
                assertThat(t).isInstanceOfSatisfying(ApiException.class, e ->
                        assertThat(e.status()).isIn(HttpStatus.NOT_FOUND, HttpStatus.CONFLICT)));
        assertThat(jdbc.queryForObject("SELECT status FROM invitation WHERE invitation_id = ?", String.class,
                invitation)).isEqualTo("ACCEPTED");
    }

    @Test
    @DisplayName("AC-INC17.13, EC-02, BR-05, QK · two different pending invitations to one address accepted at the same moment create at most one member, and neither request fails with a server error")
    void simultaneousAcceptsOfTwoInvitationsCreateOneMember() throws Exception {
        UUID first = invite("Team Member");
        UUID second = invite("Viewer");

        List<Object> results = runTogether(() -> service.accept(inviteeAccount, inviteeEmail, first),
                () -> service.accept(inviteeAccount, inviteeEmail, second));

        assertThat(membersOfInvitee()).isEqualTo(1);
        assertThat(results.stream().filter(r -> !(r instanceof Throwable)))
                .as("at least one accept succeeds").isNotEmpty();
        assertThat(results.stream().filter(r -> r instanceof Throwable)).allSatisfy(t ->
                assertThat(t).as("a refusal must be a documented 404 or 409, not %s", t)
                        .isInstanceOfSatisfying(ApiException.class, e ->
                                assertThat(e.status()).isIn(HttpStatus.NOT_FOUND, HttpStatus.CONFLICT)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM invitation WHERE workspace_id = ? AND status = 'PENDING'",
                Long.class, workspace)).as("no pending invitation is left to a member").isZero();
    }

    /** Runs the calls at the same moment, each in its own thread, and returns each result or the thrown error. */
    private static List<Object> runTogether(Supplier<?>... calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.length);
        CountDownLatch ready = new CountDownLatch(calls.length);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        try {
            for (Supplier<?> call : calls) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await(10, TimeUnit.SECONDS);
                    try {
                        return call.get();
                    } catch (Throwable t) {
                        return t;
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> f : futures) {
                results.add(f.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
