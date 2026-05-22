package com.taskscreator.usecases;

import com.taskscreator.model.Task;
import com.taskscreator.model.TaskStatus;
import com.taskscreator.model.event.TaskCreatedEvent;
import com.taskscreator.model.port.EventPublisher;
import com.taskscreator.model.port.TaskRepository;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

public class CreateTaskUseCase {

    private final TaskRepository taskRepository;
    private final EventPublisher eventPublisher;

    public CreateTaskUseCase(TaskRepository taskRepository, EventPublisher eventPublisher) {
        this.taskRepository = taskRepository;
        this.eventPublisher = eventPublisher;
    }

    public Mono<Task> execute(String title, String description) {
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
}
