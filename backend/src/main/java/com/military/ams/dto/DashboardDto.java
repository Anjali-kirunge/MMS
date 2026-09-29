package com.military.ams.dto;

import java.time.LocalDate;
import java.util.List;

public record DashboardDto(
        LocalDate from,
        LocalDate to,
        Long baseId,
        Long equipmentTypeId,
        DashboardTotalsDto totals,
        List<DashboardRowDto> rows) {
}
