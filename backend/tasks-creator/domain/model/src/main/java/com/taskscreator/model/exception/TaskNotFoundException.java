package com.taskscreator.model.exception;

import java.util.UUID;

public class TaskNotFoundException extends RuntimeException {
    public TaskNotFoundException(UUID id) {
        super("Tarea no encontrada: " + id);
    }
}
