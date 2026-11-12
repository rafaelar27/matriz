package com.matriz.tasks;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface TaskRepository extends JpaRepository<Task, UUID> {

    /** Rows of [project id, number of Done tasks, number of tasks]. */
    @Query("select t.projectId, sum(case when t.status = com.matriz.tasks.TaskStatus.DONE then 1 else 0 end), count(t) "
            + "from Task t where t.projectId in :projectIds group by t.projectId")
    List<Object[]> countByProject(Collection<UUID> projectIds);
}
