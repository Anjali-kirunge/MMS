package com.military.ams.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.military.ams.dto.ExpenditureDto;
import com.military.ams.dto.ExpenditureRequest;
import com.military.ams.dto.PageResponse;
import com.military.ams.entity.AppUser;
import com.military.ams.entity.EquipmentType;
import com.military.ams.entity.Expenditure;
import com.military.ams.entity.MilitaryBase;
import com.military.ams.entity.StockBalance;
import com.military.ams.exception.ResourceNotFoundException;
import com.military.ams.repository.ExpenditureRepository;
import com.military.ams.repository.UserRepository;
import com.military.ams.security.AccessScopeService;

@Service
public class ExpenditureService {

    private final ExpenditureRepository expenditureRepository;
    private final UserRepository userRepository;
    private final BaseService baseService;
    private final EquipmentTypeService equipmentTypeService;
    private final StockService stockService;
    private final AuditService auditService;
    private final AccessScopeService accessScopeService;

    public ExpenditureService(ExpenditureRepository expenditureRepository,
                              UserRepository userRepository,
                              BaseService baseService,
                              EquipmentTypeService equipmentTypeService,
                              StockService stockService,
                              AuditService auditService,
                              AccessScopeService accessScopeService) {
        this.expenditureRepository = expenditureRepository;
        this.userRepository = userRepository;
        this.baseService = baseService;
        this.equipmentTypeService = equipmentTypeService;
        this.stockService = stockService;
        this.auditService = auditService;
        this.accessScopeService = accessScopeService;
    }

    @Transactional
    public ExpenditureDto create(ExpenditureRequest request) {
        Long baseId = accessScopeService.resolveBaseId(request.baseId());
        MilitaryBase base = baseService.require(baseId);
        EquipmentType equipmentType = equipmentTypeService.require(request.equipmentTypeId());

        StockBalance stock = stockService.lock(base, equipmentType);
        stockService.decrease(stock, request.quantity());

        Expenditure expenditure = new Expenditure();
        expenditure.setBase(base);
        expenditure.setEquipmentType(equipmentType);
        expenditure.setQuantity(request.quantity());
        expenditure.setExpendedDate(request.expendedDate());
        expenditure.setReason(request.reason().trim());
        expenditure.setRemarks(trimToNull(request.remarks()));
        expenditure.setCreatedAt(LocalDateTime.now());
        AppUser creator = userRepository.findById(accessScopeService.currentUser().getId()).orElse(null);
        expenditure.setCreatedBy(creator);
        expenditureRepository.save(expenditure);

        ExpenditureDto dto = toDto(expenditure);
        auditService.log(AuditService.ACTION_CREATE, "EXPENDITURE", expenditure.getId(), base.getId(),
                "Recorded expenditure of " + request.quantity() + " x " + equipmentType.getCode()
                        + " at " + base.getCode() + " (reason: " + request.reason().trim() + ")");
        return dto;
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpenditureDto> search(Long baseId, Long equipmentTypeId, LocalDate from, LocalDate to,
                                                int page, int size) {
        Long scopedBaseId = accessScopeService.resolveBaseId(baseId);
        Page<Expenditure> result = expenditureRepository.search(scopedBaseId, equipmentTypeId, from, to,
                PageRequest.of(page, size));
        return PageResponse.of(result.getContent().stream().map(ExpenditureService::toDto).toList(),
                page, size, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ExpenditureDto findById(Long id) {
        Expenditure expenditure = expenditureRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Expenditure", id));
        accessScopeService.resolveBaseId(expenditure.getBase().getId());
        return toDto(expenditure);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static ExpenditureDto toDto(Expenditure expenditure) {
        return new ExpenditureDto(
                expenditure.getId(),
                expenditure.getBase().getId(),
                expenditure.getBase().getCode(),
                expenditure.getBase().getName(),
                expenditure.getEquipmentType().getId(),
                expenditure.getEquipmentType().getCode(),
                expenditure.getEquipmentType().getName(),
                expenditure.getEquipmentType().getUnit(),
                expenditure.getQuantity(),
                expenditure.getExpendedDate(),
                expenditure.getReason(),
                expenditure.getRemarks(),
                expenditure.getCreatedBy() == null ? null : expenditure.getCreatedBy().getUsername(),
                expenditure.getCreatedAt());
    }
}
