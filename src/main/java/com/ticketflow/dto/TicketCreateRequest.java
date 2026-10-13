package com.ticketflow.dto;

import com.ticketflow.enums.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class TicketCreateRequest {

    @NotBlank(message = "工单标题不能为空")
    private String title;

    private String description;

    @NotNull(message = "工单优先级不能为空")
    private TicketPriority priority;

    @NotNull(message = "创建人ID不能为空")
    @Positive(message = "创建人ID必须大于0")
    private Long creatorId;
}

