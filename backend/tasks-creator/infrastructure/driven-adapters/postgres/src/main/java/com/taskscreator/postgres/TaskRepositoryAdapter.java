package com.taskscreator.postgres;

import com.taskscreator.model.Task;
import com.taskscreator.model.TaskStatus;
import com.taskscreator.model.TaskStatusHistory;
import com.taskscreator.model.port.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskRepositoryAdapter implements TaskRepository {

    private final TaskR2dbcRepository r2dbcRepository;
    private final R2dbcEntityTemplate template;
    private final DatabaseClient databaseClient;

    @Override
    public Mono<Task> save(Task task) {
        log.debug("[DB] Guardando tarea: id={}", task.getId());
        return template.insert(toEntity(task))
                .map(this::toDomain)
                .doOnNext(saved -> log.debug("[DB] Tarea insertada: id={}", saved.getId()));
    }

    @Override
    public Mono<Task> findById(UUID id) {
        log.debug("[DB] Buscando tarea: id={}", id);
        return r2dbcRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Flux<Task> findAll() {
        log.debug("[DB] Consultando todas las tareas");
        return r2dbcRepository.findAll().map(this::toDomain);
    }

    @Override
    public Flux<Task> findByStatus(TaskStatus status) {
        log.debug("[DB] Consultando tareas por status={}", status);
        return r2dbcRepository.findByStatusId(status.getId()).map(this::toDomain);
    }

    @Override
    public Mono<Task> update(Task task) {
        log.debug("[DB] Actualizando tarea: id={}", task.getId());
        return template.update(toEntity(task))
                .map(this::toDomain)
                .doOnNext(updated -> log.debug("[DB] Tarea actualizada en DB: id={}", updated.getId()));
    }

    @Override
    public Mono<Void> deleteById(UUID id) {
        log.debug("[DB] Eliminando tarea: id={}", id);
        return r2dbcRepository.deleteById(id)
                .doOnSuccess(v -> log.debug("[DB] Tarea eliminada de DB: id={}", id));
    }

    @Override
    public Flux<TaskStatusHistory> findHistoryByTaskId(UUID taskId) {
        log.debug("[DB] Consultando historial: taskId={}", taskId);
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
