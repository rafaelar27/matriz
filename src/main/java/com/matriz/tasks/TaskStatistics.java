package com.matriz.tasks;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public interface of CMP-05 for Progress Tracking: task counts per project (FR-18, BR-15). */
@Service
public class TaskStatistics {

    private final TaskRepository tasks;

    TaskStatistics(TaskRepository tasks) {
        this.tasks = tasks;
    }

    /** Done and total task counts for each project; projects without tasks get 0 of 0 (EC-05). */
    @Transactional(readOnly = true)
    public Map<UUID, Counts> countsByProject(Collection<UUID> projectIds) {
        Map<UUID, Counts> result = new HashMap<>();
        projectIds.forEach(id -> result.put(id, new Counts(0, 0)));
        if (!projectIds.isEmpty()) {
            for (Object[] row : tasks.countByProject(projectIds)) {
                result.put((UUID) row[0], new Counts(((Number) row[1]).longValue(), ((Number) row[2]).longValue()));
            }
        }
        return result;
    }

    public record Counts(long done, long total) {
    }
}
