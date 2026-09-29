package com.military.ams.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.military.ams.dto.BaseDto;
import com.military.ams.dto.BaseRequest;
import com.military.ams.entity.MilitaryBase;
import com.military.ams.exception.DuplicateResourceException;
import com.military.ams.exception.ResourceNotFoundException;
import com.military.ams.repository.BaseRepository;
import com.military.ams.repository.PersonnelRepository;
import com.military.ams.repository.StockBalanceRepository;
import com.military.ams.repository.UserRepository;

@Service
public class BaseService {

    private final BaseRepository baseRepository;
    private final UserRepository userRepository;
    private final PersonnelRepository personnelRepository;
    private final StockBalanceRepository stockBalanceRepository;
    private final AuditService auditService;

    public BaseService(BaseRepository baseRepository,
                       UserRepository userRepository,
                       PersonnelRepository personnelRepository,
                       StockBalanceRepository stockBalanceRepository,
                       AuditService auditService) {
        this.baseRepository = baseRepository;
        this.userRepository = userRepository;
        this.personnelRepository = personnelRepository;
        this.stockBalanceRepository = stockBalanceRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<BaseDto> findAll() {
        return baseRepository.findAllByOrderByNameAsc().stream().map(BaseService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public BaseDto findById(Long id) {
        return toDto(require(id));
    }

    @Transactional(readOnly = true)
    public MilitaryBase require(Long id) {
        if (id == null) {
            throw new ResourceNotFoundException("A base must be selected");
        }
        return baseRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Base", id));
    }

    @Transactional
    public BaseDto create(BaseRequest request) {
        String code = request.code().trim();
        if (baseRepository.existsByCode(code)) {
            throw new DuplicateResourceException("A base with code '" + code + "' already exists");
        }
        MilitaryBase base = new MilitaryBase(code, request.name().trim(),
                trimToNull(request.location()), trimToNull(request.commander()));
        BaseDto dto = toDto(baseRepository.save(base));
        auditService.log(AuditService.ACTION_CREATE, "BASE", dto.id(), dto.id(),
                "Created base " + dto.code() + " - " + dto.name());
        return dto;
    }

    @Transactional
    public BaseDto update(Long id, BaseRequest request) {
        MilitaryBase base = require(id);
        String code = request.code().trim();
        if (baseRepository.existsByCodeAndIdNot(code, id)) {
            throw new DuplicateResourceException("A base with code '" + code + "' already exists");
        }
        base.setCode(code);
        base.setName(request.name().trim());
        base.setLocation(trimToNull(request.location()));
        base.setCommander(trimToNull(request.commander()));
        BaseDto dto = toDto(baseRepository.save(base));
        auditService.log(AuditService.ACTION_UPDATE, "BASE", dto.id(), dto.id(),
                "Updated base " + dto.code() + " - " + dto.name());
        return dto;
    }

    @Transactional
    public void delete(Long id) {
        MilitaryBase base = require(id);
        if (userRepository.existsByBaseId(id)
                || personnelRepository.existsByBaseId(id)
                || stockBalanceRepository.existsByBaseId(id)) {
            throw new DuplicateResourceException("Base " + base.getCode()
                    + " still has users, personnel or stock records and cannot be deleted");
        }
        baseRepository.delete(base);
        auditService.log(AuditService.ACTION_DELETE, "BASE", id, id,
                "Deleted base " + base.getCode() + " - " + base.getName());
    }

    public static BaseDto toDto(MilitaryBase base) {
        return new BaseDto(base.getId(), base.getCode(), base.getName(), base.getLocation(),
                base.getCommander(), base.getCreatedAt());
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
