package com.military.ams.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TransferItemRequest(
        @NotNull(message = "equipmentTypeId is required") Long equipmentTypeId,
        @NotNull(message = "quantity is required") @Positive(message = "quantity must be greater than zero") Integer quantity) {
}
