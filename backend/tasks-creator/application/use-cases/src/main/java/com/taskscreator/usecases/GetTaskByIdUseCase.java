package com.taskscreator.usecases;

import com.taskscreator.model.Task;
import com.taskscreator.model.exception.TaskNotFoundException;
import com.taskscreator.model.port.TaskRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public class GetTaskByIdUseCase {

    private final TaskRepository taskRepository;

    public GetTaskByIdUseCase(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public Mono<Task> execute(UUID id) {
        return taskRepository.findById(id)
                .switchIfEmpty(Mono.error(new TaskNotFoundException(id)));
    }
}
