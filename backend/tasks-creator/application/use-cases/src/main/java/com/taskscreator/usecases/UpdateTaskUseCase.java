package com.taskscreator.usecases;

import com.taskscreator.model.Task;
import com.taskscreator.model.exception.TaskNotFoundException;
import com.taskscreator.model.port.TaskRepository;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

public class UpdateTaskUseCase {

    private final TaskRepository taskRepository;

    public UpdateTaskUseCase(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public Mono<Task> execute(UUID id, String title, String description) {
        return taskRepository.findById(id)
                .switchIfEmpty(Mono.error(new TaskNotFoundException(id)))
                .flatMap(existing -> taskRepository.update(
                        Task.builder()
                                .id(existing.getId())
                                .title(title.trim())
                                .description(description)
                                .status(existing.getStatus())
                                .createdAt(existing.getCreatedAt())
                                .updatedAt(OffsetDateTime.now())
                                .build()
                ));
    }
}
