package com.ticketflow.vo;

import com.ticketflow.enums.TicketPriority;
import com.ticketflow.enums.TicketStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TicketDetailResponse {

    private Long id;
    private String ticketNo;
    private String title;
    private String description;
    private TicketStatus status;
    private TicketPriority priority;
    private Long creatorId;
    private Long assigneeId;
    private LocalDateTime dueDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;
}
