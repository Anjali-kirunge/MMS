package com.military.ams.dto;

import java.time.LocalDate;

public record DashboardRowDto(
        Long baseId,
        String baseCode,
        String baseName,
        Long equipmentTypeId,
        String equipmentCode,
        String equipmentName,
        String unit,
        long openingBalance,
        long purchases,
        long transferIn,
        long transferOut,
        long netMovement,
        long assigned,
        long expended,
        long closingBalance) {
}
