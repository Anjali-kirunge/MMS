package com.military.ams.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ExpenditureRequest(
        @NotNull(message = "baseId is required") Long baseId,
        @NotNull(message = "equipmentTypeId is required") Long equipmentTypeId,
        @NotNull(message = "quantity is required") @Positive(message = "quantity must be greater than zero") Integer quantity,
        @NotNull(message = "expendedDate is required") LocalDate expendedDate,
        @NotBlank(message = "reason is required") @Size(max = 150) String reason,
        @Size(max = 255) String remarks) {
}
