package com.microsv.task_service.enumeration;

import com.fasterxml.jackson.annotation.JsonFormat;

@JsonFormat(shape = JsonFormat.Shape.STRING)
public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    DONE
}
