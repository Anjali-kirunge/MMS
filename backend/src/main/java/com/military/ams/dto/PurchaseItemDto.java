package com.military.ams.dto;

import java.math.BigDecimal;

public record PurchaseItemDto(
        Long id,
        Long equipmentTypeId,
        String equipmentCode,
        String equipmentName,
        String unit,
        Integer quantity,
        BigDecimal unitCost,
        BigDecimal lineTotal) {
}
