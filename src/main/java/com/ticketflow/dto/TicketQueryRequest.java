package com.ticketflow.dto;

import com.ticketflow.enums.TicketPriority;
import com.ticketflow.enums.TicketStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class TicketQueryRequest {

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码必须大于等于1")
    private Integer pageNum = 1;

    @NotNull(message = "每页条数不能为空")
    @Min(value = 1, message = "每页条数必须大于等于1")
    @Max(value = 100, message = "每页条数不能大于100")
    private Integer pageSize = 10;

    private String keyword;

    private TicketStatus status;

    private TicketPriority priority;

    @Positive(message = "创建人ID必须大于0")
    private Long creatorId;
}
