package com.military.ams.dto;

public record PersonnelDto(
        Long id,
        String serviceNumber,
        String fullName,
        String rankTitle,
        Long baseId,
        String baseCode,
        String baseName,
        String contact) {
}
