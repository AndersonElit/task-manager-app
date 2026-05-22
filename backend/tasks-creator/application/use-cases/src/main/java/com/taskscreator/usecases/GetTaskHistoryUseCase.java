package com.taskscreator.usecases;

import com.taskscreator.model.TaskStatusHistory;
import com.taskscreator.model.exception.TaskNotFoundException;
import com.taskscreator.model.port.TaskRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public class GetTaskHistoryUseCase {

    private final TaskRepository taskRepository;

    public GetTaskHistoryUseCase(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public Flux<TaskStatusHistory> execute(UUID taskId) {
        return taskRepository.findById(taskId)
                .switchIfEmpty(reactor.core.publisher.Mono.error(new TaskNotFoundException(taskId)))
                .flatMapMany(task -> taskRepository.findHistoryByTaskId(taskId));
    }
}
