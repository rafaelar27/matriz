package com.matriz.accounts;

import java.util.List;
import java.util.UUID;

/**
 * Memberships shown in the signed-in profile. Defined here and implemented by CMP-02 Workspaces and Members, so that
 * Accounts does not depend on Workspaces (architecture decision rule 3: no dependency cycles).
 */
public interface MembershipProvider {

    /** The account's active memberships. Deactivated memberships give no access (SP-04). */
    List<Membership> activeMembershipsOf(UUID accountId);

    /** A membership as shown in the signed-in profile. */
    record Membership(UUID memberId, UUID workspaceId, String workspaceName, String role) {
    }
}
