package com.military.ams.dto;

import java.time.LocalDateTime;

public record UserDto(
        Long id,
        String username,
        String fullName,
        String email,
        String role,
        Long baseId,
        String baseCode,
        String baseName,
        Boolean enabled,
        LocalDateTime createdAt) {
}
