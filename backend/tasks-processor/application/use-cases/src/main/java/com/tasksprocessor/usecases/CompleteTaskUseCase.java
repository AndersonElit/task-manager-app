package com.tasksprocessor.usecases;

import com.tasksprocessor.model.TaskStatus;
import com.tasksprocessor.model.port.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

public class CompleteTaskUseCase {

    private static final Logger log = LoggerFactory.getLogger(CompleteTaskUseCase.class);

    private final TaskRepository taskRepository;

    public CompleteTaskUseCase(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public Mono<Void> execute(UUID taskId) {
        log.info("[CompleteTaskUseCase] Completando tarea: taskId={}", taskId);
        return taskRepository.updateStatus(taskId, TaskStatus.COMPLETADA)
                .doOnSuccess(v -> log.info("[CompleteTaskUseCase] Tarea completada: taskId={}", taskId))
                .doOnError(ex -> log.error("[CompleteTaskUseCase] Error al completar taskId={}: {}", taskId, ex.getMessage()));
    }
}
