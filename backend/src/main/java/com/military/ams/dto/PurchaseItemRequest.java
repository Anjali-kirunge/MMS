package com.military.ams.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PurchaseItemRequest(
        @NotNull(message = "equipmentTypeId is required") Long equipmentTypeId,
        @NotNull(message = "quantity is required") @Positive(message = "quantity must be greater than zero") Integer quantity,
        @Min(value = 0, message = "unitCost cannot be negative") BigDecimal unitCost) {
}
