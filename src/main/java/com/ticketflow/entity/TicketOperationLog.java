package com.ticketflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ticketflow.enums.TicketOperationType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ticket_operation_log")
public class TicketOperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ticketId;

    private Long operatorId;

    private TicketOperationType operationType;

    private String oldValue;

    private String newValue;

    private String remark;

    private LocalDateTime createdAt;
}