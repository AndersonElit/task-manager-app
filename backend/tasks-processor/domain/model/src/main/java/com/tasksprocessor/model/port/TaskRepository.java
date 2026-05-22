package com.tasksprocessor.model.port;

import com.tasksprocessor.model.TaskStatus;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface TaskRepository {
    Mono<Void> updateStatus(UUID taskId, TaskStatus status);
}
