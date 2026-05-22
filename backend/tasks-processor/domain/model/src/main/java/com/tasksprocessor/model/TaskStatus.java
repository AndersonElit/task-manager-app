package com.tasksprocessor.model;

public enum TaskStatus {
    PENDIENTE((short) 1),
    COMPLETADA((short) 2);

    private final short id;

    TaskStatus(short id) {
        this.id = id;
    }

    public short getId() {
        return id;
    }
}
