package com.taskscreator.usecases;

import com.taskscreator.model.Task;
import com.taskscreator.model.TaskStatus;
import com.taskscreator.model.TaskStatusHistory;
import com.taskscreator.model.event.TaskCreatedEvent;
import com.taskscreator.model.exception.TaskNotFoundException;
import com.taskscreator.model.port.EventPublisher;
import com.taskscreator.model.port.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

public class TaskUseCase {

    private static final Logger log = LoggerFactory.getLogger(TaskUseCase.class);

    private final TaskRepository taskRepository;
    private final EventPublisher eventPublisher;

    public TaskUseCase(TaskRepository taskRepository, EventPublisher eventPublisher) {
        this.taskRepository = taskRepository;
        this.eventPublisher = eventPublisher;
    }

    public Mono<Task> create(String title, String description) {
        log.info("[create] Iniciando creación: title='{}'", title.trim());
        Task task = Task.builder()
                .id(UUID.randomUUID())
                .title(title.trim())
                .description(description)
                .status(TaskStatus.PENDIENTE)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        return taskRepository.save(task)
                .doOnNext(saved -> log.info("[create] Tarea guardada en DB: id={}", saved.getId()))
                .flatMap(saved -> eventPublisher
                        .publish(new TaskCreatedEvent(
                                saved.getId(),
                                saved.getTitle(),
                                saved.getDescription(),
                                saved.getCreatedAt()
                        ))
                        .doOnSuccess(v -> log.info("[create] Evento publicado: taskId={}", saved.getId()))
                        .thenReturn(saved))
                .doOnError(ex -> log.error("[create] Error al crear tarea: {}", ex.getMessage()));
    }

    public Flux<Task> findAll(TaskStatus status) {
        if (status == null) {
            log.info("[findAll] Consultando todas las tareas");
            return taskRepository.findAll()
                    .doOnComplete(() -> log.info("[findAll] Consulta completada"));
        }
        log.info("[findAll] Consultando tareas con status={}", status);
        return taskRepository.findByStatus(status)
                .doOnComplete(() -> log.info("[findAll] Consulta con filtro completada"));
    }

    public Mono<Task> findById(UUID id) {
        log.info("[findById] Buscando tarea: id={}", id);
        return taskRepository.findById(id)
                .switchIfEmpty(Mono.error(new TaskNotFoundException(id)))
                .doOnNext(task -> log.info("[findById] Tarea encontrada: id={}, status={}", task.getId(), task.getStatus()))
                .doOnError(TaskNotFoundException.class, ex -> log.warn("[findById] Tarea no encontrada: id={}", id));
    }

    public Mono<Task> update(UUID id, String title, String description) {
        log.info("[update] Actualizando tarea: id={}, title='{}'", id, title.trim());
        return taskRepository.findById(id)
                .switchIfEmpty(Mono.error(new TaskNotFoundException(id)))
                .doOnError(TaskNotFoundException.class, ex -> log.warn("[update] Tarea no encontrada: id={}", id))
                .flatMap(existing -> taskRepository.update(
                        Task.builder()
                                .id(existing.getId())
                                .title(title.trim())
                                .description(description)
                                .status(existing.getStatus())
                                .createdAt(existing.getCreatedAt())
                                .updatedAt(OffsetDateTime.now())
                                .build()
                ))
                .doOnNext(updated -> log.info("[update] Tarea actualizada: id={}", updated.getId()))
                .doOnError(ex -> log.error("[update] Error al actualizar id={}: {}", id, ex.getMessage()));
    }

    public Mono<Void> delete(UUID id) {
        log.info("[delete] Eliminando tarea: id={}", id);
        return taskRepository.findById(id)
                .switchIfEmpty(Mono.error(new TaskNotFoundException(id)))
                .doOnError(TaskNotFoundException.class, ex -> log.warn("[delete] Tarea no encontrada: id={}", id))
                .flatMap(task -> taskRepository.deleteById(task.getId()))
                .doOnSuccess(v -> log.info("[delete] Tarea eliminada: id={}", id))
                .doOnError(ex -> log.error("[delete] Error al eliminar id={}: {}", id, ex.getMessage()));
    }

    public Flux<TaskStatusHistory> findHistory(UUID taskId) {
        log.info("[findHistory] Consultando historial: taskId={}", taskId);
        return taskRepository.findById(taskId)
                .switchIfEmpty(Mono.error(new TaskNotFoundException(taskId)))
                .doOnError(TaskNotFoundException.class, ex -> log.warn("[findHistory] Tarea no encontrada: taskId={}", taskId))
                .flatMapMany(task -> taskRepository.findHistoryByTaskId(taskId))
                .doOnComplete(() -> log.info("[findHistory] Historial completado: taskId={}", taskId));
    }
}
