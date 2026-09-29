package com.military.ams.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.military.ams.dto.AssignmentDto;
import com.military.ams.dto.AssignmentRequest;
import com.military.ams.dto.PageResponse;
import com.military.ams.entity.Assignment;
import com.military.ams.entity.EquipmentType;
import com.military.ams.entity.MilitaryBase;
import com.military.ams.entity.Personnel;
import com.military.ams.entity.StockBalance;
import com.military.ams.exception.BusinessRuleException;
import com.military.ams.exception.ResourceNotFoundException;
import com.military.ams.repository.AssignmentRepository;
import com.military.ams.repository.UserRepository;
import com.military.ams.security.AccessScopeService;

@Service
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final BaseService baseService;
    private final EquipmentTypeService equipmentTypeService;
    private final PersonnelService personnelService;
    private final StockService stockService;
    private final AuditService auditService;
    private final AccessScopeService accessScopeService;

    public AssignmentService(AssignmentRepository assignmentRepository,
                             UserRepository userRepository,
                             BaseService baseService,
                             EquipmentTypeService equipmentTypeService,
                             PersonnelService personnelService,
                             StockService stockService,
                             AuditService auditService,
                             AccessScopeService accessScopeService) {
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.baseService = baseService;
        this.equipmentTypeService = equipmentTypeService;
        this.personnelService = personnelService;
        this.stockService = stockService;
        this.auditService = auditService;
        this.accessScopeService = accessScopeService;
    }

    @Transactional
    public AssignmentDto create(AssignmentRequest request) {
        Long baseId = accessScopeService.resolveBaseId(request.baseId());
        MilitaryBase base = baseService.require(baseId);
        EquipmentType equipmentType = equipmentTypeService.require(request.equipmentTypeId());
        Personnel personnel = personnelService.require(request.personnelId());

        if (!personnel.getBase().getId().equals(base.getId())) {
            throw new BusinessRuleException(personnel.getFullName() + " is not posted to "
                    + base.getName() + " and cannot be issued assets from this base");
        }

        StockBalance stock = stockService.lock(base, equipmentType);
        stockService.decrease(stock, request.quantity());

        Assignment assignment = new Assignment();
        assignment.setBase(base);
        assignment.setEquipmentType(equipmentType);
        assignment.setPersonnel(personnel);
        assignment.setQuantity(request.quantity());
        assignment.setAssignedDate(request.assignedDate());
        assignment.setRemarks(trimToNull(request.remarks()));
        assignment.setCreatedAt(LocalDateTime.now());
        assignment.setCreatedBy(userRepository.findById(accessScopeService.currentUser().getId()).orElse(null));
        assignmentRepository.save(assignment);

        AssignmentDto dto = toDto(assignment);
        auditService.log(AuditService.ACTION_CREATE, "ASSIGNMENT", assignment.getId(), base.getId(),
                "Assigned " + request.quantity() + " x " + equipmentType.getCode()
                        + " to " + personnel.getFullName() + " (" + personnel.getServiceNumber() + ") at "
                        + base.getCode());
        return dto;
    }

    @Transactional(readOnly = true)
    public PageResponse<AssignmentDto> search(Long baseId, Long equipmentTypeId, Long personnelId,
                                              LocalDate from, LocalDate to, int page, int size) {
        Long scopedBaseId = accessScopeService.resolveBaseId(baseId);
        Page<Assignment> result = assignmentRepository.search(scopedBaseId, equipmentTypeId, personnelId,
                from, to, PageRequest.of(page, size));
        return PageResponse.of(result.getContent().stream().map(AssignmentService::toDto).toList(),
                page, size, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AssignmentDto findById(Long id) {
        Assignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Assignment", id));
        accessScopeService.resolveBaseId(assignment.getBase().getId());
        return toDto(assignment);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static AssignmentDto toDto(Assignment assignment) {
        return new AssignmentDto(
                assignment.getId(),
                assignment.getBase().getId(),
                assignment.getBase().getCode(),
                assignment.getBase().getName(),
                assignment.getEquipmentType().getId(),
                assignment.getEquipmentType().getCode(),
                assignment.getEquipmentType().getName(),
                assignment.getEquipmentType().getUnit(),
                assignment.getPersonnel().getId(),
                assignment.getPersonnel().getServiceNumber(),
                assignment.getPersonnel().getFullName(),
                assignment.getQuantity(),
                assignment.getAssignedDate(),
                assignment.getRemarks(),
                assignment.getCreatedBy() == null ? null : assignment.getCreatedBy().getUsername(),
                assignment.getCreatedAt());
    }

    public static List<String> entityName() {
        return List.of("ASSIGNMENT");
    }
}
