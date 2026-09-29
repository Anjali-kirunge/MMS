package com.military.ams.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.military.ams.dto.PageResponse;
import com.military.ams.dto.PurchaseDto;
import com.military.ams.dto.PurchaseItemDto;
import com.military.ams.dto.PurchaseItemRequest;
import com.military.ams.dto.PurchaseRequest;
import com.military.ams.entity.AppUser;
import com.military.ams.entity.EquipmentType;
import com.military.ams.entity.MilitaryBase;
import com.military.ams.entity.Purchase;
import com.military.ams.entity.PurchaseItem;
import com.military.ams.entity.StockBalance;
import com.military.ams.exception.BusinessRuleException;
import com.military.ams.exception.DuplicateResourceException;
import com.military.ams.exception.ResourceNotFoundException;
import com.military.ams.repository.PurchaseRepository;
import com.military.ams.repository.UserRepository;
import com.military.ams.security.AccessScopeService;

@Service
public class PurchaseService {

    private static final String DOC_PREFIX = "PUR";

    private final PurchaseRepository purchaseRepository;
    private final UserRepository userRepository;
    private final BaseService baseService;
    private final EquipmentTypeService equipmentTypeService;
    private final StockService stockService;
    private final AuditService auditService;
    private final ReferenceGenerator referenceGenerator;
    private final AccessScopeService accessScopeService;

    public PurchaseService(PurchaseRepository purchaseRepository,
                           UserRepository userRepository,
                           BaseService baseService,
                           EquipmentTypeService equipmentTypeService,
                           StockService stockService,
                           AuditService auditService,
                           ReferenceGenerator referenceGenerator,
                           AccessScopeService accessScopeService) {
        this.purchaseRepository = purchaseRepository;
        this.userRepository = userRepository;
        this.baseService = baseService;
        this.equipmentTypeService = equipmentTypeService;
        this.stockService = stockService;
        this.auditService = auditService;
        this.referenceGenerator = referenceGenerator;
        this.accessScopeService = accessScopeService;
    }

    @Transactional
    public PurchaseDto create(PurchaseRequest request) {
        Long baseId = accessScopeService.resolveBaseId(request.baseId());
        MilitaryBase base = baseService.require(baseId);
        rejectDuplicateLines(request.items());

        String seriesPrefix = referenceGenerator.seriesPrefix(DOC_PREFIX);
        String referenceNo = request.referenceNo() == null || request.referenceNo().isBlank()
                ? referenceGenerator.next(seriesPrefix, purchaseRepository.countByReferenceNoStartingWith(seriesPrefix))
                : request.referenceNo().trim();
        if (purchaseRepository.existsByReferenceNo(referenceNo)) {
            throw new DuplicateResourceException("Purchase reference '" + referenceNo + "' already exists");
        }

        Purchase purchase = new Purchase();
        purchase.setReferenceNo(referenceNo);
        purchase.setBase(base);
        purchase.setSupplier(request.supplier().trim());
        purchase.setInvoiceNo(trimToNull(request.invoiceNo()));
        purchase.setPurchaseDate(request.purchaseDate());
        purchase.setRemarks(trimToNull(request.remarks()));
        purchase.setCreatedAt(LocalDateTime.now());
        purchase.setCreatedBy(currentUser());

        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseItemRequest itemRequest : request.items()) {
            EquipmentType equipmentType = equipmentTypeService.require(itemRequest.equipmentTypeId());
            BigDecimal unitCost = itemRequest.unitCost() == null ? BigDecimal.ZERO : itemRequest.unitCost();
            purchase.addItem(new PurchaseItem(equipmentType, itemRequest.quantity(), unitCost));
            total = total.add(unitCost.multiply(BigDecimal.valueOf(itemRequest.quantity())));
        }
        purchase.setTotalCost(total);

        purchaseRepository.save(purchase);

        List<String> applied = new ArrayList<>();
        for (PurchaseItem item : purchase.getItems()) {
            StockBalance stock = stockService.lock(base, item.getEquipmentType());
            stockService.increase(stock, item.getQuantity());
            applied.add(item.getEquipmentType().getCode() + " x" + item.getQuantity());
        }

        PurchaseDto dto = toDto(purchase);
        auditService.log(AuditService.ACTION_CREATE, "PURCHASE", purchase.getId(), base.getId(),
                "Recorded purchase " + referenceNo + " at " + base.getCode() + " for " + base.getName()
                        + " [" + String.join(", ", applied) + "]");
        return dto;
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseDto> search(Long baseId, LocalDate from, LocalDate to, int page, int size) {
        Long scopedBaseId = accessScopeService.resolveBaseId(baseId);
        Page<Purchase> result = purchaseRepository.search(scopedBaseId, from, to, PageRequest.of(page, size));
        return PageResponse.of(result.getContent().stream().map(PurchaseService::toDto).toList(),
                page, size, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public PurchaseDto findById(Long id) {
        Purchase purchase = purchaseRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Purchase", id));
        accessScopeService.resolveBaseId(purchase.getBase().getId());
        return toDto(purchase);
    }

    private AppUser currentUser() {
        return userRepository.findById(accessScopeService.currentUser().getId()).orElse(null);
    }

    private void rejectDuplicateLines(List<PurchaseItemRequest> items) {
        Set<Long> seen = new LinkedHashSet<>();
        for (PurchaseItemRequest item : items) {
            if (!seen.add(item.equipmentTypeId())) {
                throw new BusinessRuleException(
                        "Equipment type " + item.equipmentTypeId() + " appears on more than one line item");
            }
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static PurchaseDto toDto(Purchase purchase) {
        List<PurchaseItemDto> items = purchase.getItems().stream()
                .map(item -> new PurchaseItemDto(
                        item.getId(),
                        item.getEquipmentType().getId(),
                        item.getEquipmentType().getCode(),
                        item.getEquipmentType().getName(),
                        item.getEquipmentType().getUnit(),
                        item.getQuantity(),
                        item.getUnitCost(),
                        item.getUnitCost().multiply(BigDecimal.valueOf(item.getQuantity()))))
                .toList();
        return new PurchaseDto(
                purchase.getId(),
                purchase.getReferenceNo(),
                purchase.getBase().getId(),
                purchase.getBase().getCode(),
                purchase.getBase().getName(),
                purchase.getSupplier(),
                purchase.getInvoiceNo(),
                purchase.getPurchaseDate(),
                purchase.getRemarks(),
                purchase.getTotalCost(),
                purchase.getCreatedBy() == null ? null : purchase.getCreatedBy().getUsername(),
                purchase.getCreatedAt(),
                items);
    }
}
