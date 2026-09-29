package com.military.ams.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BaseRequest(
        @NotBlank(message = "code is required") @Size(max = 20) String code,
        @NotBlank(message = "name is required") @Size(max = 120) String name,
        @Size(max = 200) String location,
        @Size(max = 120) String commander) {
}
