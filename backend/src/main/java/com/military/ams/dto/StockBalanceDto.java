package com.military.ams.dto;

import java.time.LocalDateTime;

public record StockBalanceDto(
        Long id,
        Long baseId,
        String baseCode,
        String baseName,
        Long equipmentTypeId,
        String equipmentCode,
        String equipmentName,
        String category,
        String unit,
        Integer openingBalance,
        Integer onHandQuantity,
        LocalDateTime updatedAt) {
}
