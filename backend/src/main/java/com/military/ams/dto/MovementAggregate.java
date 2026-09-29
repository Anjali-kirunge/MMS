package com.military.ams.dto;

/**
 * Flat projection used by dashboard aggregate queries.
 */
public record MovementAggregate(Long baseId, Long equipmentTypeId, Long quantity) {
}
