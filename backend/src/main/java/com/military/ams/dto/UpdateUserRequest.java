package com.military.ams.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank(message = "fullName is required") @Size(max = 120) String fullName,
        @Email(message = "email must be valid") @Size(max = 150) String email,
        @NotBlank(message = "role is required") String role,
        Long baseId,
        Boolean enabled) {
}
