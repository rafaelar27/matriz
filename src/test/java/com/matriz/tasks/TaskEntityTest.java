package com.matriz.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Task entity as pure logic (FR-12, BR-11, BR-12, AC-10.1, AC-11.1; US-10, US-11). No Spring context, no database.
 * Moving back from Done is undecided (PD-06) and not tested.
 */
class TaskEntityTest {

    private static final UUID PROJECT = UUID.fromString("a0000000-0000-0000-0000-0000000000c1");
    private static final UUID CREATOR = UUID.fromString("a0000000-0000-0000-0000-0000000000a1");

    @Test
    @DisplayName("FR-12, BR-11, AC-10.1 · a new task starts as To do")
    void newTaskStartsAsToDo() {
        assertThat(new Task(PROJECT, CREATOR).getStatus()).isEqualTo(TaskStatus.TO_DO);
    }

    @Test
    @DisplayName("BR-12, AC-10.1 · a new task records its creator and project")
    void newTaskRecordsCreator() {
        Task t = new Task(PROJECT, CREATOR);
        assertThat(t.getCreatedByMemberId()).isEqualTo(CREATOR);
        assertThat(t.getProjectId()).isEqualTo(PROJECT);
    }

    @Test
    @DisplayName("AC-11.1 · status flows To do -> In progress -> Done")
    void statusFlow() {
        Task t = new Task(PROJECT, CREATOR);
        t.changeStatus(TaskStatus.IN_PROGRESS);
        assertThat(t.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        t.changeStatus(TaskStatus.DONE);
        assertThat(t.getStatus()).isEqualTo(TaskStatus.DONE);
    }
}
