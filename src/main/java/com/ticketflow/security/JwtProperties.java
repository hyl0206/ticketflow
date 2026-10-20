package com.ticketflow.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ticketflow.jwt")
public record JwtProperties(String secret, String issuer, Duration ttl) {
    public JwtProperties {
        issuer = issuer == null ? "ticketflow" : issuer;
        ttl = ttl == null ? Duration.ofHours(1) : ttl;
        if (issuer.isBlank()) {
            throw new IllegalArgumentException("ticketflow.jwt.issuer 不能为空");
        }
        if (ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException("ticketflow.jwt.ttl 必须大于 0 且不超过 24 小时");
        }
    }
}
