package com.military.ams.dto;

public record UserInfoDto(
        Long id,
        String username,
        String fullName,
        String email,
        String role,
        Long baseId,
        String baseCode,
        String baseName) {
}
