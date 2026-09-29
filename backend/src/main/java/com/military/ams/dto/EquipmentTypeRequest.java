package com.military.ams.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EquipmentTypeRequest(
        @NotBlank(message = "code is required") @Size(max = 30) String code,
        @NotBlank(message = "name is required") @Size(max = 120) String name,
        @NotBlank(message = "category is required") @Size(max = 80) String category,
        @Size(max = 20) String unit,
        @Size(max = 255) String description) {
}
