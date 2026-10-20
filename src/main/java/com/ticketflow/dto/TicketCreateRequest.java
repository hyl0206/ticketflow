package com.ticketflow.dto;

import com.ticketflow.enums.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Data;

@Data
public class TicketCreateRequest {

    @NotBlank(message = "工单标题不能为空")
    @Size(max = 200, message = "工单标题不能超过200个字符")
    private String title;

    @NotBlank(message = "工单描述不能为空")
    @Size(max = 10000, message = "工单描述不能超过10000个字符")
    private String description;

    @NotNull(message = "工单优先级不能为空")
    private TicketPriority priority;

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("请求包含不支持的字段");
    }
}

