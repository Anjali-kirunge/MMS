package com.military.ams.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.military.ams.dto.PageResponse;
import com.military.ams.dto.PersonnelDto;
import com.military.ams.dto.PersonnelRequest;
import com.military.ams.entity.Personnel;
import com.military.ams.exception.DuplicateResourceException;
import com.military.ams.exception.ResourceNotFoundException;
import com.military.ams.repository.PersonnelRepository;
import com.military.ams.security.AccessScopeService;

@Service
public class PersonnelService {

    private final PersonnelRepository personnelRepository;
    private final BaseService baseService;
    private final AuditService auditService;
    private final AccessScopeService accessScopeService;

    public PersonnelService(PersonnelRepository personnelRepository,
                            BaseService baseService,
                            AuditService auditService,
                            AccessScopeService accessScopeService) {
        this.personnelRepository = personnelRepository;
        this.baseService = baseService;
        this.auditService = auditService;
        this.accessScopeService = accessScopeService;
    }

    @Transactional(readOnly = true)
    public List<PersonnelDto> findByBase(Long baseId) {
        Long scoped = accessScopeService.resolveBaseId(baseId);
        if (scoped == null) {
            return personnelRepository.findAll().stream().map(PersonnelService::toDto).toList();
        }
        return personnelRepository.findByBaseIdOrderByFullNameAsc(scoped).stream()
                .map(PersonnelService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<PersonnelDto> search(Long baseId, String q, int page, int size) {
        Long scoped = accessScopeService.resolveBaseId(baseId);
        Page<Personnel> result = personnelRepository.search(scoped,
                q == null || q.isBlank() ? null : q.trim(), PageRequest.of(page, size));
        return PageResponse.of(result.getContent().stream().map(PersonnelService::toDto).toList(),
                page, size, result.getTotalElements());
    }

    @Transactional
    public PersonnelDto create(PersonnelRequest request) {
        Long baseId = accessScopeService.resolveBaseId(request.baseId());
        String serviceNumber = request.serviceNumber().trim();
        if (personnelRepository.existsByServiceNumber(serviceNumber)) {
            throw new DuplicateResourceException("Service number '" + serviceNumber + "' already exists");
        }
        Personnel personnel = new Personnel();
        personnel.setServiceNumber(serviceNumber);
        personnel.setFullName(request.fullName().trim());
        personnel.setRankTitle(trimToNull(request.rankTitle()));
        personnel.setBase(baseService.require(baseId));
        personnel.setContact(trimToNull(request.contact()));
        personnel.setCreatedAt(LocalDateTime.now());

        PersonnelDto dto = toDto(personnelRepository.save(personnel));
        auditService.log(AuditService.ACTION_CREATE, "PERSONNEL", dto.id(), dto.baseId(),
                "Created personnel " + dto.serviceNumber() + " - " + dto.fullName());
        return dto;
    }

    @Transactional
    public PersonnelDto update(Long id, PersonnelRequest request) {
        Personnel personnel = requireScoped(id);
        Long baseId = accessScopeService.resolveBaseId(request.baseId());
        String serviceNumber = request.serviceNumber().trim();
        if (personnelRepository.existsByServiceNumberAndIdNot(serviceNumber, id)) {
            throw new DuplicateResourceException("Service number '" + serviceNumber + "' already exists");
        }
        personnel.setServiceNumber(serviceNumber);
        personnel.setFullName(request.fullName().trim());
        personnel.setRankTitle(trimToNull(request.rankTitle()));
        personnel.setBase(baseService.require(baseId));
        personnel.setContact(trimToNull(request.contact()));

        PersonnelDto dto = toDto(personnelRepository.save(personnel));
        auditService.log(AuditService.ACTION_UPDATE, "PERSONNEL", dto.id(), dto.baseId(),
                "Updated personnel " + dto.serviceNumber() + " - " + dto.fullName());
        return dto;
    }

    @Transactional
    public void delete(Long id) {
        Personnel personnel = requireScoped(id);
        personnelRepository.delete(personnel);
        auditService.log(AuditService.ACTION_DELETE, "PERSONNEL", id,
                personnel.getBase() == null ? null : personnel.getBase().getId(),
                "Deleted personnel " + personnel.getServiceNumber() + " - " + personnel.getFullName());
    }

    @Transactional(readOnly = true)
    public Personnel require(Long id) {
        return personnelRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Personnel", id));
    }

    /**
     * Loads a person and enforces that they belong to the caller's base scope.
     * Base commanders are confined to their own base, so they must not be able
     * to edit or delete personnel posted elsewhere by guessing an id.
     */
    @Transactional(readOnly = true)
    public Personnel requireScoped(Long id) {
        Personnel personnel = require(id);
        accessScopeService.resolveBaseId(personnel.getBase().getId());
        return personnel;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static PersonnelDto toDto(Personnel personnel) {
        return new PersonnelDto(
                personnel.getId(),
                personnel.getServiceNumber(),
                personnel.getFullName(),
                personnel.getRankTitle(),
                personnel.getBase().getId(),
                personnel.getBase().getCode(),
                personnel.getBase().getName(),
                personnel.getContact());
    }
}
