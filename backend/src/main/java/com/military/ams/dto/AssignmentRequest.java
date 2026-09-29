package com.military.ams.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AssignmentRequest(
        @NotNull(message = "baseId is required") Long baseId,
        @NotNull(message = "equipmentTypeId is required") Long equipmentTypeId,
        @NotNull(message = "personnelId is required") Long personnelId,
        @NotNull(message = "quantity is required") @Positive(message = "quantity must be greater than zero") Integer quantity,
        @NotNull(message = "assignedDate is required") LocalDate assignedDate,
        @Size(max = 255) String remarks) {
}
