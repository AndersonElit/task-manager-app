package com.taskscreator.restapi;

import com.taskscreator.model.TaskStatus;
import com.taskscreator.restapi.dto.TaskHistoryResponse;
import com.taskscreator.restapi.dto.TaskRequest;
import com.taskscreator.restapi.dto.TaskResponse;
import com.taskscreator.usecases.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final CreateTaskUseCase createTaskUseCase;
    private final GetTaskByIdUseCase getTaskByIdUseCase;
    private final GetAllTasksUseCase getAllTasksUseCase;
    private final UpdateTaskUseCase updateTaskUseCase;
    private final DeleteTaskUseCase deleteTaskUseCase;
    private final GetTaskHistoryUseCase getTaskHistoryUseCase;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<TaskResponse> create(@RequestBody @Valid TaskRequest request) {
        return createTaskUseCase.execute(request.title(), request.description())
                .map(TaskResponse::from);
    }

    @GetMapping
    public Flux<TaskResponse> getAll(@RequestParam(required = false) String status) {
        TaskStatus taskStatus = status != null ? TaskStatus.fromName(status) : null;
        return getAllTasksUseCase.execute(taskStatus).map(TaskResponse::from);
    }

    @GetMapping("/{id}")
    public Mono<TaskResponse> getById(@PathVariable UUID id) {
        return getTaskByIdUseCase.execute(id).map(TaskResponse::from);
    }

    @PutMapping("/{id}")
    public Mono<TaskResponse> update(@PathVariable UUID id, @RequestBody @Valid TaskRequest request) {
        return updateTaskUseCase.execute(id, request.title(), request.description())
                .map(TaskResponse::from);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable UUID id) {
        return deleteTaskUseCase.execute(id);
    }

    @GetMapping("/{id}/history")
    public Flux<TaskHistoryResponse> getHistory(@PathVariable UUID id) {
        return getTaskHistoryUseCase.execute(id).map(TaskHistoryResponse::from);
    }
}
