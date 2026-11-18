package com.matriz.accesscontrol;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Roles as pure logic (INC-02 access rules; US-04 area). No Spring context and no database.
 * Expected: exactly the four roles of the openapi Role enum, in that order (FR-04, BR-03).
 */
class RoleTest {

    @Test
    @DisplayName("FR-04, BR-03 · exactly four roles with the openapi labels, in order")
    void fourRolesWithDocumentedLabels() {
        assertThat(Arrays.stream(Role.values()).map(Role::label)).containsExactly(
                "Workspace Administrator", "Project Manager", "Team Member", "Viewer");
    }
}
