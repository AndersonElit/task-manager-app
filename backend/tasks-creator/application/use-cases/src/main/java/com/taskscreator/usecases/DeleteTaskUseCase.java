package com.taskscreator.usecases;

import com.taskscreator.model.exception.TaskNotFoundException;
import com.taskscreator.model.port.TaskRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public class DeleteTaskUseCase {

    private final TaskRepository taskRepository;

    public DeleteTaskUseCase(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public Mono<Void> execute(UUID id) {
        return taskRepository.findById(id)
                .switchIfEmpty(Mono.error(new TaskNotFoundException(id)))
                .flatMap(task -> taskRepository.deleteById(task.getId()));
    }
}
