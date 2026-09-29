package com.military.ams.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record TransferDto(
        Long id,
        String referenceNo,
        Long sourceBaseId,
        String sourceBaseCode,
        String sourceBaseName,
        Long destinationBaseId,
        String destinationBaseCode,
        String destinationBaseName,
        LocalDate transferDate,
        String remarks,
        String createdBy,
        LocalDateTime createdAt,
        List<TransferItemDto> items) {
}
