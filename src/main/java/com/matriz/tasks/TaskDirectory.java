package com.matriz.tasks;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public interface of CMP-05 for other modules (CMP-07 Comments): which project a task belongs to. */
@Service
public class TaskDirectory {

    private final TaskRepository tasks;

    TaskDirectory(TaskRepository tasks) {
        this.tasks = tasks;
    }

    /** Whether the task exists and belongs to this project, so a task is only reachable through its own project. */
    @Transactional(readOnly = true)
    public boolean belongsTo(UUID taskId, UUID projectId) {
        return tasks.findById(taskId).map(t -> t.getProjectId().equals(projectId)).orElse(false);
    }
}
