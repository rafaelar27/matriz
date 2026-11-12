package com.matriz.comments;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface CommentRepository extends JpaRepository<Comment, UUID> {

    /** Oldest first, then by id (DA-07; decided 2026-11-12). */
    List<Comment> findAllByTaskIdOrderByCreatedAtAscIdAsc(UUID taskId);
}
