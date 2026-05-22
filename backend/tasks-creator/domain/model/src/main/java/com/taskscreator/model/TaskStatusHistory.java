package com.taskscreator.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class TaskStatusHistory {
    private UUID id;
    private String statusName;
    private OffsetDateTime date;
}
