package com.tasksprocessor.usecases;

import com.tasksprocessor.model.TaskStatus;
import com.tasksprocessor.model.port.TaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompleteTaskUseCaseTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private CompleteTaskUseCase completeTaskUseCase;

    // -------------------------------------------------------------------------
    // TC-CP-01: execute — happy path calls updateStatus with COMPLETADA
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-CP-01: execute debe llamar updateStatus con taskId y status COMPLETADA")
    void execute_shouldCallUpdateStatusWithCompletada() {
        // given
        UUID taskId = UUID.randomUUID();
        when(taskRepository.updateStatus(eq(taskId), eq(TaskStatus.COMPLETADA))).thenReturn(Mono.empty());

        // when / then
        StepVerifier.create(completeTaskUseCase.execute(taskId))
                .verifyComplete();

        verify(taskRepository, times(1)).updateStatus(taskId, TaskStatus.COMPLETADA);
    }

    // -------------------------------------------------------------------------
    // TC-CP-02: execute — correct taskId is forwarded to repository
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-CP-02: execute debe pasar el taskId correcto al repositorio")
    void execute_shouldForwardCorrectTaskId() {
        // given
        UUID taskId = UUID.randomUUID();
        ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<TaskStatus> statusCaptor = ArgumentCaptor.forClass(TaskStatus.class);

        when(taskRepository.updateStatus(idCaptor.capture(), statusCaptor.capture())).thenReturn(Mono.empty());

        // when / then
        StepVerifier.create(completeTaskUseCase.execute(taskId))
                .verifyComplete();

        assertThat(idCaptor.getValue()).isEqualTo(taskId);
        assertThat(statusCaptor.getValue()).isEqualTo(TaskStatus.COMPLETADA);
    }

    // -------------------------------------------------------------------------
    // TC-CP-03: execute — completes without emitting any element
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-CP-03: execute debe completar sin emitir elementos")
    void execute_shouldCompleteWithoutEmittingElements() {
        // given
        UUID taskId = UUID.randomUUID();
        when(taskRepository.updateStatus(any(), any())).thenReturn(Mono.empty());

        // when / then
        StepVerifier.create(completeTaskUseCase.execute(taskId))
                .expectNextCount(0)
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // TC-CP-04: execute — propagates repository error
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-CP-04: execute debe propagar el error cuando el repositorio falla")
    void execute_shouldPropagateError_whenRepositoryFails() {
        // given
        UUID taskId = UUID.randomUUID();
        RuntimeException repoError = new RuntimeException("DB connection failed");
        when(taskRepository.updateStatus(eq(taskId), eq(TaskStatus.COMPLETADA)))
                .thenReturn(Mono.error(repoError));

        // when / then
        StepVerifier.create(completeTaskUseCase.execute(taskId))
                .expectErrorMatches(ex -> ex instanceof RuntimeException
                        && ex.getMessage().equals("DB connection failed"))
                .verify();
    }
}
