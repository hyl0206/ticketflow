package com.ticketflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketflow.auth.AuthController;
import com.ticketflow.auth.AuthService;
import com.ticketflow.auth.RegisterRequest;
import com.ticketflow.common.Result;
import com.ticketflow.exception.GlobalExceptionHandler;
import com.ticketflow.security.AuthenticatedUser;
import com.ticketflow.security.CurrentUserService;
import com.ticketflow.security.JwtConfiguration;
import com.ticketflow.security.JwtIdentityConverter;
import com.ticketflow.security.SecurityConfiguration;
import com.ticketflow.security.SecurityErrorHandler;
import com.ticketflow.security.UserIdentityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises actual JWT signing, verification and the Spring Security filter chain without a database. */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = SecurityFilterTest.TestConfiguration.class)
@TestPropertySource(properties = {
        "ticketflow.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "ticketflow.jwt.issuer=ticketflow",
        "ticketflow.jwt.ttl=PT1H"
})
class SecurityFilterTest {
    @Autowired private WebApplicationContext context;
    @Autowired private JwtEncoder encoder;
    @Autowired private UserIdentityService identities;
    @Autowired private AuthService auth;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(identities, auth);
        when(identities.findActive(1L)).thenReturn(Optional.of(new AuthenticatedUser(1L, "alice", Set.of("USER"))));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void protectedRequestWithoutTokenReturnsJson401() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
        verifyNoInteractions(identities);
    }

    @Test
    void validTokenReturnsDatabaseIdentityWithoutSession() throws Exception {
        var result = mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.username").value("alice"))
                .andExpect(jsonPath("$.data.roles[0]").value("USER"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andReturn();
        assertNull(result.getRequest().getSession(false));
        verify(identities).findActive(1L);
    }

    @Test
    void tamperedSignatureIsRejectedBeforeDatabaseLookup() throws Exception {
        String token = validToken();
        String[] segments = token.split("\\.");
        char first = segments[2].charAt(0);
        segments[2] = (first == 'A' ? "B" : "A") + segments[2].substring(1);
        assertUnauthorized(String.join(".", segments));
        verifyNoInteractions(identities);
    }

    @Test
    void unsignedAlgorithmIsRejectedBeforeDatabaseLookup() throws Exception {
        String[] segments = validToken().split("\\.");
        segments[0] = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        assertUnauthorized(String.join(".", segments));
        verifyNoInteractions(identities);
    }

    @Test
    void expiredTokenIsRejectedBeforeDatabaseLookup() throws Exception {
        assertUnauthorized(token(claims().issuedAt(Instant.now().minusSeconds(3600))
                .expiresAt(Instant.now().minusSeconds(5)).build()));
        verifyNoInteractions(identities);
    }

    @Test
    void wrongIssuerAndMissingExpiryAreRejected() throws Exception {
        assertUnauthorized(token(claims().issuer("another-service").build()));
        assertUnauthorized(token(JwtClaimsSet.builder().issuer("ticketflow").subject("1")
                .issuedAt(Instant.now()).build()));
        verifyNoInteractions(identities);
    }

    @Test
    void disabledAccountInvalidatesPreviouslyIssuedToken() throws Exception {
        String token = validToken();
        when(identities.findActive(1L)).thenReturn(Optional.empty());
        assertUnauthorized(token);
        verify(identities).findActive(1L);
    }

    @Test
    void tokenRoleClaimCannotGrantAdminPrivileges() throws Exception {
        String forgedRole = token(claims().claim("roles", List.of("ADMIN")).build());
        mvc.perform(get("/api/security-test/admin").header(HttpHeaders.AUTHORIZATION, "Bearer " + forgedRole))
                .andExpect(status().isForbidden())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void roleRevocationAppliesToTheSameTokenOnTheNextRequest() throws Exception {
        String token = validToken();
        when(identities.findActive(1L)).thenReturn(
                Optional.of(new AuthenticatedUser(1L, "alice", Set.of("ADMIN"))),
                Optional.of(new AuthenticatedUser(1L, "alice", Set.of("USER"))));
        mvc.perform(get("/api/security-test/admin").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/security-test/admin").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        verify(identities, times(2)).findActive(1L);
    }

    @Test
    void registrationIsPublicAndDoesNotRequireCsrfToken() throws Exception {
        when(auth.register(any())).thenReturn(new AuthenticatedUser(2L, "bob", Set.of("USER")));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"password\":\"Secret123!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roles[0]").value("USER"));
        verify(auth).register(new RegisterRequest("bob", "Secret123!", null));
    }

    @Test
    void registrationRejectsRoleEscalationInputBeforeServiceCall() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"password\":\"Secret123!\",\"roles\":[\"ADMIN\"]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(auth);
    }

    @Test
    void registrationRejectsInvalidUsernameAndWeakPassword() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"a\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(auth);
    }

    private void assertUnauthorized(String token) throws Exception {
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    private String validToken() {
        return token(claims().build());
    }

    private JwtClaimsSet.Builder claims() {
        return JwtClaimsSet.builder().issuer("ticketflow").subject("1")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600));
    }

    private String token(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    @Import({JwtConfiguration.class, SecurityConfiguration.class, JwtIdentityConverter.class,
            SecurityErrorHandler.class, AuthController.class, CurrentUserService.class,
            GlobalExceptionHandler.class, AdminTestController.class})
    static class TestConfiguration {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean UserIdentityService identities() { return mock(UserIdentityService.class); }
        @Bean AuthService auth() { return mock(AuthService.class); }
    }

    @RestController
    static class AdminTestController {
        @GetMapping("/api/security-test/admin")
        @PreAuthorize("hasRole('ADMIN')")
        public Result<String> admin() {
            return Result.success("allowed");
        }
    }
}
