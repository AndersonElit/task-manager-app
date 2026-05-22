package com.taskscreator.usecases;

import com.taskscreator.model.Task;
import com.taskscreator.model.TaskStatus;
import com.taskscreator.model.TaskStatusHistory;
import com.taskscreator.model.event.TaskCreatedEvent;
import com.taskscreator.model.exception.TaskNotFoundException;
import com.taskscreator.model.port.EventPublisher;
import com.taskscreator.model.port.TaskRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

public class TaskUseCase {

    private final TaskRepository taskRepository;
    private final EventPublisher eventPublisher;

    public TaskUseCase(TaskRepository taskRepository, EventPublisher eventPublisher) {
        this.taskRepository = taskRepository;
        this.eventPublisher = eventPublisher;
    }

    public Mono<Task> create(String title, String description) {
        Task task = Task.builder()
                .id(UUID.randomUUID())
                .title(title.trim())
                .description(description)
                .status(TaskStatus.PENDIENTE)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        return taskRepository.save(task)
                .flatMap(saved -> eventPublisher
                        .publish(new TaskCreatedEvent(
                                saved.getId(),
                                saved.getTitle(),
                                saved.getDescription(),
                                saved.getCreatedAt()
                        ))
                        .thenReturn(saved));
    }

    public Flux<Task> findAll(TaskStatus status) {
        if (status == null) {
            return taskRepository.findAll();
        }
        return taskRepository.findByStatus(status);
    }

    public Mono<Task> findById(UUID id) {
        return taskRepository.findById(id)
                .switchIfEmpty(Mono.error(new TaskNotFoundException(id)));
    }

    public Mono<Task> update(UUID id, String title, String description) {
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

    public Mono<Void> delete(UUID id) {
        return taskRepository.findById(id)
                .switchIfEmpty(Mono.error(new TaskNotFoundException(id)))
                .flatMap(task -> taskRepository.deleteById(task.getId()));
    }

    public Flux<TaskStatusHistory> findHistory(UUID taskId) {
        return taskRepository.findById(taskId)
                .switchIfEmpty(Mono.error(new TaskNotFoundException(taskId)))
                .flatMapMany(task -> taskRepository.findHistoryByTaskId(taskId));
    }
}
