package com.matriz.workspaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Rollback of createWorkspace (INC-14; US-02; FR-02, SP-07, NFR-03; AC-INC14.2). Not {@code @Transactional}: the
 * service's own transaction must commit or roll back by itself, which a rolled-back test transaction would hide.
 * The member repository is replaced by a mock that fails, so nothing is ever committed and no cleanup is needed.
 */
@SpringBootTest
@ActiveProfiles("test")
class WorkspaceCreateRollbackTest {

    @Autowired
    WorkspaceService service;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    MemberRepository members;

    @Test
    @DisplayName("AC-INC14.2, SP-07, NFR-03 · if saving the first member fails, no workspace row remains (same transaction)")
    void failedMemberSaveLeavesNoWorkspace() {
        String name = "Rollback probe " + UUID.randomUUID();
        when(members.save(any())).thenThrow(new IllegalStateException("simulated failure"));
        WorkspaceFields body = new WorkspaceFields();
        body.setName(name);

        assertThatThrownBy(() -> service.create(UUID.randomUUID(), body))
                .isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM workspace WHERE name = ?", Long.class, name))
                .isZero();
    }
}
