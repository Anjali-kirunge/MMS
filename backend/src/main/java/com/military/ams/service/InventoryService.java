package com.military.ams.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.military.ams.dto.PageResponse;
import com.military.ams.dto.StockBalanceDto;
import com.military.ams.entity.StockBalance;
import com.military.ams.repository.StockBalanceRepository;
import com.military.ams.security.AccessScopeService;

@Service
public class InventoryService {

    private final StockBalanceRepository stockBalanceRepository;
    private final AccessScopeService accessScopeService;

    public InventoryService(StockBalanceRepository stockBalanceRepository,
                            AccessScopeService accessScopeService) {
        this.stockBalanceRepository = stockBalanceRepository;
        this.accessScopeService = accessScopeService;
    }

    @Transactional(readOnly = true)
    public PageResponse<StockBalanceDto> findAll(Long baseId, Long equipmentTypeId, int page, int size) {
        Long scopedBaseId = accessScopeService.resolveBaseId(baseId);
        Page<StockBalance> result = stockBalanceRepository.search(scopedBaseId, equipmentTypeId,
                PageRequest.of(page, size, Sort.unsorted()));
        return PageResponse.of(result.getContent().stream().map(InventoryService::toDto).toList(),
                page, size, result.getTotalElements());
    }

    /** All stock rows for the current scope - used to populate picker dropdowns. */
    @Transactional(readOnly = true)
    public List<StockBalanceDto> findAllScoped(Long baseId, Long equipmentTypeId) {
        Long scopedBaseId = accessScopeService.resolveBaseId(baseId);
        return stockBalanceRepository.search(scopedBaseId, equipmentTypeId,
                        PageRequest.of(0, 1000, Sort.unsorted()))
                .getContent().stream()
                .map(InventoryService::toDto)
                .toList();
    }

    public static StockBalanceDto toDto(StockBalance stock) {
        return new StockBalanceDto(
                stock.getId(),
                stock.getBase().getId(),
                stock.getBase().getCode(),
                stock.getBase().getName(),
                stock.getEquipmentType().getId(),
                stock.getEquipmentType().getCode(),
                stock.getEquipmentType().getName(),
                stock.getEquipmentType().getCategory(),
                stock.getEquipmentType().getUnit(),
                stock.getOpeningBalance(),
                stock.getOnHandQuantity(),
                stock.getUpdatedAt());
    }
}
