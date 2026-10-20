package com.ticketflow.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TicketAssignRequest(
        @NotNull(message = "处理人ID不能为空")
        @Positive(message = "处理人ID必须大于0") Long assigneeId) {
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("请求包含不支持的字段");
    }
}
