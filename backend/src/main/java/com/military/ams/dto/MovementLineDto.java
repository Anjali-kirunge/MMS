package com.military.ams.dto;

import java.time.LocalDate;

/**
 * A single movement line shown when drilling into dashboard Net Movement.
 */
public record MovementLineDto(
        Long id,
        String movementType,
        String referenceNo,
        LocalDate movementDate,
        String fromBase,
        String toBase,
        String equipmentCode,
        String equipmentName,
        String unit,
        int quantity) {
}
