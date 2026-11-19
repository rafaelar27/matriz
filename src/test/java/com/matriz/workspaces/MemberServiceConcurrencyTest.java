package com.matriz.workspaces;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

import com.matriz.shared.ApiException;

/**
 * Two administrators changing each other at the same moment (INC-18; US-04, US-05; FR-04, SP-07, BR-04, EC-03;
 * decision QG; AC-INC18.3). Not {@code @Transactional}: each simultaneous call needs its own committed transaction.
 * Expected: the workspace is never left without an active Workspace Administrator; the call that would remove the
 * last one is refused 409, never a server error. The test creates its own fictitious accounts and workspace with
 * random ids and removes them after each test.
 */
@SpringBootTest
@ActiveProfiles("test")
class MemberServiceConcurrencyTest {

    @Autowired
    MemberService service;

    @Autowired
    JdbcTemplate jdbc;

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final UUID workspace = UUID.randomUUID();
    private final UUID accountA = UUID.randomUUID();
    private final UUID accountB = UUID.randomUUID();
    private final UUID memberA = UUID.randomUUID();
    private final UUID memberB = UUID.randomUUID();

    @BeforeEach
    void createFictitiousData() {
        jdbc.update("INSERT INTO workspace (workspace_id, name) VALUES (?, ?)", workspace, "Race Space " + suffix);
        for (UUID[] row : new UUID[][] {{accountA, memberA}, {accountB, memberB}}) {
            jdbc.update("INSERT INTO account (account_id, name, email, password_hash) VALUES (?, 'Race Admin', ?, 'not-a-real-hash')",
                    row[0], "race." + row[0] + "@example.com");
            jdbc.update("INSERT INTO member (member_id, workspace_id, account_id, role, status) "
                    + "VALUES (?, ?, ?, 'WORKSPACE_ADMINISTRATOR', 'ACTIVE')", row[1], workspace, row[0]);
        }
    }

    @AfterEach
    void removeFictitiousData() {
        jdbc.update("DELETE FROM member WHERE workspace_id = ?", workspace);
        jdbc.update("DELETE FROM workspace WHERE workspace_id = ?", workspace);
        jdbc.update("DELETE FROM account WHERE account_id IN (?, ?)", accountA, accountB);
    }

    private long activeAdministrators() {
        return jdbc.queryForObject("SELECT count(*) FROM member WHERE workspace_id = ? "
                + "AND role = 'WORKSPACE_ADMINISTRATOR' AND status = 'ACTIVE'", Long.class, workspace);
    }

    private static MemberRoleFields viewer() {
        MemberRoleFields body = new MemberRoleFields();
        body.setRole("Viewer");
        return body;
    }

    private static void assertOnlyDocumentedRefusals(List<Object> results) {
        assertThat(results.stream().filter(r -> r instanceof Throwable)).allSatisfy(t ->
                assertThat(t).as("a refusal must be a documented 403, 404 or 409, not %s", t)
                        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status())
                                .isIn(HttpStatus.CONFLICT, HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND)));
    }

    @Test
    @DisplayName("AC-INC18.3, QG, SP-07, BR-04, EC-03 · two administrators demoting each other at the same moment leave one active administrator")
    void simultaneousDemotionsKeepOneAdministrator() throws Exception {
        List<Object> results = runTogether(
                () -> service.changeRole(accountA, workspace, memberB, viewer()),
                () -> service.changeRole(accountB, workspace, memberA, viewer()));

        assertThat(activeAdministrators()).isEqualTo(1);
        assertThat(results.stream().filter(r -> !(r instanceof Throwable))).hasSize(1);
        assertOnlyDocumentedRefusals(results);
    }

    @Test
    @DisplayName("AC-INC18.3, QG, SP-07, BR-04, EC-03 · two administrators removing each other at the same moment leave one active administrator")
    void simultaneousRemovalsKeepOneAdministrator() throws Exception {
        List<Object> results = runTogether(
                () -> { service.remove(accountA, workspace, memberB, Optional.empty()); return "removed"; },
                () -> { service.remove(accountB, workspace, memberA, Optional.empty()); return "removed"; });

        assertThat(activeAdministrators()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member WHERE workspace_id = ?", Long.class, workspace))
                .isEqualTo(1);
        assertThat(results.stream().filter(r -> !(r instanceof Throwable))).hasSize(1);
        assertOnlyDocumentedRefusals(results);
    }

    @Test
    @DisplayName("AC-INC18.3, AC-INC18.6, R-04, SP-03 · an administrator demoted (committed) before the call gets 403 on changing a role and removing a member; nothing changes")
    void demotedAdministratorIsRefusedAndNothingChanges() {
        jdbc.update("UPDATE member SET role = 'VIEWER' WHERE member_id = ?", memberB);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.changeRole(accountB, workspace, memberA, viewer()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.remove(accountB, workspace, memberA,
                Optional.empty()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN));

        assertThat(jdbc.queryForObject("SELECT role FROM member WHERE member_id = ?", String.class, memberA))
                .isEqualTo("WORKSPACE_ADMINISTRATOR");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member WHERE workspace_id = ?", Long.class, workspace))
                .isEqualTo(2);
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
