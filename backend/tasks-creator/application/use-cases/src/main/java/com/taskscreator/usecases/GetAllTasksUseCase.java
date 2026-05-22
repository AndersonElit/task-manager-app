package com.taskscreator.usecases;

import com.taskscreator.model.Task;
import com.taskscreator.model.TaskStatus;
import com.taskscreator.model.port.TaskRepository;
import reactor.core.publisher.Flux;

public class GetAllTasksUseCase {

    private final TaskRepository taskRepository;

    public GetAllTasksUseCase(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public Flux<Task> execute(TaskStatus status) {
        if (status == null) {
            return taskRepository.findAll();
        }
        return taskRepository.findByStatus(status);
    }
}
