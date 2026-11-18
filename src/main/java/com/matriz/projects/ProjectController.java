package com.matriz.projects;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.matriz.accounts.SignedInAccount;
import com.matriz.shared.ApiException;
import com.matriz.shared.Ids;
import com.matriz.shared.PageParams;

/** listProjects, createProject, getProject, updateProject, deleteProject (openapi.yaml). */
@RestController
@RequestMapping("/projects")
class ProjectController {

    private final ProjectService service;

    ProjectController(ProjectService service) {
        this.service = service;
    }

    @GetMapping
    ProjectService.ProjectPage list(@AuthenticationPrincipal SignedInAccount caller,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(name = "workspace_id", required = false) String workspaceId,
                                    @RequestParam(required = false) String page,
                                    @RequestParam(required = false) String size) {
        if (q != null && q.trim().length() > ProjectService.NAME_MAX) {
            throw ApiException.validation("The projects could not be listed.", "q",
                    "The search text can be at most 100 characters long.");
        }
        Optional<UUID> workspace = Optional.empty();
        if (workspaceId != null && !workspaceId.isEmpty()) {
            workspace = Optional.of(Ids.parse(workspaceId).orElseThrow(() -> ApiException.validation(
                    "The projects could not be listed.", "workspace_id", "The workspace id is not valid.")));
        }
        int p = PageParams.page(page, "The projects could not be listed.");
        int s = PageParams.size(size, "The projects could not be listed.");
        return service.list(caller.accountId(), q, workspace, p, s);
    }

    @PostMapping
    ResponseEntity<ProjectView> create(@AuthenticationPrincipal SignedInAccount caller,
                                       @RequestBody ProjectFields.Create body) {
        ProjectView created = service.create(caller.accountId(), body);
        return ResponseEntity.created(URI.create("/projects/" + created.projectId())).body(created);
    }

    @GetMapping("/{projectId}")
    ProjectView get(@AuthenticationPrincipal SignedInAccount caller, @PathVariable String projectId) {
        return service.get(caller.accountId(), id(projectId));
    }

    @PatchMapping("/{projectId}")
    ProjectView update(@AuthenticationPrincipal SignedInAccount caller, @PathVariable String projectId,
                       @RequestBody ProjectFields body) {
        return service.update(caller.accountId(), id(projectId), body);
    }

    @DeleteMapping("/{projectId}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal SignedInAccount caller, @PathVariable String projectId) {
        service.delete(caller.accountId(), id(projectId));
        return ResponseEntity.noContent().build();
    }

    private static UUID id(String value) {
        return Ids.parse(value).orElseThrow(() -> ApiException.notFound(ProjectService.NOT_FOUND));
    }
}
