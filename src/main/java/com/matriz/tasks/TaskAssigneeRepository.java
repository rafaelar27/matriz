package com.matriz.tasks;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface TaskAssigneeRepository extends JpaRepository<TaskAssignee, TaskAssignee.Key> {

    List<TaskAssignee> findAllByTaskId(UUID taskId);

    /** Plain insert: a second row for the same task and member breaks the primary key (BR-22). */
    @Modifying
    @Query(value = "INSERT INTO task_assignee (task_id, member_id) VALUES (:taskId, :memberId)", nativeQuery = true)
    void insert(UUID taskId, UUID memberId);

    @Modifying
    @Query("delete from TaskAssignee a where a.taskId = :taskId and a.memberId = :memberId")
    int remove(UUID taskId, UUID memberId);
}
