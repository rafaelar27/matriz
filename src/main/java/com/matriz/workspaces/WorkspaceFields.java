package com.matriz.workspaces;

import java.util.HashSet;
import java.util.Set;

/**
 * Body of createWorkspace (FR-02). Only {@code name} is accepted; any other field, including identity or role fields,
 * is refused with 422 (openapi.yaml, additionalProperties false). Records whether the name was sent.
 */
class WorkspaceFields {

    private final Set<String> present = new HashSet<>();
    private String name;

    boolean has(String field) {
        return present.contains(field);
    }

    String name() {
        return name;
    }

    public void setName(String name) {
        present.add("name");
        this.name = name;
    }
}
