package com.taskscreator.model;

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

    public static TaskStatus fromId(short id) {
        for (TaskStatus s : values()) {
            if (s.id == id) return s;
        }
        throw new IllegalArgumentException("Estado desconocido con id: " + id);
    }

    public static TaskStatus fromName(String name) {
        for (TaskStatus s : values()) {
            if (s.name().equalsIgnoreCase(name)) return s;
        }
        throw new IllegalArgumentException("Estado inválido: '" + name + "'. Valores permitidos: pendiente, completada");
    }
}
