package com.tasksprocessor.sqsconsumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tasksprocessor.model.event.TaskCreatedEvent;
import com.tasksprocessor.usecases.CompleteTaskUseCase;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqsMessageConsumer {

    private final SqsAsyncClient sqsClient;
    private final CompleteTaskUseCase completeTaskUseCase;
    private final ObjectMapper objectMapper;

    @Value("${sqs.queue-url}")
    private String queueUrl;

    @PostConstruct
    public void startPolling() {
        Mono.fromFuture(() -> sqsClient.receiveMessage(req -> req
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(10)
                        .waitTimeSeconds(20)   // long polling — reduce llamadas vacías
                ))
                .flatMapIterable(ReceiveMessageResponse::messages)
                .flatMap(this::processMessage)
                .repeat()                      // vuelve a suscribirse al completar cada lote
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(ex -> log.error("Error en el loop de polling SQS: {}", ex.getMessage()))
                .retry()                       // reinicia el loop ante cualquier error
                .subscribe();

        log.info("SQS consumer iniciado. Escuchando en: {}", queueUrl);
    }

    private Mono<Void> processMessage(Message message) {
        return Mono.fromCallable(() -> objectMapper.readValue(message.body(), TaskCreatedEvent.class))
                .doOnNext(event -> log.info("Evento recibido: taskId={}", event.taskId()))
                .flatMap(event -> completeTaskUseCase.execute(event.taskId()))
                .then(deleteMessage(message))
                .doOnSuccess(v -> log.info("Mensaje procesado y eliminado: {}", message.messageId()))
                .onErrorResume(ex -> {
                    log.error("Error procesando mensaje {}: {}", message.messageId(), ex.getMessage());
                    return Mono.empty();   // no elimina el mensaje → SQS lo reintentará
                });
    }

    private Mono<Void> deleteMessage(Message message) {
        return Mono.fromFuture(() -> sqsClient.deleteMessage(req -> req
                        .queueUrl(queueUrl)
                        .receiptHandle(message.receiptHandle())
                ))
                .then();
    }
}
