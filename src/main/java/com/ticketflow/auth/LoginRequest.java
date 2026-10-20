package com.ticketflow.auth;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "用户名不能为空") @Size(max = 50, message = "用户名长度不能超过 50") String username,
        @NotBlank(message = "密码不能为空") @Size(max = 72, message = "密码长度不能超过 72") String password) {

    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("登录请求包含不支持的字段");
    }
}
