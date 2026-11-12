package com.matriz.accesscontrol;

import java.util.UUID;

/**
 * The facts Access Control needs about the caller in one workspace, supplied by the asking module
 * (architecture.md, CMP-03: "it receives the facts it needs from the asking module").
 *
 * @param memberId the caller's membership in the workspace of the resource
 * @param role     the caller's role in that workspace
 * @param active   false if the membership is deactivated (SP-04)
 */
public record Actor(UUID memberId, Role role, boolean active) {
}
