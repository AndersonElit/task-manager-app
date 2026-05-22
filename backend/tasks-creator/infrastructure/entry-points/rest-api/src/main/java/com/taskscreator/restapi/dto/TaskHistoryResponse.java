package com.taskscreator.restapi.dto;

import com.taskscreator.model.TaskStatusHistory;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TaskHistoryResponse(
        UUID id,
        String status,
        OffsetDateTime date
) {
    public static TaskHistoryResponse from(TaskStatusHistory history) {
        return new TaskHistoryResponse(history.getId(), history.getStatusName(), history.getDate());
    }
}
