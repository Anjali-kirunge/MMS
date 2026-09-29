package com.military.ams.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.military.ams.dto.EquipmentTypeDto;
import com.military.ams.dto.EquipmentTypeRequest;
import com.military.ams.dto.PageResponse;
import com.military.ams.entity.EquipmentType;
import com.military.ams.exception.DuplicateResourceException;
import com.military.ams.exception.ResourceNotFoundException;
import com.military.ams.repository.EquipmentTypeRepository;
import com.military.ams.repository.StockBalanceRepository;

@Service
public class EquipmentTypeService {

    private final EquipmentTypeRepository equipmentTypeRepository;
    private final StockBalanceRepository stockBalanceRepository;
    private final AuditService auditService;

    public EquipmentTypeService(EquipmentTypeRepository equipmentTypeRepository,
                                StockBalanceRepository stockBalanceRepository,
                                AuditService auditService) {
        this.equipmentTypeRepository = equipmentTypeRepository;
        this.stockBalanceRepository = stockBalanceRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<EquipmentTypeDto> findAll() {
        return equipmentTypeRepository.findAllByOrderByNameAsc().stream().map(EquipmentTypeService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<EquipmentTypeDto> search(String q, int page, int size) {
        Page<EquipmentType> result = equipmentTypeRepository.search(
                q == null || q.isBlank() ? null : q.trim(),
                PageRequest.of(page, size, Sort.by("name").ascending()));
        return PageResponse.of(result.getContent().stream().map(EquipmentTypeService::toDto).toList(),
                page, size, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public EquipmentTypeDto findById(Long id) {
        return toDto(require(id));
    }

    @Transactional(readOnly = true)
    public EquipmentType require(Long id) {
        if (id == null) {
            throw new ResourceNotFoundException("An equipment type must be selected");
        }
        return equipmentTypeRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Equipment type", id));
    }

    @Transactional
    public EquipmentTypeDto create(EquipmentTypeRequest request) {
        String code = request.code().trim();
        if (equipmentTypeRepository.existsByCode(code)) {
            throw new DuplicateResourceException("An equipment type with code '" + code + "' already exists");
        }
        EquipmentType type = new EquipmentType(code, request.name().trim(), request.category().trim(),
                resolveUnit(request.unit()), trimToNull(request.description()));
        EquipmentTypeDto dto = toDto(equipmentTypeRepository.save(type));
        auditService.log(AuditService.ACTION_CREATE, "EQUIPMENT_TYPE", dto.id(), null,
                "Created equipment type " + dto.code() + " - " + dto.name());
        return dto;
    }

    @Transactional
    public EquipmentTypeDto update(Long id, EquipmentTypeRequest request) {
        EquipmentType type = require(id);
        String code = request.code().trim();
        if (equipmentTypeRepository.existsByCodeAndIdNot(code, id)) {
            throw new DuplicateResourceException("An equipment type with code '" + code + "' already exists");
        }
        type.setCode(code);
        type.setName(request.name().trim());
        type.setCategory(request.category().trim());
        type.setUnit(resolveUnit(request.unit()));
        type.setDescription(trimToNull(request.description()));
        EquipmentTypeDto dto = toDto(equipmentTypeRepository.save(type));
        auditService.log(AuditService.ACTION_UPDATE, "EQUIPMENT_TYPE", dto.id(), null,
                "Updated equipment type " + dto.code() + " - " + dto.name());
        return dto;
    }

    @Transactional
    public void delete(Long id) {
        EquipmentType type = require(id);
        if (stockBalanceRepository.existsByEquipmentTypeId(id)) {
            throw new DuplicateResourceException("Equipment type " + type.getCode()
                    + " is used in stock records and cannot be deleted");
        }
        equipmentTypeRepository.delete(type);
        auditService.log(AuditService.ACTION_DELETE, "EQUIPMENT_TYPE", id, null,
                "Deleted equipment type " + type.getCode() + " - " + type.getName());
    }

    private String resolveUnit(String unit) {
        return unit == null || unit.isBlank() ? "UNIT" : unit.trim().toUpperCase();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static EquipmentTypeDto toDto(EquipmentType type) {
        return new EquipmentTypeDto(type.getId(), type.getCode(), type.getName(), type.getCategory(),
                type.getUnit(), type.getDescription());
    }
}
