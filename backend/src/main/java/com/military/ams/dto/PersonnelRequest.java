package com.military.ams.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PersonnelRequest(
        @NotBlank(message = "serviceNumber is required") @Size(max = 40) String serviceNumber,
        @NotBlank(message = "fullName is required") @Size(max = 120) String fullName,
        @Size(max = 60) String rankTitle,
        @NotNull(message = "baseId is required") Long baseId,
        @Size(max = 40) String contact) {
}
