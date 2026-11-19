package com.matriz.accesscontrol;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Role lookup by API label as pure logic (INC-17, INC-18; US-03, US-04; FR-04, BR-03). No Spring context and no
 * database. Expected: exactly the four labels of the openapi Role enum, spelled and cased as there; anything else,
 * including codes, other letter case, translations, spaces around and null, is not a role.
 */
class RoleLabelTest {

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("AC-INC17.3, AC-INC18.2, FR-04 · each of the four labels finds its role")
    void everyLabelFindsItsRole(Role role) {
        assertThat(Role.fromLabel(role.label())).contains(role);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "viewer", "VIEWER", "team member", "Team  Member", "TEAM_MEMBER", "Team Member ",
        " Viewer", "WORKSPACE_ADMINISTRATOR", "Workspace administrator", "Administrador", "Membro da equipe", "Owner"})
    @DisplayName("AC-INC17.3, AC-INC18.2, FR-04 · other letter case, codes, translated labels, extra spaces, empty and null are not roles")
    void everythingElseIsNoRole(String label) {
        assertThat(Role.fromLabel(label)).isEmpty();
    }
}
