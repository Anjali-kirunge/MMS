package com.military.ams.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PurchaseRequest(
        @Size(max = 40) String referenceNo,
        @NotNull(message = "baseId is required") Long baseId,
        @NotBlank(message = "supplier is required") @Size(max = 150) String supplier,
        @Size(max = 80) String invoiceNo,
        @NotNull(message = "purchaseDate is required") LocalDate purchaseDate,
        @Size(max = 255) String remarks,
        @NotEmpty(message = "at least one line item is required") @Valid List<PurchaseItemRequest> items) {
}
