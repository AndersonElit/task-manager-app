package com.taskscreator.postgres;

import com.taskscreator.model.Task;
import com.taskscreator.model.TaskStatus;
import com.taskscreator.model.TaskStatusHistory;
import com.taskscreator.model.port.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TaskRepositoryAdapter implements TaskRepository {

    private final TaskR2dbcRepository r2dbcRepository;
    private final R2dbcEntityTemplate template;
    private final DatabaseClient databaseClient;

    @Override
    public Mono<Task> save(Task task) {
        return template.insert(toEntity(task)).map(this::toDomain);
    }

    @Override
    public Mono<Task> findById(UUID id) {
        return r2dbcRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Flux<Task> findAll() {
        return r2dbcRepository.findAll().map(this::toDomain);
    }

    @Override
    public Flux<Task> findByStatus(TaskStatus status) {
        return r2dbcRepository.findByStatusId(status.getId()).map(this::toDomain);
    }

    @Override
    public Mono<Task> update(Task task) {
        return template.update(toEntity(task)).map(this::toDomain);
    }

    @Override
    public Mono<Void> deleteById(UUID id) {
        return r2dbcRepository.deleteById(id);
    }

    @Override
    public Flux<TaskStatusHistory> findHistoryByTaskId(UUID taskId) {
        return databaseClient.sql("""
                SELECT ts.id, s.name AS status_name, ts.date
                FROM tasks_status ts
                JOIN status s ON s.id = ts.status_id
                WHERE ts.task_id = :taskId
                ORDER BY ts.date DESC
                """)
                .bind("taskId", taskId)
                .map((row, md) -> new TaskStatusHistory(
                        row.get("id", UUID.class),
                        row.get("status_name", String.class),
                        row.get("date", OffsetDateTime.class)
                ))
                .all();
    }

    private TaskEntity toEntity(Task task) {
        return TaskEntity.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .statusId(task.getStatus().getId())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    private Task toDomain(TaskEntity entity) {
        return Task.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .status(TaskStatus.fromId(entity.getStatusId()))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
