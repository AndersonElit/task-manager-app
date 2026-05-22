package com.taskscreator.usecases;

import com.taskscreator.model.Task;
import com.taskscreator.model.TaskStatus;
import com.taskscreator.model.TaskStatusHistory;
import com.taskscreator.model.event.TaskCreatedEvent;
import com.taskscreator.model.exception.TaskNotFoundException;
import com.taskscreator.model.port.EventPublisher;
import com.taskscreator.model.port.TaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskUseCaseTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private TaskUseCase taskUseCase;

    // -------------------------------------------------------------------------
    // TC-UC-01: create — happy path
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-UC-01: create debe guardar la tarea con status PENDIENTE y publicar el evento")
    void create_shouldSaveTaskAndPublishEvent() {
        // given
        String title = "  Mi tarea  ";
        String description = "Descripción de prueba";

        Task savedTask = Task.builder()
                .id(UUID.randomUUID())
                .title(title.trim())
                .description(description)
                .status(TaskStatus.PENDIENTE)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        when(taskRepository.save(any(Task.class))).thenReturn(Mono.just(savedTask));
        when(eventPublisher.publish(any(TaskCreatedEvent.class))).thenReturn(Mono.empty());

        // when / then
        StepVerifier.create(taskUseCase.create(title, description))
                .assertNext(task -> {
                    assertThat(task.getId()).isEqualTo(savedTask.getId());
                    assertThat(task.getTitle()).isEqualTo("Mi tarea");
                    assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDIENTE);
                })
                .verifyComplete();

        verify(taskRepository, times(1)).save(any(Task.class));
        verify(eventPublisher, times(1)).publish(any(TaskCreatedEvent.class));
    }

    // -------------------------------------------------------------------------
    // TC-UC-02: create — the task is saved with trimmed title
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-UC-02: create debe hacer trim al título antes de guardar")
    void create_shouldTrimTitle() {
        // given
        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        String title = "   Task con espacios   ";

        Task savedTask = Task.builder()
                .id(UUID.randomUUID())
                .title(title.trim())
                .description(null)
                .status(TaskStatus.PENDIENTE)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        when(taskRepository.save(captor.capture())).thenReturn(Mono.just(savedTask));
        when(eventPublisher.publish(any(TaskCreatedEvent.class))).thenReturn(Mono.empty());

        // when / then
        StepVerifier.create(taskUseCase.create(title, null))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(captor.getValue().getTitle()).isEqualTo("Task con espacios");
    }

    // -------------------------------------------------------------------------
    // TC-UC-03: create — event contains correct data
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-UC-03: create debe publicar evento con los datos correctos de la tarea")
    void create_shouldPublishEventWithCorrectData() {
        // given
        String title = "Evento task";
        String description = "desc";
        UUID taskId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        Task savedTask = Task.builder()
                .id(taskId)
                .title(title)
                .description(description)
                .status(TaskStatus.PENDIENTE)
                .createdAt(now)
                .updatedAt(now)
                .build();

        ArgumentCaptor<TaskCreatedEvent> eventCaptor = ArgumentCaptor.forClass(TaskCreatedEvent.class);

        when(taskRepository.save(any(Task.class))).thenReturn(Mono.just(savedTask));
        when(eventPublisher.publish(eventCaptor.capture())).thenReturn(Mono.empty());

        // when / then
        StepVerifier.create(taskUseCase.create(title, description))
                .expectNextCount(1)
                .verifyComplete();

        TaskCreatedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.taskId()).isEqualTo(taskId);
        assertThat(publishedEvent.title()).isEqualTo(title);
        assertThat(publishedEvent.description()).isEqualTo(description);
    }

    // -------------------------------------------------------------------------
    // TC-UC-E01: create — repository error propagates
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-UC-E01: create debe propagar el error cuando el repositorio falla")
    void create_shouldPropagateError_whenRepositoryFails() {
        // given
        when(taskRepository.save(any(Task.class))).thenReturn(Mono.error(new RuntimeException("DB error")));

        // when / then
        StepVerifier.create(taskUseCase.create("titulo", "desc"))
                .expectErrorMatches(ex -> ex instanceof RuntimeException && ex.getMessage().equals("DB error"))
                .verify();

        verify(eventPublisher, never()).publish(any());
    }

    // -------------------------------------------------------------------------
    // TC-UC-04: findAll — without filter returns all tasks
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-UC-04: findAll sin filtro debe retornar todas las tareas")
    void findAll_withNullStatus_shouldReturnAllTasks() {
        // given
        Task task1 = buildTask(UUID.randomUUID(), "Task 1", TaskStatus.PENDIENTE);
        Task task2 = buildTask(UUID.randomUUID(), "Task 2", TaskStatus.COMPLETADA);

        when(taskRepository.findAll()).thenReturn(Flux.just(task1, task2));

        // when / then
        StepVerifier.create(taskUseCase.findAll(null))
                .expectNext(task1)
                .expectNext(task2)
                .verifyComplete();

        verify(taskRepository, times(1)).findAll();
        verify(taskRepository, never()).findByStatus(any());
    }

    // -------------------------------------------------------------------------
    // TC-UC-05: findAll — with status filter calls findByStatus
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-UC-05: findAll con filtro de status debe llamar findByStatus")
    void findAll_withStatus_shouldCallFindByStatus() {
        // given
        Task task = buildTask(UUID.randomUUID(), "Task filtrada", TaskStatus.PENDIENTE);

        when(taskRepository.findByStatus(TaskStatus.PENDIENTE)).thenReturn(Flux.just(task));

        // when / then
        StepVerifier.create(taskUseCase.findAll(TaskStatus.PENDIENTE))
                .expectNext(task)
                .verifyComplete();

        verify(taskRepository, never()).findAll();
        verify(taskRepository, times(1)).findByStatus(TaskStatus.PENDIENTE);
    }

    // -------------------------------------------------------------------------
    // TC-UC-06: findAll — empty repository returns empty Flux
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-UC-06: findAll debe retornar Flux vacío cuando no hay tareas")
    void findAll_shouldReturnEmptyFlux_whenNoTasks() {
        // given
        when(taskRepository.findAll()).thenReturn(Flux.empty());

        // when / then
        StepVerifier.create(taskUseCase.findAll(null))
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // TC-UC-07: findById — existing task
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-UC-07: findById debe retornar la tarea cuando existe")
    void findById_shouldReturnTask_whenExists() {
        // given
        UUID id = UUID.randomUUID();
        Task task = buildTask(id, "Task encontrada", TaskStatus.PENDIENTE);

        when(taskRepository.findById(id)).thenReturn(Mono.just(task));

        // when / then
        StepVerifier.create(taskUseCase.findById(id))
                .assertNext(t -> assertThat(t.getId()).isEqualTo(id))
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // TC-UC-E02: findById — not found throws TaskNotFoundException
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-UC-E02: findById debe lanzar TaskNotFoundException cuando la tarea no existe")
    void findById_shouldThrowTaskNotFoundException_whenNotFound() {
        // given
        UUID id = UUID.randomUUID();
        when(taskRepository.findById(id)).thenReturn(Mono.empty());

        // when / then
        StepVerifier.create(taskUseCase.findById(id))
                .expectErrorMatches(ex -> ex instanceof TaskNotFoundException
                        && ex.getMessage().contains(id.toString()))
                .verify();
    }

    // -------------------------------------------------------------------------
    // TC-UC-08: update — existing task is updated
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-UC-08: update debe actualizar la tarea correctamente cuando existe")
    void update_shouldUpdateTask_whenExists() {
        // given
        UUID id = UUID.randomUUID();
        Task existing = buildTask(id, "Titulo original", TaskStatus.PENDIENTE);
        Task updated = Task.builder()
                .id(id)
                .title("Titulo actualizado")
                .description("Nueva desc")
                .status(TaskStatus.PENDIENTE)
                .createdAt(existing.getCreatedAt())
                .updatedAt(OffsetDateTime.now())
                .build();

        when(taskRepository.findById(id)).thenReturn(Mono.just(existing));
        when(taskRepository.update(any(Task.class))).thenReturn(Mono.just(updated));

        // when / then
        StepVerifier.create(taskUseCase.update(id, "  Titulo actualizado  ", "Nueva desc"))
                .assertNext(t -> {
                    assertThat(t.getId()).isEqualTo(id);
                    assertThat(t.getTitle()).isEqualTo("Titulo actualizado");
                })
                .verifyComplete();

        verify(taskRepository, times(1)).findById(id);
        verify(taskRepository, times(1)).update(any(Task.class));
    }

    // -------------------------------------------------------------------------
    // TC-UC-E03: update — task not found throws TaskNotFoundException
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-UC-E03: update debe lanzar TaskNotFoundException cuando la tarea no existe")
    void update_shouldThrowTaskNotFoundException_whenNotFound() {
        // given
        UUID id = UUID.randomUUID();
        when(taskRepository.findById(id)).thenReturn(Mono.empty());

        // when / then
        StepVerifier.create(taskUseCase.update(id, "nuevo titulo", "desc"))
                .expectErrorMatches(ex -> ex instanceof TaskNotFoundException)
                .verify();

        verify(taskRepository, never()).update(any());
    }

    // -------------------------------------------------------------------------
    // TC-UC-09: update — trimmed title is used when updating
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-UC-09: update debe hacer trim al nuevo título")
    void update_shouldTrimTitle() {
        // given
        UUID id = UUID.randomUUID();
        Task existing = buildTask(id, "Titulo original", TaskStatus.PENDIENTE);
        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);

        when(taskRepository.findById(id)).thenReturn(Mono.just(existing));
        when(taskRepository.update(captor.capture())).thenReturn(Mono.just(existing));

        // when / then
        StepVerifier.create(taskUseCase.update(id, "   Con espacios   ", "desc"))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(captor.getValue().getTitle()).isEqualTo("Con espacios");
    }

    // -------------------------------------------------------------------------
    // TC-UC-10: delete — existing task is deleted
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-UC-10: delete debe eliminar la tarea cuando existe")
    void delete_shouldDeleteTask_whenExists() {
        // given
        UUID id = UUID.randomUUID();
        Task task = buildTask(id, "Task a eliminar", TaskStatus.PENDIENTE);

        when(taskRepository.findById(id)).thenReturn(Mono.just(task));
        when(taskRepository.deleteById(id)).thenReturn(Mono.empty());

        // when / then
        StepVerifier.create(taskUseCase.delete(id))
                .verifyComplete();

        verify(taskRepository, times(1)).findById(id);
        verify(taskRepository, times(1)).deleteById(id);
    }

    // -------------------------------------------------------------------------
    // TC-UC-E04: delete — task not found throws TaskNotFoundException
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-UC-E04: delete debe lanzar TaskNotFoundException cuando la tarea no existe")
    void delete_shouldThrowTaskNotFoundException_whenNotFound() {
        // given
        UUID id = UUID.randomUUID();
        when(taskRepository.findById(id)).thenReturn(Mono.empty());

        // when / then
        StepVerifier.create(taskUseCase.delete(id))
                .expectErrorMatches(ex -> ex instanceof TaskNotFoundException)
                .verify();

        verify(taskRepository, never()).deleteById(any());
    }

    // -------------------------------------------------------------------------
    // TC-UC-11: findHistory — returns history for existing task
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-UC-11: findHistory debe retornar el historial cuando la tarea existe")
    void findHistory_shouldReturnHistory_whenTaskExists() {
        // given
        UUID taskId = UUID.randomUUID();
        Task task = buildTask(taskId, "Task con historial", TaskStatus.COMPLETADA);
        TaskStatusHistory h1 = new TaskStatusHistory(UUID.randomUUID(), "PENDIENTE", OffsetDateTime.now().minusDays(1));
        TaskStatusHistory h2 = new TaskStatusHistory(UUID.randomUUID(), "COMPLETADA", OffsetDateTime.now());

        when(taskRepository.findById(taskId)).thenReturn(Mono.just(task));
        when(taskRepository.findHistoryByTaskId(taskId)).thenReturn(Flux.just(h1, h2));

        // when / then
        StepVerifier.create(taskUseCase.findHistory(taskId))
                .expectNext(h1)
                .expectNext(h2)
                .verifyComplete();

        verify(taskRepository, times(1)).findById(taskId);
        verify(taskRepository, times(1)).findHistoryByTaskId(taskId);
    }

    // -------------------------------------------------------------------------
    // TC-UC-E05: findHistory — task not found throws TaskNotFoundException
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-UC-E05: findHistory debe lanzar TaskNotFoundException cuando la tarea no existe")
    void findHistory_shouldThrowTaskNotFoundException_whenTaskNotFound() {
        // given
        UUID taskId = UUID.randomUUID();
        when(taskRepository.findById(taskId)).thenReturn(Mono.empty());

        // when / then
        StepVerifier.create(taskUseCase.findHistory(taskId))
                .expectErrorMatches(ex -> ex instanceof TaskNotFoundException
                        && ex.getMessage().contains(taskId.toString()))
                .verify();

        verify(taskRepository, never()).findHistoryByTaskId(any());
    }

    // -------------------------------------------------------------------------
    // TC-UC-E06: create — event publisher error propagates
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-UC-E06: create debe propagar el error cuando el publisher falla")
    void create_shouldPropagateError_whenPublisherFails() {
        // given
        Task savedTask = buildTask(UUID.randomUUID(), "titulo", TaskStatus.PENDIENTE);

        when(taskRepository.save(any(Task.class))).thenReturn(Mono.just(savedTask));
        when(eventPublisher.publish(any(TaskCreatedEvent.class)))
                .thenReturn(Mono.error(new RuntimeException("SQS error")));

        // when / then
        StepVerifier.create(taskUseCase.create("titulo", "desc"))
                .expectErrorMatches(ex -> ex instanceof RuntimeException && ex.getMessage().equals("SQS error"))
                .verify();
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------
    private Task buildTask(UUID id, String title, TaskStatus status) {
        return Task.builder()
                .id(id)
                .title(title)
                .description("desc")
                .status(status)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }
}
