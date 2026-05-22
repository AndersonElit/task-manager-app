package com.taskscreator.model.port;

import com.taskscreator.model.Task;
import com.taskscreator.model.TaskStatus;
import com.taskscreator.model.TaskStatusHistory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface TaskRepository {
    Mono<Task> save(Task task);
    Mono<Task> findById(UUID id);
    Flux<Task> findAll();
    Flux<Task> findByStatus(TaskStatus status);
    Mono<Task> update(Task task);
    Mono<Void> deleteById(UUID id);
    Flux<TaskStatusHistory> findHistoryByTaskId(UUID taskId);
}
