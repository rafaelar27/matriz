package com.matriz.progress;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.matriz.accesscontrol.Actor;
import com.matriz.accounts.SignedInAccount;
import com.matriz.projects.ProjectDirectory;
import com.matriz.shared.ApiException;
import com.matriz.shared.Ids;
import com.matriz.tasks.TaskStatistics;
import com.matriz.workspaces.MemberDirectory;

/**
 * getProjectProgress (openapi.yaml): "N of M tasks done" per project (CMP-09; FR-18, BR-15, EC-05). Derived on every
 * request, never stored. Projects the caller cannot see are left out (NFR-01).
 */
@RestController
class ProgressController {

    private static final int MAX_IDS = 100;

    private final TaskStatistics tasks;
    private final ProjectDirectory projects;
    private final MemberDirectory members;

    ProgressController(TaskStatistics tasks, ProjectDirectory projects, MemberDirectory members) {
        this.tasks = tasks;
        this.projects = projects;
        this.members = members;
    }

    @GetMapping("/progress")
    @Transactional(readOnly = true)
    List<ProjectProgress> progress(@AuthenticationPrincipal SignedInAccount caller,
                                   @RequestParam(name = "project_id", required = false) List<String> projectIds) {
        if (projectIds == null || projectIds.isEmpty() || projectIds.size() > MAX_IDS) {
            throw ApiException.validation("The progress could not be read.", "project_id",
                    "Send 1 to 100 project ids.");
        }
        Set<UUID> visible = new LinkedHashSet<>();
        for (String value : projectIds) {
            UUID id = Ids.parse(value).orElseThrow(() -> ApiException.validation("The progress could not be read.",
                    "project_id", "A project id is not valid."));
            Optional<UUID> workspace = projects.workspaceOf(id);
            boolean canSee = workspace.flatMap(ws -> members.actorIn(caller.accountId(), ws))
                    .map(Actor::active).orElse(false);
            if (canSee) {
                visible.add(id);
            }
        }
        Map<UUID, TaskStatistics.Counts> counts = tasks.countsByProject(visible);
        List<ProjectProgress> result = new ArrayList<>();
        for (UUID id : visible) {
            TaskStatistics.Counts c = counts.get(id);
            result.add(new ProjectProgress(id, c.done(), c.total()));
        }
        return result;
    }

    record ProjectProgress(UUID projectId, long done, long total) {
    }
}
