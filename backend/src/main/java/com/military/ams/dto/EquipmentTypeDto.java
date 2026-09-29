package com.military.ams.dto;

public record EquipmentTypeDto(
        Long id,
        String code,
        String name,
        String category,
        String unit,
        String description) {
}
