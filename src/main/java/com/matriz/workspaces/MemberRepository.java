package com.matriz.workspaces;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface MemberRepository extends JpaRepository<Member, UUID> {

    @Query("select m from Member m where m.accountId = :accountId and m.workspace.id = :workspaceId")
    Optional<Member> findMembership(UUID accountId, UUID workspaceId);

    @Query("select m from Member m join fetch m.workspace where m.accountId = :accountId order by m.workspace.name")
    List<Member> findAllByAccount(UUID accountId);
}
