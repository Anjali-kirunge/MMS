package com.military.ams.dto;

public record TransferItemDto(
        Long id,
        Long equipmentTypeId,
        String equipmentCode,
        String equipmentName,
        String unit,
        Integer quantity) {
}
