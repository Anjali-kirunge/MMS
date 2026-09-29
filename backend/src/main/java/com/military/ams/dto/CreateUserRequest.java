package com.military.ams.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank(message = "username is required") @Size(min = 3, max = 60) String username,
        @NotBlank(message = "password is required") @Size(min = 6, max = 100) String password,
        @NotBlank(message = "fullName is required") @Size(max = 120) String fullName,
        @Email(message = "email must be valid") @Size(max = 150) String email,
        @NotBlank(message = "role is required") String role,
        Long baseId,
        Boolean enabled) {
}
