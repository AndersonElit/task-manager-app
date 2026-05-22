package com.taskscreator.restapi;

import com.taskscreator.model.TaskStatus;
import com.taskscreator.restapi.dto.TaskHistoryResponse;
import com.taskscreator.restapi.dto.TaskRequest;
import com.taskscreator.restapi.dto.TaskResponse;
import com.taskscreator.usecases.TaskUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskUseCase taskUseCase;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<TaskResponse> create(@RequestBody @Valid TaskRequest request) {
        log.info("[POST /api/v1/tasks] Crear tarea: title='{}'", request.title());
        return taskUseCase.create(request.title(), request.description())
                .map(TaskResponse::from)
                .doOnSuccess(r -> log.info("[POST /api/v1/tasks] Respuesta enviada: id={}", r.id()));
    }

    @GetMapping
    public Flux<TaskResponse> getAll(@RequestParam(required = false) String status) {
        log.info("[GET /api/v1/tasks] Listar tareas - filtro: {}", status != null ? status : "todos");
        TaskStatus taskStatus = status != null ? TaskStatus.fromName(status) : null;
        return taskUseCase.findAll(taskStatus).map(TaskResponse::from);
    }

    @GetMapping("/{id}")
    public Mono<TaskResponse> getById(@PathVariable UUID id) {
        log.info("[GET /api/v1/tasks/{}] Buscar tarea", id);
        return taskUseCase.findById(id).map(TaskResponse::from);
    }

    @PutMapping("/{id}")
    public Mono<TaskResponse> update(@PathVariable UUID id, @RequestBody @Valid TaskRequest request) {
        log.info("[PUT /api/v1/tasks/{}] Actualizar tarea: title='{}'", id, request.title());
        return taskUseCase.update(id, request.title(), request.description())
                .map(TaskResponse::from)
                .doOnSuccess(r -> log.info("[PUT /api/v1/tasks/{}] Tarea actualizada correctamente", id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable UUID id) {
        log.info("[DELETE /api/v1/tasks/{}] Eliminar tarea", id);
        return taskUseCase.delete(id)
                .doOnSuccess(v -> log.info("[DELETE /api/v1/tasks/{}] Tarea eliminada correctamente", id));
    }

    @GetMapping("/{id}/history")
    public Flux<TaskHistoryResponse> getHistory(@PathVariable UUID id) {
        log.info("[GET /api/v1/tasks/{}/history] Consultar historial", id);
        return taskUseCase.findHistory(id).map(TaskHistoryResponse::from);
    }
}
