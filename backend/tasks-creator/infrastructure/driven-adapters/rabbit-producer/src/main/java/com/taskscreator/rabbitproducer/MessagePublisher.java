package com.taskscreator.rabbitproducer;

import reactor.core.publisher.Mono;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class MessagePublisher {

    private final RabbitTemplate rabbitTemplate;

    public MessagePublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public Mono<Void> publish(Object message) {
        return Mono.fromRunnable(() ->
            rabbitTemplate.convertAndSend(RabbitMQConfig.QUEUE_NAME, message)
        );
    }
}
