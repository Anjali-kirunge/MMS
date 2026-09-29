package com.military.ams.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.military.ams.dto.PageResponse;
import com.military.ams.dto.TransferDto;
import com.military.ams.dto.TransferItemDto;
import com.military.ams.dto.TransferItemRequest;
import com.military.ams.dto.TransferRequest;
import com.military.ams.entity.AppUser;
import com.military.ams.entity.EquipmentType;
import com.military.ams.entity.MilitaryBase;
import com.military.ams.entity.StockBalance;
import com.military.ams.entity.Transfer;
import com.military.ams.entity.TransferItem;
import com.military.ams.exception.BusinessRuleException;
import com.military.ams.exception.DuplicateResourceException;
import com.military.ams.exception.ResourceNotFoundException;
import com.military.ams.repository.TransferRepository;
import com.military.ams.repository.UserRepository;
import com.military.ams.security.AccessScopeService;

@Service
public class TransferService {

    private static final String DOC_PREFIX = "TRF";

    private final TransferRepository transferRepository;
    private final UserRepository userRepository;
    private final BaseService baseService;
    private final EquipmentTypeService equipmentTypeService;
    private final StockService stockService;
    private final AuditService auditService;
    private final ReferenceGenerator referenceGenerator;
    private final AccessScopeService accessScopeService;

    public TransferService(TransferRepository transferRepository,
                           UserRepository userRepository,
                           BaseService baseService,
                           EquipmentTypeService equipmentTypeService,
                           StockService stockService,
                           AuditService auditService,
                           ReferenceGenerator referenceGenerator,
                           AccessScopeService accessScopeService) {
        this.transferRepository = transferRepository;
        this.userRepository = userRepository;
        this.baseService = baseService;
        this.equipmentTypeService = equipmentTypeService;
        this.stockService = stockService;
        this.auditService = auditService;
        this.referenceGenerator = referenceGenerator;
        this.accessScopeService = accessScopeService;
    }

    @Transactional
    public TransferDto create(TransferRequest request) {
        Long sourceBaseId = accessScopeService.resolveBaseId(request.sourceBaseId());
        MilitaryBase source = baseService.require(sourceBaseId);
        MilitaryBase destination = baseService.require(request.destinationBaseId());

        if (source.getId().equals(destination.getId())) {
            throw new BusinessRuleException("Source and destination base must be different");
        }
        rejectDuplicateLines(request.items());

        String seriesPrefix = referenceGenerator.seriesPrefix(DOC_PREFIX);
        String referenceNo = request.referenceNo() == null || request.referenceNo().isBlank()
                ? referenceGenerator.next(seriesPrefix, transferRepository.countByReferenceNoStartingWith(seriesPrefix))
                : request.referenceNo().trim();
        if (transferRepository.existsByReferenceNo(referenceNo)) {
            throw new DuplicateResourceException("Transfer reference '" + referenceNo + "' already exists");
        }

        Transfer transfer = new Transfer();
        transfer.setReferenceNo(referenceNo);
        transfer.setSourceBase(source);
        transfer.setDestinationBase(destination);
        transfer.setTransferDate(request.transferDate());
        transfer.setRemarks(trimToNull(request.remarks()));
        transfer.setCreatedAt(LocalDateTime.now());
        transfer.setCreatedBy(userRepository.findById(accessScopeService.currentUser().getId()).orElse(null));

        for (TransferItemRequest itemRequest : request.items()) {
            EquipmentType equipmentType = equipmentTypeService.require(itemRequest.equipmentTypeId());
            transfer.addItem(new TransferItem(equipmentType, itemRequest.quantity()));
        }

        transferRepository.save(transfer);

        // Lock every affected stock row in a deterministic order so that two
        // opposing transfers (A->B and B->A) cannot deadlock each other.
        Map<StockKey, StockBalance> locked = lockAll(transfer, source, destination);

        List<String> applied = new ArrayList<>();
        for (TransferItem item : transfer.getItems()) {
            StockBalance sourceStock = locked.get(new StockKey(source.getId(), item.getEquipmentType().getId()));
            StockBalance destinationStock =
                    locked.get(new StockKey(destination.getId(), item.getEquipmentType().getId()));
            stockService.decrease(sourceStock, item.getQuantity());
            stockService.increase(destinationStock, item.getQuantity());
            applied.add(item.getEquipmentType().getCode() + " x" + item.getQuantity());
        }

        TransferDto dto = toDto(transfer);
        auditService.log(AuditService.ACTION_CREATE, "TRANSFER", transfer.getId(), source.getId(),
                "Transferred stock " + referenceNo + " from " + source.getCode() + " to " + destination.getCode()
                        + " [" + String.join(", ", applied) + "]");
        return dto;
    }

    @Transactional(readOnly = true)
    public PageResponse<TransferDto> search(Long baseId, LocalDate from, LocalDate to, int page, int size) {
        Long scopedBaseId = accessScopeService.resolveBaseId(baseId);
        Page<Transfer> result = transferRepository.search(scopedBaseId, from, to, PageRequest.of(page, size));
        return PageResponse.of(result.getContent().stream().map(TransferService::toDto).toList(),
                page, size, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public TransferDto findById(Long id) {
        Transfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Transfer", id));
        // A transfer is visible to a base commander when their base is either
        // end of the movement, matching the scope of search() above.
        if (transfer.getSourceBase().getId().equals(accessScopeService.currentUser().getBaseId())) {
            accessScopeService.resolveBaseId(transfer.getSourceBase().getId());
        } else {
            accessScopeService.resolveBaseId(transfer.getDestinationBase().getId());
        }
        return toDto(transfer);
    }

    private Map<StockKey, StockBalance> lockAll(Transfer transfer, MilitaryBase source, MilitaryBase destination) {
        Set<StockKey> keys = new LinkedHashSet<>();
        for (TransferItem item : transfer.getItems()) {
            keys.add(new StockKey(source.getId(), item.getEquipmentType().getId()));
            keys.add(new StockKey(destination.getId(), item.getEquipmentType().getId()));
        }
        List<StockKey> ordered = new ArrayList<>(keys);
        ordered.sort(Comparator.comparing(StockKey::baseId).thenComparing(StockKey::equipmentTypeId));

        Map<StockKey, StockBalance> locked = new LinkedHashMap<>();
        for (StockKey key : ordered) {
            MilitaryBase base = key.baseId().equals(source.getId()) ? source : destination;
            EquipmentType equipmentType = transfer.getItems().stream()
                    .map(TransferItem::getEquipmentType)
                    .filter(type -> type.getId().equals(key.equipmentTypeId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Unresolved equipment type for stock key " + key));
            locked.put(key, stockService.lock(base, equipmentType));
        }
        return locked;
    }

    private void rejectDuplicateLines(List<TransferItemRequest> items) {
        Set<Long> seen = new LinkedHashSet<>();
        for (TransferItemRequest item : items) {
            if (!seen.add(item.equipmentTypeId())) {
                throw new BusinessRuleException(
                        "Equipment type " + item.equipmentTypeId() + " appears on more than one line item");
            }
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static TransferDto toDto(Transfer transfer) {
        List<TransferItemDto> items = transfer.getItems().stream()
                .map(item -> new TransferItemDto(
                        item.getId(),
                        item.getEquipmentType().getId(),
                        item.getEquipmentType().getCode(),
                        item.getEquipmentType().getName(),
                        item.getEquipmentType().getUnit(),
                        item.getQuantity()))
                .toList();
        return new TransferDto(
                transfer.getId(),
                transfer.getReferenceNo(),
                transfer.getSourceBase().getId(),
                transfer.getSourceBase().getCode(),
                transfer.getSourceBase().getName(),
                transfer.getDestinationBase().getId(),
                transfer.getDestinationBase().getCode(),
                transfer.getDestinationBase().getName(),
                transfer.getTransferDate(),
                transfer.getRemarks(),
                transfer.getCreatedBy() == null ? null : transfer.getCreatedBy().getUsername(),
                transfer.getCreatedAt(),
                items);
    }

    private record StockKey(Long baseId, Long equipmentTypeId) {
    }
}
