package com.military.ams.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.military.ams.dto.CreateUserRequest;
import com.military.ams.dto.PageResponse;
import com.military.ams.dto.UpdateUserRequest;
import com.military.ams.dto.UserDto;
import com.military.ams.entity.AppUser;
import com.military.ams.entity.MilitaryBase;
import com.military.ams.entity.Role;
import com.military.ams.entity.RoleName;
import com.military.ams.exception.DuplicateResourceException;
import com.military.ams.exception.ForbiddenOperationException;
import com.military.ams.exception.ResourceNotFoundException;
import com.military.ams.repository.RoleRepository;
import com.military.ams.repository.UserRepository;
import com.military.ams.security.AccessScopeService;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final BaseService baseService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final AccessScopeService accessScopeService;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       BaseService baseService,
                       PasswordEncoder passwordEncoder,
                       AuditService auditService,
                       AccessScopeService accessScopeService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.baseService = baseService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.accessScopeService = accessScopeService;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserDto> search(String role, Long baseId, int page, int size) {
        Page<AppUser> result = userRepository.search(
                role == null || role.isBlank() ? null : role.trim(),
                baseId,
                PageRequest.of(page, size));
        return PageResponse.of(result.getContent().stream().map(UserService::toDto).toList(),
                page, size, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public UserDto findById(Long id) {
        return toDto(require(id));
    }

    @Transactional
    public UserDto create(CreateUserRequest request) {
        String username = request.username().trim();
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("Username '" + username + "' is already taken");
        }
        String email = trimToNull(request.email());
        if (email != null && userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email '" + email + "' is already registered");
        }

        RoleName roleName = parseRole(request.role());
        MilitaryBase base = resolveBase(roleName, request.baseId());

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setRole(requireRole(roleName));
        user.setBase(base);
        user.setEnabled(request.enabled() == null || request.enabled());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        UserDto dto = toDto(userRepository.save(user));
        auditService.log(AuditService.ACTION_CREATE, "USER", dto.id(), base == null ? null : base.getId(),
                "Created user '" + dto.username() + "' with role " + dto.role()
                        + (base == null ? "" : " for base " + base.getCode()));
        return dto;
    }

    @Transactional
    public UserDto update(Long id, UpdateUserRequest request) {
        AppUser user = require(id);
        String email = trimToNull(request.email());
        if (email != null && userRepository.findByEmail(email).filter(other -> !other.getId().equals(id)).isPresent()) {
            throw new DuplicateResourceException("Email '" + email + "' is already registered");
        }

        RoleName roleName = parseRole(request.role());
        MilitaryBase base = resolveBase(roleName, request.baseId());

        boolean isSelf = accessScopeService.currentUser().getId().equals(id);
        if (isSelf && roleName != RoleName.ADMIN) {
            throw new ForbiddenOperationException("You cannot remove your own administrator role.");
        }
        if (Boolean.FALSE.equals(request.enabled()) && isSelf) {
            throw new ForbiddenOperationException("You cannot disable your own account.");
        }

        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setRole(requireRole(roleName));
        user.setBase(base);
        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
        }
        user.setUpdatedAt(LocalDateTime.now());

        UserDto dto = toDto(userRepository.save(user));
        auditService.log(AuditService.ACTION_UPDATE, "USER", dto.id(), base == null ? null : base.getId(),
                "Updated user '" + dto.username() + "' (role " + dto.role() + ", enabled " + dto.enabled() + ")");
        return dto;
    }

    @Transactional
    public UserDto setEnabled(Long id, boolean enabled) {
        AppUser user = require(id);
        if (accessScopeService.currentUser().getId().equals(id) && !enabled) {
            throw new ForbiddenOperationException("You cannot disable your own account.");
        }
        user.setEnabled(enabled);
        user.setUpdatedAt(LocalDateTime.now());
        UserDto dto = toDto(userRepository.save(user));
        auditService.log(AuditService.ACTION_STATUS_CHANGE, "USER", dto.id(),
                user.getBase() == null ? null : user.getBase().getId(),
                (enabled ? "Enabled" : "Disabled") + " user '" + dto.username() + "'");
        return dto;
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        AppUser user = require(id);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        auditService.log(AuditService.ACTION_PASSWORD_RESET, "USER", user.getId(),
                user.getBase() == null ? null : user.getBase().getId(),
                "Reset password for user '" + user.getUsername() + "'");
    }

    @Transactional
    public void delete(Long id) {
        AppUser user = require(id);
        if (accessScopeService.currentUser().getId().equals(id)) {
            throw new ForbiddenOperationException("You cannot delete your own account.");
        }
        userRepository.delete(user);
        auditService.log(AuditService.ACTION_DELETE, "USER", id, null,
                "Deleted user '" + user.getUsername() + "'");
    }

    @Transactional(readOnly = true)
    public AppUser require(Long id) {
        return userRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("User", id));
    }

    private Role requireRole(RoleName roleName) {
        return roleRepository.findByName(roleName.name())
                .orElseThrow(() -> new ResourceNotFoundException("Role " + roleName + " is not configured"));
    }

    private RoleName parseRole(String role) {
        if (role == null || role.isBlank()) {
            throw new ResourceNotFoundException("A role must be selected");
        }
        try {
            return RoleName.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResourceNotFoundException("Unknown role '" + role + "'");
        }
    }

    private MilitaryBase resolveBase(RoleName roleName, Long baseId) {
        if (roleName == RoleName.BASE_COMMANDER) {
            if (baseId == null) {
                throw new IllegalArgumentException("baseId is required for the BASE_COMMANDER role");
            }
            return baseService.require(baseId);
        }
        return baseId == null ? null : baseService.require(baseId);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static UserDto toDto(AppUser user) {
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().getName(),
                user.getBase() == null ? null : user.getBase().getId(),
                user.getBase() == null ? null : user.getBase().getCode(),
                user.getBase() == null ? null : user.getBase().getName(),
                user.getEnabled(),
                user.getCreatedAt());
    }

    public static List<String> knownRoles() {
        return List.of(RoleName.ADMIN.name(), RoleName.BASE_COMMANDER.name(), RoleName.LOGISTICS_OFFICER.name());
    }
}
