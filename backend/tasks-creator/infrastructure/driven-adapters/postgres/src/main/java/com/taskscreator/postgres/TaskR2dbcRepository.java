package com.taskscreator.postgres;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface TaskR2dbcRepository extends ReactiveCrudRepository<TaskEntity, UUID> {
    Flux<TaskEntity> findByStatusId(Short statusId);
}
