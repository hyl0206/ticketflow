package com.ticketflow.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ticketflow.enums.TicketPriority;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TicketUpdateRequest {
    @Size(max = 200, message = "工单标题不能超过200个字符")
    @Pattern(regexp = "(?s).*\\S.*", message = "工单标题不能为空")
    private String title;

    @Size(max = 10000, message = "工单描述不能超过10000个字符")
    @Pattern(regexp = "(?s).*\\S.*", message = "工单描述不能为空")
    private String description;

    private TicketPriority priority;

    @JsonIgnore
    @AssertTrue(message = "至少提供一个可修改字段")
    public boolean isChangeProvided() {
        return title != null || description != null || priority != null;
    }

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("请求包含不支持的字段");
    }
}
