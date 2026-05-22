package com.taskscreator.restapi;

import com.taskscreator.model.Task;
import com.taskscreator.model.TaskStatus;
import com.taskscreator.model.TaskStatusHistory;
import com.taskscreator.model.exception.TaskNotFoundException;
import com.taskscreator.restapi.handler.GlobalErrorHandler;
import com.taskscreator.usecases.TaskUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@WebFluxTest(TaskController.class)
@Import(GlobalErrorHandler.class)
class TaskControllerTest {

    @SpringBootApplication(scanBasePackages = "com.taskscreator.restapi")
    static class TaskCreatorRestApiTestApp {
    }

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private TaskUseCase taskUseCase;

    // -------------------------------------------------------------------------
    // TC-CT-01: POST /api/v1/tasks — 201 with task response
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-CT-01: POST /api/v1/tasks debe retornar 201 con la tarea creada")
    void createTask_shouldReturn201WithTaskResponse() {
        // given
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        Task task = buildTask(id, "Nueva tarea", "desc", TaskStatus.PENDIENTE, now);

        when(taskUseCase.create(eq("Nueva tarea"), eq("desc"))).thenReturn(Mono.just(task));

        // when / then
        webTestClient.post()
                .uri("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"title": "Nueva tarea", "description": "desc"}
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo(id.toString())
                .jsonPath("$.title").isEqualTo("Nueva tarea")
                .jsonPath("$.status").isEqualTo("pendiente");
    }

    // -------------------------------------------------------------------------
    // TC-CT-E01: POST /api/v1/tasks — 400 when title is blank
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-CT-E01: POST /api/v1/tasks debe retornar 400 cuando el título está vacío")
    void createTask_shouldReturn400_whenTitleIsBlank() {
        webTestClient.post()
                .uri("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"title": "", "description": "desc"}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400);
    }

    // -------------------------------------------------------------------------
    // TC-CT-E02: POST /api/v1/tasks — 400 when title exceeds 255 chars
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-CT-E02: POST /api/v1/tasks debe retornar 400 cuando el título supera 255 caracteres")
    void createTask_shouldReturn400_whenTitleExceeds255Chars() {
        String longTitle = "a".repeat(256);
        webTestClient.post()
                .uri("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"title\": \"" + longTitle + "\", \"description\": \"desc\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400);
    }

    // -------------------------------------------------------------------------
    // TC-CT-02: GET /api/v1/tasks — 200 returns all tasks
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-CT-02: GET /api/v1/tasks debe retornar 200 con todas las tareas")
    void getAllTasks_shouldReturn200WithAllTasks() {
        // given
        OffsetDateTime now = OffsetDateTime.now();
        Task task1 = buildTask(UUID.randomUUID(), "Task 1", "d1", TaskStatus.PENDIENTE, now);
        Task task2 = buildTask(UUID.randomUUID(), "Task 2", "d2", TaskStatus.COMPLETADA, now);

        when(taskUseCase.findAll(null)).thenReturn(Flux.just(task1, task2));

        // when / then
        webTestClient.get()
                .uri("/api/v1/tasks")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Object.class)
                .hasSize(2);
    }

    // -------------------------------------------------------------------------
    // TC-CT-03: GET /api/v1/tasks?status=pendiente — filters by status
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-CT-03: GET /api/v1/tasks?status=pendiente debe filtrar por status")
    void getAllTasks_withStatusFilter_shouldFilterByStatus() {
        // given
        OffsetDateTime now = OffsetDateTime.now();
        Task task = buildTask(UUID.randomUUID(), "Task pendiente", "d", TaskStatus.PENDIENTE, now);

        when(taskUseCase.findAll(TaskStatus.PENDIENTE)).thenReturn(Flux.just(task));

        // when / then
        webTestClient.get()
                .uri("/api/v1/tasks?status=pendiente")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].status").isEqualTo("pendiente");
    }

    // -------------------------------------------------------------------------
    // TC-CT-E03: GET /api/v1/tasks?status=invalido — 400 for invalid status
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-CT-E03: GET /api/v1/tasks?status=invalido debe retornar 400")
    void getAllTasks_withInvalidStatus_shouldReturn400() {
        // The controller calls TaskStatus.fromName() synchronously, which throws IllegalArgumentException
        // GlobalErrorHandler catches it and returns 400
        webTestClient.get()
                .uri("/api/v1/tasks?status=invalido")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400);
    }

    // -------------------------------------------------------------------------
    // TC-CT-04: GET /api/v1/tasks/{id} — 200 returns task by id
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-CT-04: GET /api/v1/tasks/{id} debe retornar 200 con la tarea")
    void getTaskById_shouldReturn200WithTask() {
        // given
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        Task task = buildTask(id, "Task por id", "desc", TaskStatus.PENDIENTE, now);

        when(taskUseCase.findById(id)).thenReturn(Mono.just(task));

        // when / then
        webTestClient.get()
                .uri("/api/v1/tasks/{id}", id)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(id.toString())
                .jsonPath("$.title").isEqualTo("Task por id");
    }

    // -------------------------------------------------------------------------
    // TC-CT-E04: GET /api/v1/tasks/{id} — 404 when task not found
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-CT-E04: GET /api/v1/tasks/{id} debe retornar 404 cuando la tarea no existe")
    void getTaskById_shouldReturn404_whenNotFound() {
        // given
        UUID id = UUID.randomUUID();
        when(taskUseCase.findById(id)).thenReturn(Mono.error(new TaskNotFoundException(id)));

        // when / then
        webTestClient.get()
                .uri("/api/v1/tasks/{id}", id)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.status").isEqualTo(404);
    }

    // -------------------------------------------------------------------------
    // TC-CT-05: PUT /api/v1/tasks/{id} — 200 updates task
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-CT-05: PUT /api/v1/tasks/{id} debe retornar 200 con la tarea actualizada")
    void updateTask_shouldReturn200WithUpdatedTask() {
        // given
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        Task updatedTask = buildTask(id, "Titulo actualizado", "nueva desc", TaskStatus.PENDIENTE, now);

        when(taskUseCase.update(eq(id), eq("Titulo actualizado"), eq("nueva desc")))
                .thenReturn(Mono.just(updatedTask));

        // when / then
        webTestClient.put()
                .uri("/api/v1/tasks/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"title": "Titulo actualizado", "description": "nueva desc"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(id.toString())
                .jsonPath("$.title").isEqualTo("Titulo actualizado");
    }

    // -------------------------------------------------------------------------
    // TC-CT-E05: PUT /api/v1/tasks/{id} — 404 when task not found
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-CT-E05: PUT /api/v1/tasks/{id} debe retornar 404 cuando la tarea no existe")
    void updateTask_shouldReturn404_whenNotFound() {
        // given
        UUID id = UUID.randomUUID();
        when(taskUseCase.update(eq(id), anyString(), anyString()))
                .thenReturn(Mono.error(new TaskNotFoundException(id)));

        // when / then
        webTestClient.put()
                .uri("/api/v1/tasks/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"title": "Titulo", "description": "desc"}
                        """)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.status").isEqualTo(404);
    }

    // -------------------------------------------------------------------------
    // TC-CT-06: DELETE /api/v1/tasks/{id} — 204 deletes task
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-CT-06: DELETE /api/v1/tasks/{id} debe retornar 204")
    void deleteTask_shouldReturn204() {
        // given
        UUID id = UUID.randomUUID();
        when(taskUseCase.delete(id)).thenReturn(Mono.empty());

        // when / then
        webTestClient.delete()
                .uri("/api/v1/tasks/{id}", id)
                .exchange()
                .expectStatus().isNoContent();
    }

    // -------------------------------------------------------------------------
    // TC-CT-E06: DELETE /api/v1/tasks/{id} — 404 when task not found
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-CT-E06: DELETE /api/v1/tasks/{id} debe retornar 404 cuando la tarea no existe")
    void deleteTask_shouldReturn404_whenNotFound() {
        // given
        UUID id = UUID.randomUUID();
        when(taskUseCase.delete(id)).thenReturn(Mono.error(new TaskNotFoundException(id)));

        // when / then
        webTestClient.delete()
                .uri("/api/v1/tasks/{id}", id)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.status").isEqualTo(404);
    }

    // -------------------------------------------------------------------------
    // TC-CT-07: GET /api/v1/tasks/{id}/history — 200 with history
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-CT-07: GET /api/v1/tasks/{id}/history debe retornar 200 con el historial")
    void getHistory_shouldReturn200WithHistory() {
        // given
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        TaskStatusHistory h1 = new TaskStatusHistory(UUID.randomUUID(), "PENDIENTE", now.minusDays(1));
        TaskStatusHistory h2 = new TaskStatusHistory(UUID.randomUUID(), "COMPLETADA", now);

        when(taskUseCase.findHistory(id)).thenReturn(Flux.just(h1, h2));

        // when / then
        webTestClient.get()
                .uri("/api/v1/tasks/{id}/history", id)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Object.class)
                .hasSize(2);
    }

    // -------------------------------------------------------------------------
    // TC-CT-E07: GET /api/v1/tasks/{id}/history — 404 when task not found
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-CT-E07: GET /api/v1/tasks/{id}/history debe retornar 404 cuando la tarea no existe")
    void getHistory_shouldReturn404_whenTaskNotFound() {
        // given
        UUID id = UUID.randomUUID();
        when(taskUseCase.findHistory(id)).thenReturn(Flux.error(new TaskNotFoundException(id)));

        // when / then
        webTestClient.get()
                .uri("/api/v1/tasks/{id}/history", id)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.status").isEqualTo(404);
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------
    private Task buildTask(UUID id, String title, String description, TaskStatus status, OffsetDateTime ts) {
        return Task.builder()
                .id(id)
                .title(title)
                .description(description)
                .status(status)
                .createdAt(ts)
                .updatedAt(ts)
                .build();
    }
}
