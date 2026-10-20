package com.ticketflow.auth;

import com.ticketflow.common.Result;
import com.ticketflow.security.AuthenticatedUser;
import com.ticketflow.security.CurrentUserService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final CurrentUserService currentUser;

    public AuthController(AuthService auth, CurrentUserService currentUser) {
        this.auth = auth;
        this.currentUser = currentUser;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements
    public Result<AuthenticatedUser> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(auth.register(request));
    }

    @PostMapping("/login")
    @SecurityRequirements
    public Result<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(auth.login(request));
    }

    @GetMapping("/me")
    public Result<AuthenticatedUser> me() {
        return Result.success(currentUser.require());
    }
}
