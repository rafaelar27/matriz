package com.matriz.projects;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ProjectRepository extends JpaRepository<Project, UUID> {

    @Query(value = "SELECT EXISTS (SELECT 1 FROM project_member WHERE project_id = :projectId AND member_id = :memberId)",
            nativeQuery = true)
    boolean isProjectMember(UUID projectId, UUID memberId);
}
