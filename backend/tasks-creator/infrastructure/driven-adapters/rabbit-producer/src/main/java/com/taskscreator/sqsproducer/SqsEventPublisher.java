package com.taskscreator.sqsproducer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskscreator.model.event.TaskCreatedEvent;
import com.taskscreator.model.port.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqsEventPublisher implements EventPublisher {

    private final SqsAsyncClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${sqs.queue-url}")
    private String queueUrl;

    @Override
    public Mono<Void> publish(TaskCreatedEvent event) {
        log.info("[SqsEventPublisher] Publicando evento: taskId={}", event.taskId());
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(event))
                .doOnNext(body -> log.debug("[SqsEventPublisher] Payload: {}", body))
                .flatMap(body -> Mono.fromFuture(sqsClient.sendMessage(req -> req
                        .queueUrl(queueUrl)
                        .messageBody(body)
                )))
                .doOnNext(resp -> log.info("[SqsEventPublisher] Mensaje enviado a SQS: messageId={}", resp.messageId()))
                .doOnError(ex -> log.error("[SqsEventPublisher] Error al publicar taskId={}: {}", event.taskId(), ex.getMessage()))
                .then();
    }
}
