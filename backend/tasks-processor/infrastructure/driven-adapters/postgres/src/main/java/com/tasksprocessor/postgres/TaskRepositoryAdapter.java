package com.tasksprocessor.postgres;

import com.tasksprocessor.model.TaskStatus;
import com.tasksprocessor.model.port.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskRepositoryAdapter implements TaskRepository {

    private final R2dbcEntityTemplate template;

    @Override
    public Mono<Void> updateStatus(UUID taskId, TaskStatus status) {
        return template.update(TaskEntity.class)
                .matching(Query.query(Criteria.where("id").is(taskId)))
                .apply(Update.update("status_id", status.getId()))
                .doOnNext(count -> log.info("Tarea {} actualizada a {} ({} fila/s afectada/s)",
                        taskId, status, count))
                .then();
    }
}
