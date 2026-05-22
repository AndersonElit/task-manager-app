package com.taskscreator.model.port;

import com.taskscreator.model.event.TaskCreatedEvent;
import reactor.core.publisher.Mono;

public interface EventPublisher {
    Mono<Void> publish(TaskCreatedEvent event);
}
