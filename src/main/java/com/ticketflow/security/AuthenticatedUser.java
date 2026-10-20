package com.ticketflow.security;

import java.util.Set;

/** A trusted identity loaded from the database, never from request parameters. */
public record AuthenticatedUser(Long id, String username, Set<String> roles) {
    public AuthenticatedUser {
        roles = Set.copyOf(roles);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
