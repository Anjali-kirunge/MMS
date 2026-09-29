package com.military.ams.dto;

public record AuthResponse(
        String token,
        String tokenType,
        long expiresInMs,
        UserInfoDto user) {
}
