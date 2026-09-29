package com.military.ams.dto;

public record DashboardTotalsDto(
        long openingBalance,
        long purchases,
        long transferIn,
        long transferOut,
        long netMovement,
        long assigned,
        long expended,
        long closingBalance) {
}
