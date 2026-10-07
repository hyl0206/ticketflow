package com.ticketflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.ticketflow.enums.TicketPriority;
import com.ticketflow.enums.TicketStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ticket")
public class Ticket {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("ticket_no")
    private String ticketNo;

    private String title;

    private String description;

    private TicketStatus status;

    private TicketPriority priority;

    @TableField("creator_id")
    private Long creatorId;

    @TableField("assignee_id")
    private Long assigneeId;

    @TableField("due_date")
    private LocalDateTime dueDate;

    @Version
    private Integer version;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("resolved_at")
    private LocalDateTime resolvedAt;

    @TableField("closed_at")
    private LocalDateTime closedAt;
}
