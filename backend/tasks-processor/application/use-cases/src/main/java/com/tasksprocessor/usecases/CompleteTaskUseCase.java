package com.tasksprocessor.usecases;

import com.tasksprocessor.model.TaskStatus;
import com.tasksprocessor.model.port.TaskRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public class CompleteTaskUseCase {

    private final TaskRepository taskRepository;

    public CompleteTaskUseCase(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public Mono<Void> execute(UUID taskId) {
        return taskRepository.updateStatus(taskId, TaskStatus.COMPLETADA);
    }
}
