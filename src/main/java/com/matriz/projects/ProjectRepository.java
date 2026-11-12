package com.matriz.projects;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface ProjectRepository extends JpaRepository<Project, UUID> {

    @Query(value = "SELECT EXISTS (SELECT 1 FROM project_member WHERE project_id = :projectId AND member_id = :memberId)",
            nativeQuery = true)
    boolean isProjectMember(UUID projectId, UUID memberId);

    @Query(value = "SELECT member_id FROM project_member WHERE project_id = :projectId", nativeQuery = true)
    List<UUID> memberIdsOf(UUID projectId);

    @Modifying
    @Query(value = "INSERT INTO project_member (project_id, member_id) VALUES (:projectId, :memberId) "
            + "ON CONFLICT DO NOTHING", nativeQuery = true)
    void addProjectMember(UUID projectId, UUID memberId);

    /** Projects of the given workspaces whose name contains the (already escaped, lower-case) pattern. */
    @Query("select p from Project p where p.workspaceId in :workspaceIds "
            + "and lower(p.name) like :pattern escape '\\' order by lower(p.name), p.id")
    Page<Project> search(Collection<UUID> workspaceIds, String pattern, Pageable page);
}
