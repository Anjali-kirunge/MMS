package com.military.ams.dto;

import java.time.LocalDateTime;

public record AuditLogDto(
        Long id,
        Long userId,
        String username,
        String action,
        String entity,
        Long entityId,
        Long baseId,
        String baseCode,
        String description,
        LocalDateTime createdAt) {
}
