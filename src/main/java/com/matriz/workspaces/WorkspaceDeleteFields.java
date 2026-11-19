package com.matriz.workspaces;

/** Body of deleteWorkspace (FR-24). Only {@code confirm_name} is accepted (decision QN). */
class WorkspaceDeleteFields {

    private String confirmName;

    String confirmName() {
        return confirmName;
    }

    public void setConfirmName(String confirmName) {
        this.confirmName = confirmName;
    }
}
