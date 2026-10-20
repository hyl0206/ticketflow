package com.ticketflow.auth;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "[A-Za-z0-9_]{3,50}", message = "用户名须为 3 至 50 位字母、数字或下划线")
        String username,
        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 72, message = "密码长度须为 8 至 72 位")
        String password,
        @Email(message = "邮箱格式不正确")
        @Size(max = 100, message = "邮箱长度不能超过 100")
        String email) {

    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        // Reject role/roles/status/id rather than silently accepting privilege-looking input.
        throw new IllegalArgumentException("注册请求包含不支持的字段");
    }
}
