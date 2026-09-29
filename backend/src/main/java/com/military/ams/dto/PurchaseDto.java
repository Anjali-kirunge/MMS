package com.military.ams.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PurchaseDto(
        Long id,
        String referenceNo,
        Long baseId,
        String baseCode,
        String baseName,
        String supplier,
        String invoiceNo,
        LocalDate purchaseDate,
        String remarks,
        BigDecimal totalCost,
        String createdBy,
        LocalDateTime createdAt,
        List<PurchaseItemDto> items) {
}
