package com.military.ams.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ExpenditureDto(
        Long id,
        Long baseId,
        String baseCode,
        String baseName,
        Long equipmentTypeId,
        String equipmentCode,
        String equipmentName,
        String unit,
        Integer quantity,
        LocalDate expendedDate,
        String reason,
        String remarks,
        String createdBy,
        LocalDateTime createdAt) {
}
