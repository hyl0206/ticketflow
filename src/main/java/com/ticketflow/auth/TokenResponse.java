package com.ticketflow.auth;

import com.ticketflow.security.AuthenticatedUser;

public record TokenResponse(String accessToken, String tokenType, long expiresIn, AuthenticatedUser user) {
}
