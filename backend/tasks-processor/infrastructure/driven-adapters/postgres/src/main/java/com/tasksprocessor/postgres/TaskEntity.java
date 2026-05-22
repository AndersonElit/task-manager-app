package com.tasksprocessor.postgres;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
@Table("tasks")
public class TaskEntity {
    @Id
    private UUID id;
    @Column("status_id")
    private Short statusId;
}
