package com.ticketflow.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ticketflow.entity.Role;
import com.ticketflow.entity.User;
import com.ticketflow.entity.UserRole;
import com.ticketflow.exception.BusinessException;
import com.ticketflow.mapper.RoleMapper;
import com.ticketflow.mapper.UserMapper;
import com.ticketflow.mapper.UserRoleMapper;
import com.ticketflow.security.AuthenticatedUser;
import com.ticketflow.security.UserIdentityService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Set;

@Service
public class AuthService {
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwords;
    private final UserIdentityService identities;
    private final JwtTokenService tokens;
    private final String dummyPasswordHash;

    public AuthService(UserMapper userMapper, RoleMapper roleMapper, UserRoleMapper userRoleMapper,
                       PasswordEncoder passwords, UserIdentityService identities, JwtTokenService tokens) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwords = passwords;
        this.identities = identities;
        this.tokens = tokens;
        this.dummyPasswordHash = passwords.encode("ticketflow-invalid-credentials");
    }

    @Transactional
    public AuthenticatedUser register(RegisterRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD",
                    "密码的 UTF-8 编码长度不能超过 72 字节");
        }
        if (userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, request.username())) != null) {
            throw usernameTaken();
        }
        Role defaultRole = roleMapper.selectOne(Wrappers.<Role>lambdaQuery()
                .eq(Role::getRoleCode, "USER").eq(Role::getStatus, 1));
        if (defaultRole == null) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "REGISTRATION_UNAVAILABLE",
                    "注册暂不可用，请联系管理员初始化用户角色");
        }
        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setUsername(request.username());
        user.setPassword(passwords.encode(request.password()));
        user.setEmail(request.email());
        user.setStatus(1);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        try {
            if (userMapper.insert(user) != 1) {
                throw new IllegalStateException("注册用户写入失败");
            }
        } catch (DuplicateKeyException exception) {
            // The unique database constraint also protects concurrent registrations.
            throw usernameTaken();
        }
        UserRole relation = new UserRole();
        relation.setUserId(user.getId());
        relation.setRoleId(defaultRole.getId());
        relation.setCreatedAt(now);
        if (userRoleMapper.insert(relation) != 1) {
            throw new IllegalStateException("注册用户角色写入失败");
        }
        return new AuthenticatedUser(user.getId(), user.getUsername(), Set.of("USER"));
    }

    public TokenResponse login(LoginRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw invalidCredentials();
        }
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, request.username()));
        String hash = user == null ? dummyPasswordHash : user.getPassword();
        boolean validPassword = hash != null && passwords.matches(request.password(), hash);
        if (user == null || !validPassword || !Integer.valueOf(1).equals(user.getStatus())) {
            throw invalidCredentials();
        }
        return tokens.issue(identities.identityFor(user));
    }

    private BusinessException usernameTaken() {
        return new BusinessException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "用户名已存在");
    }

    private BusinessException invalidCredentials() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "用户名或密码错误，或用户不可用");
    }
}
