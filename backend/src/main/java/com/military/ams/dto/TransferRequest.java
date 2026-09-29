package com.military.ams.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransferRequest(
        @Size(max = 40) String referenceNo,
        @NotNull(message = "sourceBaseId is required") Long sourceBaseId,
        @NotNull(message = "destinationBaseId is required") Long destinationBaseId,
        @NotNull(message = "transferDate is required") LocalDate transferDate,
        @Size(max = 255) String remarks,
        @NotEmpty(message = "at least one line item is required") @Valid List<TransferItemRequest> items) {
}
