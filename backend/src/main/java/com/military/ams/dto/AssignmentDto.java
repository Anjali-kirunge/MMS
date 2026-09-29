package com.military.ams.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AssignmentDto(
        Long id,
        Long baseId,
        String baseCode,
        String baseName,
        Long equipmentTypeId,
        String equipmentCode,
        String equipmentName,
        String unit,
        Long personnelId,
        String personnelServiceNumber,
        String personnelName,
        Integer quantity,
        LocalDate assignedDate,
        String remarks,
        String createdBy,
        LocalDateTime createdAt) {
}
