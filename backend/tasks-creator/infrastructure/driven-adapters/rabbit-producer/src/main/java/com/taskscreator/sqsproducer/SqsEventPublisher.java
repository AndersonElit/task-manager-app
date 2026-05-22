package com.taskscreator.sqsproducer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskscreator.model.event.TaskCreatedEvent;
import com.taskscreator.model.port.EventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

@Component
@RequiredArgsConstructor
public class SqsEventPublisher implements EventPublisher {

    private final SqsAsyncClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${sqs.queue-url}")
    private String queueUrl;

    @Override
    public Mono<Void> publish(TaskCreatedEvent event) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(event))
                .flatMap(body -> Mono.fromFuture(sqsClient.sendMessage(req -> req
                        .queueUrl(queueUrl)
                        .messageBody(body)
                )))
                .then();
    }
}
