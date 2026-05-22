package com.taskscreator.model.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TaskCreatedEvent(
        UUID taskId,
        String title,
        String description,
        OffsetDateTime createdAt
) {}
