package com.military.ams.dto;

import java.time.LocalDateTime;

public record BaseDto(
        Long id,
        String code,
        String name,
        String location,
        String commander,
        LocalDateTime createdAt) {
}
