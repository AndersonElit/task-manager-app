package com.taskscreator.sqsproducer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskscreator.model.event.TaskCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.test.StepVerifier;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SqsEventPublisherTest {

    @Mock
    private SqsAsyncClient sqsClient;

    private ObjectMapper objectMapper;
    private SqsEventPublisher publisher;

    private static final String TEST_QUEUE_URL = "http://localhost:4566/000000000000/test-queue";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        publisher = new SqsEventPublisher(sqsClient, objectMapper);
        ReflectionTestUtils.setField(publisher, "queueUrl", TEST_QUEUE_URL);
    }

    // -------------------------------------------------------------------------
    // TC-SQS-01: publish — queueUrl is sent to SQS
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-SQS-01: publish debe usar la queueUrl configurada al enviar el mensaje")
    void publish_shouldUseConfiguredQueueUrl() {
        // given
        TaskCreatedEvent event = buildEvent();

        ArgumentCaptor<Consumer<SendMessageRequest.Builder>> captor =
                ArgumentCaptor.forClass(Consumer.class);

        when(sqsClient.sendMessage(captor.capture()))
                .thenReturn(CompletableFuture.completedFuture(
                        SendMessageResponse.builder().messageId("msg-1").build()));

        // when / then
        StepVerifier.create(publisher.publish(event))
                .verifyComplete();

        // Verify the queueUrl used
        SendMessageRequest.Builder reqBuilder = SendMessageRequest.builder();
        captor.getValue().accept(reqBuilder);
        SendMessageRequest builtRequest = reqBuilder.build();
        assertThat(builtRequest.queueUrl()).isEqualTo(TEST_QUEUE_URL);
    }

    // -------------------------------------------------------------------------
    // TC-SQS-02: publish — message body contains taskId and title
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-SQS-02: publish debe serializar el evento con taskId y title en el body")
    void publish_shouldSerializeEventWithTaskIdAndTitle() {
        // given
        UUID taskId = UUID.randomUUID();
        TaskCreatedEvent event = new TaskCreatedEvent(taskId, "Mi titulo", "Mi descripcion", OffsetDateTime.now());

        ArgumentCaptor<Consumer<SendMessageRequest.Builder>> captor =
                ArgumentCaptor.forClass(Consumer.class);

        when(sqsClient.sendMessage(captor.capture()))
                .thenReturn(CompletableFuture.completedFuture(
                        SendMessageResponse.builder().messageId("msg-2").build()));

        // when / then
        StepVerifier.create(publisher.publish(event))
                .verifyComplete();

        SendMessageRequest.Builder reqBuilder = SendMessageRequest.builder();
        captor.getValue().accept(reqBuilder);
        String body = reqBuilder.build().messageBody();

        assertThat(body).contains(taskId.toString());
        assertThat(body).contains("Mi titulo");
    }

    // -------------------------------------------------------------------------
    // TC-SQS-03: publish — SQS error propagates
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-SQS-03: publish debe propagar el error cuando SQS falla")
    void publish_shouldPropagateError_whenSqsFails() {
        // given
        TaskCreatedEvent event = buildEvent();

        CompletableFuture<SendMessageResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("SQS unavailable"));

        when(sqsClient.sendMessage(any(Consumer.class))).thenReturn(failedFuture);

        // when / then
        StepVerifier.create(publisher.publish(event))
                .expectErrorMatches(ex -> ex.getMessage().contains("SQS unavailable"))
                .verify();
    }

    // -------------------------------------------------------------------------
    // TC-SQS-04: publish — message body is valid JSON
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("TC-SQS-04: publish debe enviar un body JSON válido")
    void publish_shouldSendValidJsonBody() throws Exception {
        // given
        TaskCreatedEvent event = buildEvent();

        ArgumentCaptor<Consumer<SendMessageRequest.Builder>> captor =
                ArgumentCaptor.forClass(Consumer.class);

        when(sqsClient.sendMessage(captor.capture()))
                .thenReturn(CompletableFuture.completedFuture(
                        SendMessageResponse.builder().messageId("msg-4").build()));

        // when / then
        StepVerifier.create(publisher.publish(event))
                .verifyComplete();

        SendMessageRequest.Builder reqBuilder = SendMessageRequest.builder();
        captor.getValue().accept(reqBuilder);
        String body = reqBuilder.build().messageBody();

        // Verify it's valid JSON and can be parsed back
        TaskCreatedEvent parsed = objectMapper.readValue(body, TaskCreatedEvent.class);
        assertThat(parsed.taskId()).isEqualTo(event.taskId());
        assertThat(parsed.title()).isEqualTo(event.title());
        assertThat(parsed.description()).isEqualTo(event.description());
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------
    private TaskCreatedEvent buildEvent() {
        return new TaskCreatedEvent(
                UUID.randomUUID(),
                "Titulo de prueba",
                "Descripcion de prueba",
                OffsetDateTime.now()
        );
    }
}
