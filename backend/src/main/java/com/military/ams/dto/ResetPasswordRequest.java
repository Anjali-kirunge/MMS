package com.military.ams.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "newPassword is required") @Size(min = 6, max = 100) String newPassword) {
}
