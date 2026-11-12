package com.matriz.tasks;

import java.util.Arrays;
import java.util.Optional;

/** The three task statuses (FR-12, BR-11). Stored as the enum name; shown in the API as the label. */
enum TaskStatus {
    TO_DO("To do"),
    IN_PROGRESS("In progress"),
    DONE("Done");

    private final String label;

    TaskStatus(String label) {
        this.label = label;
    }

    String label() {
        return label;
    }

    /** Exact match on the API label only (AC-11.2). */
    static Optional<TaskStatus> fromLabel(String label) {
        return Arrays.stream(values()).filter(s -> s.label.equals(label)).findFirst();
    }
}
