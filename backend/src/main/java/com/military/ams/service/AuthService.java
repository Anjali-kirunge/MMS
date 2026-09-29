package com.military.ams.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.military.ams.dto.AuthResponse;
import com.military.ams.dto.LoginRequest;
import com.military.ams.dto.UserInfoDto;
import com.military.ams.entity.AppUser;
import com.military.ams.exception.ApiException;
import com.military.ams.repository.UserRepository;
import com.military.ams.security.AppUserPrincipal;
import com.military.ams.security.CurrentUserService;
import com.military.ams.security.JwtService;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final CurrentUserService currentUserService;

    public AuthService(AuthenticationManager authenticationManager,
                       UserRepository userRepository,
                       JwtService jwtService,
                       AuditService auditService,
                       CurrentUserService currentUserService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String username = request.username().trim();
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, request.password()));
            AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();

            String token = jwtService.generateToken(principal);
            auditService.logAs(principal.getId(), principal.getUsername(), AuditService.ACTION_LOGIN,
                    "AUTH", null, principal.getBaseId(), "User '" + username + "' signed in");

            return new AuthResponse(token, "Bearer", jwtService.getExpirationMs(), toUserInfo(principal));
        } catch (AuthenticationException ex) {
            auditService.logAs(null, username, AuditService.ACTION_LOGIN_FAILED, "AUTH", null, null,
                    "Failed sign-in attempt for username '" + username + "'");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
    }

    @Transactional(readOnly = true)
    public UserInfoDto me() {
        AppUserPrincipal principal = currentUserService.requireCurrentUser();
        return toUserInfo(principal);
    }

    private UserInfoDto toUserInfo(AppUserPrincipal principal) {
        AppUser user = userRepository.findById(principal.getId()).orElse(null);
        String baseCode = user != null && user.getBase() != null ? user.getBase().getCode() : null;
        String baseName = user != null && user.getBase() != null ? user.getBase().getName() : null;
        return new UserInfoDto(principal.getId(), principal.getUsername(), principal.getFullName(),
                user != null ? user.getEmail() : null, principal.getRole().name(),
                principal.getBaseId(), baseCode, baseName);
    }
}
