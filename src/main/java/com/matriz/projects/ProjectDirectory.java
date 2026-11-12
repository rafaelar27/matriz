package com.matriz.projects;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public interface of CMP-04 for other modules: which workspace a project is in, and who takes part in it. */
@Service
public class ProjectDirectory {

    private final ProjectRepository projects;

    ProjectDirectory(ProjectRepository projects) {
        this.projects = projects;
    }

    /** The project's workspace, or empty if the project does not exist. */
    @Transactional(readOnly = true)
    public Optional<UUID> workspaceOf(UUID projectId) {
        return projects.findById(projectId).map(Project::getWorkspaceId);
    }

    /**
     * Whether the member takes part in the project (FR-07). A member of another workspace is never a member of
     * this project (BR-08), so this also enforces workspace isolation for owners (NFR-01, BR-18).
     */
    @Transactional(readOnly = true)
    public boolean isProjectMember(UUID projectId, UUID memberId) {
        return projects.isProjectMember(projectId, memberId);
    }

    /** The member ids of everyone who takes part in the project (FR-07), active or not. */
    @Transactional(readOnly = true)
    public Set<UUID> memberIdsOf(UUID projectId) {
        return new HashSet<>(projects.memberIdsOf(projectId));
    }
}
