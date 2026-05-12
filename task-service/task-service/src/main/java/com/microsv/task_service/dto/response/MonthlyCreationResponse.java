package com.microsv.task_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyCreationResponse {
    private Long thisMonthEvents;
    private Long thisMonthTasks;
    private Long lastMonthEvents;
    private Long lastMonthTasks;
    private Double eventsChange;
    private Double tasksChange;
}
