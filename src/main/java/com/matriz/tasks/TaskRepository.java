package com.matriz.tasks;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface TaskRepository extends JpaRepository<Task, UUID> {

    /** Rows of [project id, number of Done tasks, number of tasks]. */
    @Query("select t.projectId, sum(case when t.status = com.matriz.tasks.TaskStatus.DONE then 1 else 0 end), count(t) "
            + "from Task t where t.projectId in :projectIds group by t.projectId")
    List<Object[]> countByProject(Collection<UUID> projectIds);

    /**
     * listTasks (FR-13, FR-15): one project's tasks with the given statuses and, unless {@code anyOwner}, the given
     * owner. Earliest due date first, tasks without a due date last, then by title ignoring case, then by id
     * (openapi.yaml, decided 2026-11-12).
     */
    @Query(value = "select t from Task t where t.projectId = :projectId and t.status in :statuses "
            + "and (:anyOwner = true or t.ownerMemberId = :owner) "
            + "order by case when t.dueDate is null then 1 else 0 end, t.dueDate, lower(t.title), t.id",
            countQuery = "select count(t) from Task t where t.projectId = :projectId and t.status in :statuses "
                    + "and (:anyOwner = true or t.ownerMemberId = :owner)")
    Page<Task> search(UUID projectId, Collection<TaskStatus> statuses, boolean anyOwner, UUID owner,
                      Pageable pageable);

    /** Rows of [status, number of tasks] for one project (status_counts). */
    @Query("select t.status, count(t) from Task t where t.projectId = :projectId group by t.status")
    List<Object[]> countByStatus(UUID projectId);
}
