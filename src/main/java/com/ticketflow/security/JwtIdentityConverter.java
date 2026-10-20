package com.ticketflow.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class JwtIdentityConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final UserIdentityService identities;

    public JwtIdentityConverter(UserIdentityService identities) {
        this.identities = identities;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        final long userId;
        try {
            userId = Long.parseLong(jwt.getSubject());
            if (userId <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            throw invalidToken();
        }
        AuthenticatedUser user = identities.findActive(userId).orElseThrow(this::invalidToken);
        return UsernamePasswordAuthenticationToken.authenticated(user, null,
                user.roles().stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList());
    }

    private OAuth2AuthenticationException invalidToken() {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_token"), "登录凭证无效或用户已禁用");
    }
}
