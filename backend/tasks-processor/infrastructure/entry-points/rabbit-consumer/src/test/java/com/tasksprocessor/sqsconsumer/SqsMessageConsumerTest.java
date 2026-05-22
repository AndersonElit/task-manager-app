package com.tasksprocessor.sqsconsumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tasksprocessor.model.event.TaskCreatedEvent;
import com.tasksprocessor.usecases.CompleteTaskUseCase;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SqsMessageConsumerTest {

    @Mock
    private SqsAsyncClient sqsClient;

    @Mock
    private CompleteTaskUseCase completeTaskUseCase;

    private ObjectMapper objectMapper;
    private SqsMessageConsumer consumer;

    private static final String TEST_QUEUE_URL = "http://localhost:4566/000000000000/test-queue";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        consumer = new SqsMessageConsumer(sqsClient, completeTaskUseCase, objectMapper);
        ReflectionTestUtils.setField(consumer, "queueUrl", TEST_QUEUE_URL);
    }

    // -------------------------------------------------------------------------
    // TC-MC-01: valid JSON → use case called with correct taskId
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-MC-01: mensaje JSON válido debe llamar al use case con el taskId correcto")
    void startPolling_validMessage_shouldCallUseCaseWithCorrectTaskId() throws Exception {
        // given
        UUID taskId = UUID.randomUUID();
        String body = buildEventJson(taskId);
        Message message = buildMessage("receipt-1", body);
        ReceiveMessageResponse response = ReceiveMessageResponse.builder()
                .messages(List.of(message))
                .build();

        AtomicInteger callCount = new AtomicInteger(0);
        when(sqsClient.receiveMessage(any(Consumer.class))).thenAnswer(inv -> {
            if (callCount.getAndIncrement() == 0) {
                return CompletableFuture.completedFuture(response);
            }
            // Never complete — stops the infinite loop
            return new CompletableFuture<>();
        });

        when(completeTaskUseCase.execute(taskId)).thenReturn(Mono.empty());
        when(sqsClient.deleteMessage(any(Consumer.class)))
                .thenReturn(CompletableFuture.completedFuture(DeleteMessageResponse.builder().build()));

        // when
        consumer.startPolling();

        // then
        Awaitility.await()
                .atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(completeTaskUseCase, times(1)).execute(taskId));
    }

    // -------------------------------------------------------------------------
    // TC-MC-02: valid JSON → deleteMessage called after use case
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-MC-02: mensaje válido debe eliminar el mensaje de SQS tras el use case")
    void startPolling_validMessage_shouldDeleteMessageAfterUseCase() throws Exception {
        // given
        UUID taskId = UUID.randomUUID();
        String body = buildEventJson(taskId);
        Message message = buildMessage("receipt-2", body);
        ReceiveMessageResponse response = ReceiveMessageResponse.builder()
                .messages(List.of(message))
                .build();

        AtomicInteger callCount = new AtomicInteger(0);
        when(sqsClient.receiveMessage(any(Consumer.class))).thenAnswer(inv -> {
            if (callCount.getAndIncrement() == 0) {
                return CompletableFuture.completedFuture(response);
            }
            return new CompletableFuture<>();
        });

        when(completeTaskUseCase.execute(taskId)).thenReturn(Mono.empty());
        when(sqsClient.deleteMessage(any(Consumer.class)))
                .thenReturn(CompletableFuture.completedFuture(DeleteMessageResponse.builder().build()));

        // when
        consumer.startPolling();

        // then
        Awaitility.await()
                .atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(sqsClient, times(1)).deleteMessage(any(Consumer.class)));
    }

    // -------------------------------------------------------------------------
    // TC-MC-03: malformed JSON → use case NOT called, deleteMessage NOT called
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-MC-03: JSON malformado no debe llamar al use case ni eliminar el mensaje")
    void startPolling_malformedJson_shouldNotCallUseCaseOrDeleteMessage() throws Exception {
        // given
        Message message = buildMessage("receipt-3", "this is not json {{}}");
        ReceiveMessageResponse response = ReceiveMessageResponse.builder()
                .messages(List.of(message))
                .build();

        AtomicInteger callCount = new AtomicInteger(0);
        when(sqsClient.receiveMessage(any(Consumer.class))).thenAnswer(inv -> {
            if (callCount.getAndIncrement() == 0) {
                return CompletableFuture.completedFuture(response);
            }
            return new CompletableFuture<>();
        });

        // when
        consumer.startPolling();

        // then — wait long enough for the consumer to attempt processing
        Thread.sleep(300);

        verify(completeTaskUseCase, never()).execute(any());
        verify(sqsClient, never()).deleteMessage(any(Consumer.class));
    }

    // -------------------------------------------------------------------------
    // TC-MC-04: use case throws error → deleteMessage NOT called
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-MC-04: error en use case no debe eliminar el mensaje")
    void startPolling_useCaseThrowsError_shouldNotDeleteMessage() throws Exception {
        // given
        UUID taskId = UUID.randomUUID();
        String body = buildEventJson(taskId);
        Message message = buildMessage("receipt-4", body);
        ReceiveMessageResponse response = ReceiveMessageResponse.builder()
                .messages(List.of(message))
                .build();

        AtomicInteger callCount = new AtomicInteger(0);
        when(sqsClient.receiveMessage(any(Consumer.class))).thenAnswer(inv -> {
            if (callCount.getAndIncrement() == 0) {
                return CompletableFuture.completedFuture(response);
            }
            return new CompletableFuture<>();
        });

        when(completeTaskUseCase.execute(taskId))
                .thenReturn(Mono.error(new RuntimeException("Use case failure")));

        // when
        consumer.startPolling();

        // then — give time for processing to attempt
        Thread.sleep(300);

        verify(sqsClient, never()).deleteMessage(any(Consumer.class));
    }

    // -------------------------------------------------------------------------
    // TC-MC-05: taskId in JSON passed correctly to use case
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-MC-05: el taskId del JSON debe ser pasado correctamente al use case")
    void startPolling_shouldPassCorrectTaskIdFromJson() throws Exception {
        // given
        UUID specificTaskId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        String body = buildEventJson(specificTaskId);
        Message message = buildMessage("receipt-5", body);
        ReceiveMessageResponse response = ReceiveMessageResponse.builder()
                .messages(List.of(message))
                .build();

        AtomicInteger callCount = new AtomicInteger(0);
        when(sqsClient.receiveMessage(any(Consumer.class))).thenAnswer(inv -> {
            if (callCount.getAndIncrement() == 0) {
                return CompletableFuture.completedFuture(response);
            }
            return new CompletableFuture<>();
        });

        when(completeTaskUseCase.execute(specificTaskId)).thenReturn(Mono.empty());
        when(sqsClient.deleteMessage(any(Consumer.class)))
                .thenReturn(CompletableFuture.completedFuture(DeleteMessageResponse.builder().build()));

        // when
        consumer.startPolling();

        // then
        Awaitility.await()
                .atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(completeTaskUseCase, times(1)).execute(specificTaskId));
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------
    private String buildEventJson(UUID taskId) throws Exception {
        TaskCreatedEvent event = new TaskCreatedEvent(taskId, "Titulo test", "Desc test", OffsetDateTime.now());
        return objectMapper.writeValueAsString(event);
    }

    private Message buildMessage(String receiptHandle, String body) {
        return Message.builder()
                .messageId(UUID.randomUUID().toString())
                .receiptHandle(receiptHandle)
                .body(body)
                .build();
    }
}
